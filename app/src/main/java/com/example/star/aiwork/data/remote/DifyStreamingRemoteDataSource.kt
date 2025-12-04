package com.example.star.aiwork.data.remote

import android.util.Log
import com.example.star.aiwork.data.model.LlmError
import com.example.star.aiwork.domain.TextGenerationParams
import com.example.star.aiwork.domain.model.ChatDataItem
import com.example.star.aiwork.domain.model.MessageRole
import com.example.star.aiwork.domain.model.ProviderProxy
import com.example.star.aiwork.domain.model.ProviderSetting
import com.example.star.aiwork.domain.model.ProviderSetting.DifyBotType
import com.example.star.aiwork.infra.network.NetworkException
import com.example.star.aiwork.infra.network.SseClient
import com.example.star.aiwork.infra.network.defaultOkHttpClient
import com.example.star.aiwork.infra.util.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Dify 的流式聊天数据源实现。
 *
 * 负责将标准 Chat 协议转换为 Dify 的 API 请求，并处理 Dify 的 SSE 响应格式。
 */
class DifyStreamingRemoteDataSource(
    private val sseClient: SseClient,
    private val jsonParser: Json = json
) {

    private val clientCache = ConcurrentHashMap<String, OkHttpClient>()

    fun streamChat(
        history: List<ChatDataItem>,
        setting: ProviderSetting.Dify,
        params: TextGenerationParams,
        taskId: String
    ): Flow<String> {
        val request = buildStreamRequest(history, setting, params)
        val client = clientFor(setting)

        return sseClient.createStream(request, taskId, client)
            .mapNotNull { payload -> parseChunk(payload, setting) }
            .catch { throwable ->
                Log.e(TAG, "Error in Dify stream", throwable)
                if (throwable is CancellationException) {
                    throw throwable
                }
                throw throwable.toLlmError()
            }
    }

    private fun buildStreamRequest(
        history: List<ChatDataItem>,
        setting: ProviderSetting.Dify,
        params: TextGenerationParams
    ): Request {
        val apiPath = when (setting.botType) {
            DifyBotType.Chat -> "/chat-messages"
            DifyBotType.Completion -> "/completion-messages"
            DifyBotType.Workflow -> "/workflows/run"
        }
        val url = "${setting.baseUrl.trimEnd('/')}$apiPath"

        val queryString = buildQueryString(history, setting.botType)

        val body = buildJsonObject {
            // inputs 处理
            if (setting.inputVariable.isNotBlank()) {
                putJsonObject("inputs") {
                    put(setting.inputVariable, queryString)
                }
            } else {
                putJsonObject("inputs") {}
                // 兼容 JS 逻辑：没有 inputVariable 时，query 字段承载内容
                put("query", queryString)
            }

            // Chat 模式通常必须传 query
            if (setting.botType == DifyBotType.Chat && setting.inputVariable.isNotBlank()) {
                 put("query", queryString)
            }

            put("response_mode", "streaming")
            put("conversation_id", "") // Stateless, new conversation every time
            put("user", "apiuser")
            put("auto_generate_name", false)
        }

        val requestBody = body.toString().toRequestBody(JSON_MEDIA_TYPE)

        return Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${setting.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()
    }

    private fun buildQueryString(history: List<ChatDataItem>, botType: DifyBotType): String {
        return if (botType == DifyBotType.Chat) {
            val sb = StringBuilder()
            val lastMsg = history.lastOrNull()
            if (history.size > 1) {
                sb.append("here is our talk history:\n'''\n")
                history.dropLast(1).forEach { msg ->
                    val roleName = when (msg.role.lowercase()) {
                        "user" -> "user"
                        "model", "assistant" -> "assistant"
                        else -> "unknown"
                    }
                    sb.append("$roleName: ${msg.content}\n")
                }
                sb.append("'''\n\n")
            }
            sb.append("here is my question:\n")
            sb.append(lastMsg?.content ?: "")
            sb.toString()
        } else {
            history.lastOrNull()?.content ?: ""
        }
    }

    private fun parseChunk(payload: String, setting: ProviderSetting.Dify): String? {
        // 1. 预处理：移除 data: 前缀，处理可能的空格
        var jsonStr = payload.trim()
        if (jsonStr.startsWith("data:")) {
            jsonStr = jsonStr.removePrefix("data:").trim()
        }

        // 2. 过滤无效行
        if (jsonStr.isBlank() || jsonStr == "[DONE]") return null

        return runCatching {
            val element = jsonParser.parseToJsonElement(jsonStr).jsonObject
            val event = element["event"]?.jsonPrimitive?.contentOrNull

            when (event) {
                "message", "agent_message" -> {
                    // Chat 模式的主要内容
                    element["answer"]?.jsonPrimitive?.contentOrNull
                }
                "text_chunk" -> {
                    // Completion 模式的内容
                    element["data"]?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                }
                "workflow_finished" -> {
                    // Workflow 模式的最终输出
                    val outputs = element["data"]?.jsonObject?.get("outputs")?.jsonObject
                    if (outputs != null) {
                         if (setting.outputVariable.isNotBlank()) {
                             outputs[setting.outputVariable]?.jsonPrimitive?.contentOrNull
                         } else {
                             outputs.toString()
                         }
                    } else {
                        null
                    }
                }
                "error" -> {
                    val msg = element["message"]?.jsonPrimitive?.contentOrNull ?: "Unknown error"
                    Log.e(TAG, "Dify API Error: $msg")
                    throw Exception("Dify Error: $msg")
                }
                // 忽略 ping, message_end, message_replace, workflow_started 等事件
                else -> null
            }
        }.getOrElse {
            // JSON 解析失败或字段缺失时，记录日志但不崩溃
            Log.w(TAG, "Failed to parse Dify chunk: $jsonStr", it)
            null
        }
    }

    private fun clientFor(setting: ProviderSetting.Dify): OkHttpClient {
        return clientCache.getOrPut(setting.id) {
            defaultOkHttpClient().newBuilder()
                .applyProxy(setting.proxy)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build()
        }
    }

    private fun OkHttpClient.Builder.applyProxy(proxy: ProviderProxy): OkHttpClient.Builder {
        when (proxy) {
            is ProviderProxy.None -> Unit
            is ProviderProxy.Http -> {
                val javaProxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(proxy.address, proxy.port))
                proxy(javaProxy)
            }
        }
        return this
    }

    private fun Throwable.toLlmError(): LlmError {
        return when (this) {
            is LlmError -> this
            is NetworkException.TimeoutException -> LlmError.NetworkError("请求超时", this)
            is NetworkException.ConnectionException -> LlmError.NetworkError(cause = this)
            is NetworkException.HttpException -> LlmError.ServerError("HTTP Error", this)
            else -> LlmError.UnknownError(cause = this)
        }
    }

    companion object {
        private const val TAG = "DifyRemoteDataSource"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
