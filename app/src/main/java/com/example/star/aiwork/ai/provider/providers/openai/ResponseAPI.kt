package com.example.star.aiwork.ai.provider.providers.openai

import kotlinx.coroutines.flow.Flow
import com.example.star.aiwork.ai.provider.ProviderSetting
import com.example.star.aiwork.ai.provider.TextGenerationParams
import com.example.star.aiwork.ai.ui.MessageChunk
import com.example.star.aiwork.ai.ui.UIMessage
import okhttp3.OkHttpClient

/**
 * 处理特定 Response 格式 API 的实现类。
 *
 * 某些第三方服务可能不完全遵循标准的 OpenAI Chat Completions 格式，
 * 或者需要特殊的解析逻辑。此类预留用于处理这些特殊情况。
 * 目前暂未实现具体逻辑。
 *
 * @property client OkHttpClient 实例。
 */
class ResponseAPI(private val client: OkHttpClient) {

    /**
     * 非流式生成文本（未实现）。
     */
    suspend fun generateText(
        providerSetting: ProviderSetting.OpenAI,
        messages: List<UIMessage>,
        params: TextGenerationParams
    ): MessageChunk {
        // TODO: Implement if needed, usually ChatCompletionsAPI is enough
        throw NotImplementedError("ResponseAPI for non-streaming not implemented yet")
    }

    /**
     * 流式生成文本（未实现）。
     */
    suspend fun streamText(
        providerSetting: ProviderSetting.OpenAI,
        messages: List<UIMessage>,
        params: TextGenerationParams
    ): Flow<MessageChunk> {
        // TODO: Implement if needed
        throw NotImplementedError("ResponseAPI for streaming not implemented yet")
    }
}
