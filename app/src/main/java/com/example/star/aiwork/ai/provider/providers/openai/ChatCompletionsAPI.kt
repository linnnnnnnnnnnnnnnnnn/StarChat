package com.example.star.aiwork.ai.provider.providers.openai

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.example.star.aiwork.ai.core.MessageRole
import com.example.star.aiwork.ai.provider.CustomBody
import com.example.star.aiwork.ai.provider.ProviderSetting
import com.example.star.aiwork.ai.provider.TextGenerationParams
import com.example.star.aiwork.ai.ui.MessageChunk
import com.example.star.aiwork.ai.ui.UIMessage
import com.example.star.aiwork.ai.ui.UIMessagePart
import com.example.star.aiwork.ai.util.KeyRoulette
import com.example.star.aiwork.ai.util.await
import com.example.star.aiwork.ai.util.configureClientWithProxy
import com.example.star.aiwork.ai.util.json
import com.example.star.aiwork.ai.util.mergeCustomBody
import com.example.star.aiwork.ai.util.toHeaders
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.util.concurrent.TimeUnit

/**
 * OpenAI Chat Completions API 的具体实现。
 *
 * 负责处理与 OpenAI 风格的 /v1/chat/completions 接口的直接交互。
 * 支持流式 (Stream) 和非流式 (Non-Stream) 的文本生成请求。
 *
 * @property client OkHttpClient 实例，用于发送网络请求。
 * @property keyRoulette API Key 轮盘赌工具，用于密钥负载均衡。
 */
class ChatCompletionsAPI(
    private val client: OkHttpClient,
    private val keyRoulette: KeyRoulette
) {

    /**
     * 非流式生成文本。
     *
     * 发送 POST 请求并等待完整响应，然后解析为 MessageChunk。
     *
     * @param providerSetting OpenAI 提供商设置。
     * @param messages 聊天历史消息列表。
     * @param params 文本生成参数。
     * @return 包含生成内容的 MessageChunk。
     */
    suspend fun generateText(
        providerSetting: ProviderSetting.OpenAI,
        messages: List<UIMessage>,
        params: TextGenerationParams
    ): MessageChunk {
        val request = buildRequest(providerSetting, messages, params, stream = false)
        val response = client.configureClientWithProxy(providerSetting.proxy).newCall(request).await()
        if (!response.isSuccessful) {
            error("Failed to generate text: ${response.code} ${response.body?.string()}")
        }
        val bodyStr = response.body?.string() ?: error("Empty response body")
        
        // Decode into OpenAIChunk DTO first, then convert to MessageChunk
        val openAIChunk = json.decodeFromString<OpenAIChunk>(bodyStr)
        return openAIChunk.toMessageChunk()
    }

    /**
     * 流式生成文本。
     *
     * 发送请求并建立 SSE (Server-Sent Events) 连接，逐行读取响应数据。
     *
     * @param providerSetting OpenAI 提供商设置。
     * @param messages 聊天历史消息列表。
     * @param params 文本生成参数。
     * @return 发出 MessageChunk 的 Flow 数据流。
     */
    suspend fun streamText(
        providerSetting: ProviderSetting.OpenAI,
        messages: List<UIMessage>,
        params: TextGenerationParams
    ): Flow<MessageChunk> = flow {
        val request = buildRequest(providerSetting, messages, params, stream = true)
        val response = client.configureClientWithProxy(providerSetting.proxy)
            .newBuilder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
            .newCall(request)
            .await()
            
        if (!response.isSuccessful) {
            error("Failed to stream text: ${response.code} ${response.body?.string()}")
        }

        val source = response.body?.source() ?: error("Empty response body")
        val reader = BufferedReader(source.inputStream().reader())

        try {
            var line: String? = reader.readLine()
            while (line != null) {
                if (line.startsWith("data: ")) {
                    val data = line.substring(6).trim()
                    if (data == "[DONE]") break
                    try {
                        // Decode into OpenAIChunk DTO first
                        val chunk = json.decodeFromString<OpenAIChunk>(data)
                        emit(chunk.toMessageChunk())
                    } catch (e: Exception) {
                        // Ignore malformed chunks or keepalive
                        // e.printStackTrace() // Uncomment for debug
                    }
                }
                line = reader.readLine()
            }
        } finally {
            reader.close()
            response.close()
        }
    }

    /**
     * 构建 OkHttp 请求对象。
     *
     * 组装 URL、Header 和 JSON Body。
     *
     * @param stream 是否启用流式传输。
     */
    private fun buildRequest(
        providerSetting: ProviderSetting.OpenAI,
        messages: List<UIMessage>,
        params: TextGenerationParams,
        stream: Boolean
    ): Request {
        val key = keyRoulette.next(providerSetting.apiKey)
        val url = "${providerSetting.baseUrl}${providerSetting.chatCompletionsPath}"

        val messagesJson = buildJsonArray {
            messages.forEach { msg ->
                add(buildJsonObject {
                    put("role", msg.role.name.lowercase())
                    // Simplified content handling for now. 
                    // In production, handle multimodal content (images) here.
                    put("content", msg.toText())
                })
            }
        }

        val jsonBody = buildJsonObject {
            put("model", params.model.modelId)
            put("messages", messagesJson)
            put("stream", stream)
            if (params.temperature != null) put("temperature", params.temperature)
            if (params.topP != null) put("top_p", params.topP)
            if (params.maxTokens != null) put("max_tokens", params.maxTokens)
        }.mergeCustomBody(params.customBody)

        val requestBody = json.encodeToString(jsonBody)
            .toRequestBody("application/json".toMediaType())

        return Request.Builder()
            .url(url)
            .headers(params.customHeaders.toHeaders())
            .addHeader("Authorization", "Bearer $key")
            .post(requestBody)
            .build()
    }
}
