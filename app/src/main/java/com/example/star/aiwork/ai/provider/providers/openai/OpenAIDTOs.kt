package com.example.star.aiwork.ai.provider.providers.openai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import com.example.star.aiwork.ai.core.MessageRole
import com.example.star.aiwork.ai.core.TokenUsage
import com.example.star.aiwork.ai.ui.MessageChunk
import com.example.star.aiwork.ai.ui.UIMessage
import com.example.star.aiwork.ai.ui.UIMessageChoice
import com.example.star.aiwork.ai.ui.UIMessagePart
import java.util.UUID

/**
 * OpenAI API 响应的数据传输对象 (DTO)。
 *
 * 用于直接映射 OpenAI 格式的 JSON 响应。
 *
 * @property id 响应 ID。
 * @property model 使用的模型名称。
 * @property choices 选项列表。
 * @property usage Token 使用情况。
 */
@Serializable
data class OpenAIChunk(
    val id: String,
    val model: String,
    val choices: List<OpenAIChoice>,
    val usage: TokenUsage? = null,
) {
    /**
     * 将 DTO 转换为通用的 MessageChunk 业务对象。
     */
    fun toMessageChunk(): MessageChunk {
        return MessageChunk(
            id = id,
            model = model,
            choices = choices.map { it.toUIMessageChoice() },
            usage = usage
        )
    }
}

/**
 * OpenAI 响应中的单个选项 (Choice)。
 *
 * @property index 索引。
 * @property delta 增量消息内容（流式响应）。
 * @property message 完整消息内容（非流式响应）。
 * @property finishReason 结束原因（如 "stop", "length", "tool_calls"）。
 */
@Serializable
data class OpenAIChoice(
    val index: Int,
    val delta: OpenAIMessage? = null,
    val message: OpenAIMessage? = null,
    @SerialName("finish_reason")
    val finishReason: String? = null
) {
    /**
     * 将 DTO 转换为通用的 UIMessageChoice 业务对象。
     */
    fun toUIMessageChoice(): UIMessageChoice {
        return UIMessageChoice(
            index = index,
            delta = delta?.toUIMessage(),
            message = message?.toUIMessage(),
            finishReason = finishReason
        )
    }
}

/**
 * OpenAI 消息对象。
 *
 * 包含流式 (delta) 或非流式 (message) 的内容。
 *
 * @property role 角色。
 * @property content 消息内容字符串。
 */
@Serializable
data class OpenAIMessage(
    val role: MessageRole? = null,
    val content: String? = null,
    // tool_calls, etc. can be added later
) {
    /**
     * 将 DTO 转换为通用的 UIMessage 业务对象。
     */
    fun toUIMessage(): UIMessage {
        val parts = if (content != null) {
            listOf(UIMessagePart.Text(content))
        } else {
            emptyList()
        }
        
        return UIMessage(
            id = UUID.randomUUID().toString(),
            role = role ?: MessageRole.ASSISTANT, // Default to assistant if role is missing in delta
            parts = parts
        )
    }
}
