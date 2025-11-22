package com.example.star.aiwork.ai.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 记录 API 调用的 Token 使用情况。
 *
 * @property promptTokens 提示词 (Input) 消耗的 Token 数。
 * @property completionTokens 生成结果 (Output) 消耗的 Token 数。
 * @property totalTokens 总 Token 数 (promptTokens + completionTokens)。
 */
@Serializable
data class TokenUsage(
    @SerialName("prompt_tokens")
    val promptTokens: Int,
    @SerialName("completion_tokens")
    val completionTokens: Int,
    @SerialName("total_tokens")
    val totalTokens: Int
)
