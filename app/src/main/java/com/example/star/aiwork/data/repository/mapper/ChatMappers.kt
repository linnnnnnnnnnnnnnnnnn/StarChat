package com.example.star.aiwork.data.repository.mapper

import com.example.star.aiwork.data.model.AiMessage
import com.example.star.aiwork.data.model.AiMessageRole
import com.example.star.aiwork.data.model.HttpBodyField
import com.example.star.aiwork.data.model.HttpHeader
import com.example.star.aiwork.data.model.ModelConfig
import com.example.star.aiwork.data.model.NetworkProxy
import com.example.star.aiwork.data.model.OpenAiEndpoint
import com.example.star.aiwork.domain.TextGenerationParams
import com.example.star.aiwork.domain.model.ChatDataItem
import com.example.star.aiwork.domain.model.CustomBody
import com.example.star.aiwork.domain.model.CustomHeader
import com.example.star.aiwork.domain.model.ProviderProxy
import com.example.star.aiwork.domain.model.ProviderSetting

internal fun List<ChatDataItem>.toAiMessages(): List<AiMessage> =
    map { chat ->
        // 如果存在 imageBase64 数据，将其注入到 content 中的 [image] 占位符
        // 这样 OpenAIStreamingRemoteDataSource 就能解析出图片数据
        // 注意：ChatDataItem 目前只存储一个 imageBase64，如果有多张图片，这里可能会有局限性
        val finalContent = if (!chat.imageBase64.isNullOrEmpty() && chat.content.contains("[image]")) {
            chat.content.replace("[image]", "[image:${chat.imageBase64}]")
        } else {
            chat.content
        }

        AiMessage(
            role = chat.role.toAiMessageRole(),
            content = finalContent
        )
    }

internal fun ProviderSetting.toModelConfig(
    params: TextGenerationParams,
    taskId: String
): ModelConfig = when (this) {
    is ProviderSetting.OpenAICompatible -> toOpenAiModelConfig(params, taskId)
    // 如果有其他未实现 OpenAICompatible 的类型，需要单独处理或保留 error
    else -> error("Unsupported provider setting: ${this::class.simpleName}")
}

private fun ProviderSetting.OpenAICompatible.toOpenAiModelConfig(
    params: TextGenerationParams,
    taskId: String
): ModelConfig.OpenAICompatible {
    return ModelConfig.OpenAICompatible(
        endpoint = toEndpoint(),
        modelId = params.model.modelId,
        modelHeaders = params.model.customHeaders.toHttpHeaders(),
        modelBodies = params.model.customBodies.toHttpBodies(),
        temperature = params.temperature,
        topP = params.topP,
        maxTokens = params.maxTokens,
        customHeaders = params.customHeaders.toHttpHeaders(),
        customBody = params.customBody.toHttpBodies(),
        taskId = taskId
    )
}

private fun ProviderSetting.OpenAICompatible.toEndpoint(): OpenAiEndpoint =
    OpenAiEndpoint(
        providerId = id,
        apiKey = apiKey,
        baseUrl = baseUrl,
        chatCompletionsPath = chatCompletionsPath,
        proxy = proxy.toNetworkProxy()
    )

private fun List<CustomHeader>.toHttpHeaders(): List<HttpHeader> =
    map { HttpHeader(it.name, it.value) }

private fun List<CustomBody>.toHttpBodies(): List<HttpBodyField> =
    map { HttpBodyField(it.key, it.value.toString()) }

private fun ProviderProxy.toNetworkProxy(): NetworkProxy = when (this) {
    ProviderProxy.None -> NetworkProxy.None
    is ProviderProxy.Http -> NetworkProxy.Http(host = address, port = port)
}

private fun String?.toAiMessageRole(): AiMessageRole = when (this?.lowercase()) {
    "system" -> AiMessageRole.SYSTEM
    "assistant" -> AiMessageRole.ASSISTANT
    "tool" -> AiMessageRole.TOOL
    else -> AiMessageRole.USER
}
