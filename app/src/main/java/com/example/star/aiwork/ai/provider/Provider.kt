package com.example.star.aiwork.ai.provider

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import com.example.star.aiwork.ai.core.Tool
import com.example.star.aiwork.ai.ui.ImageAspectRatio
import com.example.star.aiwork.ai.ui.ImageGenerationResult
import com.example.star.aiwork.ai.ui.MessageChunk
import com.example.star.aiwork.ai.ui.UIMessage
import java.math.BigDecimal

/**
 * AI 提供商接口 (Provider Interface)。
 * 所有的 AI 服务提供商（如 OpenAI, Google, Claude 等）都需要实现此接口。
 * 采用无状态设计，所有状态信息通过 [ProviderSetting] 传入。
 *
 * @param T 具体的 [ProviderSetting] 子类，用于存储该提供商的配置（如 API Key）。
 */
interface Provider<T : ProviderSetting> {
    /**
     * 获取该提供商支持的模型列表。
     *
     * @param providerSetting 提供商配置。
     * @return 模型列表。
     */
    suspend fun listModels(providerSetting: T): List<Model>

    /**
     * 获取账户余额（可选实现）。
     *
     * @param providerSetting 提供商配置。
     * @return 余额字符串。
     */
    suspend fun getBalance(providerSetting: T): String {
        return "TODO"
    }

    /**
     * 非流式生成文本。
     * 发送消息列表并等待完整响应。
     *
     * @param providerSetting 提供商配置。
     * @param messages 历史消息列表。
     * @param params 生成参数 (Temperature, Max Tokens 等)。
     * @return 包含生成内容的消息块。
     */
    suspend fun generateText(
        providerSetting: T,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): MessageChunk

    /**
     * 流式生成文本。
     * 发送消息列表并返回一个 [Flow]，逐步产生文本块。
     *
     * @param providerSetting 提供商配置。
     * @param messages 历史消息列表。
     * @param params 生成参数。
     * @return 消息块的数据流 (Flow)。
     */
    suspend fun streamText(
        providerSetting: T,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): Flow<MessageChunk>

    /**
     * 生成图像。
     *
     * @param providerSetting 提供商配置。
     * @param params 图像生成参数 (Prompt, 宽高比等)。
     * @return 图像生成结果。
     */
    suspend fun generateImage(
        providerSetting: ProviderSetting,
        params: ImageGenerationParams,
    ): ImageGenerationResult
}

/**
 * 文本生成参数。
 * 封装了调用 LLM API 时所需的各种超参数和工具配置。
 *
 * @property model 目标模型。
 * @property temperature 随机性 (Temperature)，控制生成的创造性。
 * @property topP 核采样 (Top-P)，控制生成的多样性。
 * @property maxTokens 最大生成 Token 数。
 * @property tools 可用的工具列表 (用于 Function Calling)。
 * @property thinkingBudget 思考/推理预算 (针对某些支持 Chain of Thought 的模型)。
 * @property customHeaders 自定义 HTTP 头部。
 * @property customBody 自定义 HTTP Body 参数。
 */
@Serializable
data class TextGenerationParams(
    val model: Model,
    val temperature: Float? = null,
    val topP: Float? = null,
    val maxTokens: Int? = null,
    val tools: List<Tool> = emptyList(),
    val thinkingBudget: Int? = null,
    val customHeaders: List<CustomHeader> = emptyList(),
    val customBody: List<CustomBody> = emptyList(),
)

/**
 * 图像生成参数。
 *
 * @property model 目标模型。
 * @property prompt 图像生成提示词。
 * @property numOfImages 生成数量。
 * @property aspectRatio 宽高比。
 * @property customHeaders 自定义 HTTP 头部。
 * @property customBody 自定义 HTTP Body 参数。
 */
@Serializable
data class ImageGenerationParams(
    val model: Model,
    val prompt: String,
    val numOfImages: Int = 1,
    val aspectRatio: ImageAspectRatio = ImageAspectRatio.SQUARE,
    val customHeaders: List<CustomHeader> = emptyList(),
    val customBody: List<CustomBody> = emptyList(),
)

/**
 * 自定义 HTTP 头部。
 * 用于向 API 请求中添加额外的 Header。
 *
 * @property name Header 名称。
 * @property value Header 值。
 */
@Serializable
data class CustomHeader(
    val name: String,
    val value: String
)

/**
 * 自定义 HTTP Body 参数。
 * 用于向 API 请求体中注入额外的 JSON 字段。
 *
 * @property key JSON 键。
 * @property value JSON 值 (支持复杂对象)。
 */
@Serializable
data class CustomBody(
    val key: String,
    val value: JsonElement
)
