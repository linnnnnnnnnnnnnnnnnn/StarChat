package com.example.star.aiwork.data.provider

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import com.example.star.aiwork.domain.Provider
import com.example.star.aiwork.domain.ImageGenerationParams
import com.example.star.aiwork.domain.TextGenerationParams
import com.example.star.aiwork.domain.model.Model
import com.example.star.aiwork.domain.model.ProviderSetting
import com.example.star.aiwork.domain.model.MessageRole
import com.example.star.aiwork.ui.ai.ImageGenerationResult
import com.example.star.aiwork.ui.ai.MessageChunk
import com.example.star.aiwork.ui.ai.UIMessage
import com.example.star.aiwork.ui.ai.UIMessageChoice
import com.example.star.aiwork.ui.ai.UIMessagePart
import com.example.star.aiwork.infra.util.configureClientWithProxy
import com.example.star.aiwork.infra.util.json
import com.example.star.aiwork.infra.util.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Ollama API 提供商实现。
 *
 * 该类负责与 Ollama 服务进行通信，提供列出模型、生成文本和流式生成文本的功能。
 * Ollama 通常在本地运行，提供类似 OpenAI 的接口但有细微差别。
 *
 * @property client OkHttpClient 实例，用于发送网络请求。
 */
class OllamaProvider(
    private val client: OkHttpClient
) : Provider<ProviderSetting.Ollama> {

    /**
     * 列出 Ollama 服务上可用的模型。
     *
     * 发送 GET 请求到 `/api/tags` 端点，解析返回的 JSON 数据以获取模型列表。
     *
     * @param providerSetting Ollama 提供商设置，包含 base URL 和 API key (可选)。
     * @return 可用模型列表 [Model]。
     */
    override suspend fun listModels(providerSetting: ProviderSetting.Ollama): List<Model> =
        withContext(Dispatchers.IO) {
            // API 端点: GET /api/tags
            val url = "${providerSetting.baseUrl}/api/tags"
            
            val requestBuilder = Request.Builder().url(url).get()
            
            // 如果设置了 API Key 且不是默认值 "ollama"，则添加到 Header (部分反代服务可能需要)
            if (providerSetting.apiKey.isNotBlank() && providerSetting.apiKey != "ollama") {
                requestBuilder.addHeader("Authorization", "Bearer ${providerSetting.apiKey}")
            }

            val response = client.configureClientWithProxy(providerSetting.proxy)
                .newCall(requestBuilder.build())
                .await()
            
            // 检查响应状态，如果不成功抛出异常
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                error("Failed to get models: ${response.code} $errorBody")
            }

            val bodyStr = response.body?.string() ?: ""
            val bodyJson = json.parseToJsonElement(bodyStr).jsonObject
            val models = bodyJson["models"]?.jsonArray ?: return@withContext emptyList()

            // 映射 JSON 到 Model 对象
            models.mapNotNull { modelJson ->
                val modelObj = modelJson.jsonObject
                val name = modelObj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                Model(
                    modelId = name,
                    displayName = name,
                )
            }
        }

    /**
     * 获取余额信息。
     *
     * Ollama 通常是本地部署且免费的，因此返回 "Unlimited"。
     *
     * @param providerSetting Ollama 提供商设置。
     * @return 余额描述字符串。
     */
    override suspend fun getBalance(providerSetting: ProviderSetting.Ollama): String {
        return "Unlimited"
    }

    /**
     * 非流式生成文本。
     *
     * 发送 POST 请求到聊天接口，等待完整响应后返回。
     * 由于本地推理可能较慢，此处设置了无限超时时间。
     *
     * @param providerSetting Ollama 提供商设置。
     * @param messages 聊天历史消息。
     * @param params 文本生成参数。
     * @return 生成的消息块 [MessageChunk]。
     */
    override suspend fun generateText(
        providerSetting: ProviderSetting.Ollama,
        messages: List<UIMessage>,
        params: TextGenerationParams
    ): MessageChunk = withContext(Dispatchers.IO) {
        val request = buildRequest(providerSetting, messages, params, stream = false)
        // 设置无限超时，防止复杂问题生成时间过长导致请求断开
        val response = client.configureClientWithProxy(providerSetting.proxy)
            .newBuilder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .build()
            .newCall(request)
            .await()
        
        if (!response.isSuccessful) {
             val errorBody = response.body?.string()
            error("Failed to generate text: ${response.code} $errorBody")
        }
        val bodyStr = response.body?.string() ?: error("Empty response body")
        
        val bodyJson = try {
            json.parseToJsonElement(bodyStr).jsonObject
        } catch (e: Exception) {
            throw RuntimeException("Invalid JSON response from Ollama: $bodyStr", e)
        }
        
        // 响应结构: { "message": { "role": "assistant", "content": "..." }, "done": true }
        // 注意：旧版本或不同接口可能直接返回 response 字段
        val messageObj = try {
            bodyJson["message"]?.jsonObject
        } catch (e: Exception) {
            null
        }
        
        val content = if (messageObj != null) {
             try {
                messageObj["content"]?.jsonPrimitive?.contentOrNull
             } catch (e: Exception) {
                null
             }
        } else {
             try {
                bodyJson["response"]?.jsonPrimitive?.contentOrNull
             } catch (e: Exception) {
                null
             }
        } ?: ""
        
        val done = bodyJson["done"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true
        
        MessageChunk(
            id = UUID.randomUUID().toString(),
            model = params.model.modelId,
            choices = listOf(
                UIMessageChoice(
                    index = 0,
                    delta = null,
                    message = UIMessage(
                        role = MessageRole.ASSISTANT,
                        parts = listOf(UIMessagePart.Text(content))
                    ),
                    finishReason = if(done) "stop" else null
                )
            )
        )
    }

    /**
     * 流式生成文本。
     *
     * 发送请求并逐行读取 JSON 对象响应。
     * Ollama 的流式响应是分块的 JSON 对象，而不是 SSE (Server-Sent Events) 格式。
     *
     * @param providerSetting Ollama 提供商设置。
     * @param messages 聊天历史消息。
     * @param params 文本生成参数。
     * @return [MessageChunk] 的数据流。
     */
    override suspend fun streamText(
        providerSetting: ProviderSetting.Ollama,
        messages: List<UIMessage>,
        params: TextGenerationParams
    ): Flow<MessageChunk> = flow {
        val request = buildRequest(providerSetting, messages, params, stream = true)
        // 设置无限超时，防止长生成过程中连接断开
        val response = client.configureClientWithProxy(providerSetting.proxy)
            .newBuilder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .build()
            .newCall(request)
            .await()
            
        if (!response.isSuccessful) {
            val errorBody = response.body?.string()
            error("Failed to stream text: ${response.code} $errorBody")
        }

        val source = response.body?.source() ?: error("Empty response body")
        val reader = BufferedReader(source.inputStream().reader())

        try {
            var line: String? = reader.readLine()
            while (line != null) {
                if (line.isNotBlank()) {
                    try {
                        // 解析单行 JSON 响应
                        val jsonElement = json.parseToJsonElement(line).jsonObject
                        val done = jsonElement["done"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: false
                        
                        // Ollama 流式响应字段解析
                        // 响应样例: { "model": "...", "created_at": "...", "message": { "role": "assistant", "content": "..." }, "done": false }
                        
                        val messageObj = jsonElement["message"]?.jsonObject
                        val content = messageObj?.get("content")?.jsonPrimitive?.contentOrNull ?: ""
                        
                        emit(
                            MessageChunk(
                                id = UUID.randomUUID().toString(),
                                model = params.model.modelId,
                                choices = listOf(
                                    UIMessageChoice(
                                        index = 0,
                                        delta = UIMessage(
                                            role = MessageRole.ASSISTANT,
                                            parts = if(content.isNotEmpty()) listOf(UIMessagePart.Text(content)) else emptyList()
                                        ),
                                        message = null,
                                        finishReason = if (done) "stop" else null
                                    )
                                )
                            )
                        )
                        
                        if (done) break
                    } catch (e: Exception) {
                        // 忽略解析错误，继续读取下一行
                    }
                }
                line = reader.readLine()
            }
        } finally {
            reader.close()
            response.close()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 构建发送给 Ollama 的请求。
     *
     * 组装符合 Ollama API (兼容部分 OpenAI 格式) 的 JSON 请求体。
     *
     * @param providerSetting 配置信息。
     * @param messages 消息列表。
     * @param params 生成参数 (温度、TopP 等)。
     * @param stream 是否流式传输。
     * @return OkHttp [Request] 对象。
     */
    private fun buildRequest(
        providerSetting: ProviderSetting.Ollama,
        messages: List<UIMessage>,
        params: TextGenerationParams,
        stream: Boolean
    ): Request {
        // API 端点: POST /api/chat
        val url = "${providerSetting.baseUrl}${providerSetting.chatCompletionsPath}"

        val messagesJson = buildJsonArray {
            messages.forEach { msg ->
                add(buildJsonObject {
                    put("role", msg.role.name.lowercase())
                    put("content", msg.toText())
                })
            }
        }

        val jsonBody = buildJsonObject {
            put("model", params.model.modelId)
            put("messages", messagesJson)
            put("stream", stream)
            
            put("options", buildJsonObject {
                if (params.temperature != null) put("temperature", params.temperature)
                if (params.topP != null) put("top_p", params.topP)
                // Ollama 使用 num_predict 来控制最大生成 token 数
                if (params.maxTokens != null) put("num_predict", params.maxTokens)
            })
        }

        val requestBody = json.encodeToString(jsonBody)
            .toRequestBody("application/json".toMediaType())

        val requestBuilder = Request.Builder()
            .url(url)
            .post(requestBody)
            
        if (providerSetting.apiKey.isNotBlank() && providerSetting.apiKey != "ollama") {
            requestBuilder.addHeader("Authorization", "Bearer ${providerSetting.apiKey}")
        }

        return requestBuilder.build()
    }

    override suspend fun generateImage(
        providerSetting: ProviderSetting,
        params: ImageGenerationParams
    ): ImageGenerationResult {
        throw NotImplementedError("Ollama does not support image generation yet")
    }
}
