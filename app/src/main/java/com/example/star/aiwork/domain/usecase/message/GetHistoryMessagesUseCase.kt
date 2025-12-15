package com.example.star.aiwork.domain.usecase.message

import com.example.star.aiwork.domain.model.MessageEntity
import com.example.star.aiwork.domain.model.MessageRole
import com.example.star.aiwork.domain.repository.MessageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * 获取会话的历史消息列表的用例。
 * 用于消息构建等场景，需要同步获取消息列表。
 */
class GetHistoryMessagesUseCase(
    private val messageRepository: MessageRepository
) {
    /**
     * 获取会话的历史消息列表
     *
     * @param sessionId 会话ID
     * @param excludeSystem 是否排除系统消息，默认为 true
     * @param limit 限制返回的消息数量，默认为 10
     * @return 历史消息列表（已反转，最新的在前）
     */
    suspend operator fun invoke(
        sessionId: String,
        excludeSystem: Boolean = true,
        limit: Int = 10
    ): List<MessageEntity> = withContext(Dispatchers.IO) {
        messageRepository.observeMessages(sessionId)
            .first()
            .let { messages ->
                val filtered = if (excludeSystem) {
                    messages.filter { it.role != MessageRole.SYSTEM }
                } else {
                    messages
                }
                filtered
                    .reversed()
                    .takeLast(limit)
            }
    }
}



