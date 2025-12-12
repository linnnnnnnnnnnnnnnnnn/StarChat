package com.example.star.aiwork.domain.usecase.message

import com.example.star.aiwork.domain.model.MessageEntity
import com.example.star.aiwork.domain.repository.MessageRepository
import kotlinx.coroutines.flow.Flow

/**
 * UseCase: 观察某个会话的消息列表
 */
class ObserveMessagesUseCase(
    private val repository: MessageRepository
) {
    operator fun invoke(sessionId: String): Flow<List<MessageEntity>> {
        return repository.observeMessages(sessionId)
    }
}