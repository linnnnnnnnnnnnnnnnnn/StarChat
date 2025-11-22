package com.example.star.aiwork.ai

/**
 * 聊天会话接口，定义了聊天会话的基本属性。
 *
 * @property supportOmni 是否支持 Omni 模型（全能模型，可能支持多模态）。
 * @property modelId 关联的 AI 模型 ID。
 * @property sessionId 会话的唯一标识符。
 * @property historyList 会话的历史消息列表。
 */
interface ChatSession {
    var supportOmni: Boolean
    val modelId: String
    val sessionId: String
    val historyList: List<ChatDataItem>?
}
