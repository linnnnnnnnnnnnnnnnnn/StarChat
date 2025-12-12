package com.example.star.aiwork.domain.usecase.message

import com.example.star.aiwork.domain.model.MessageEntity
import com.example.star.aiwork.domain.repository.MessageRepository

class GetMessagesByPageUseCase(private val repository: MessageRepository) {
    suspend operator fun invoke(sessionId: String, page: Int, pageSize: Int): List<MessageEntity> {
        return repository.getMessagesByPage(sessionId, page, pageSize)
    }
}