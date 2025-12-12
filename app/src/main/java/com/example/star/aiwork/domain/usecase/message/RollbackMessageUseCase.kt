package com.example.star.aiwork.domain.usecase.message

import com.example.star.aiwork.domain.repository.MessageRepository

@Deprecated("Use RollbackHandler instead")
class RollbackMessageUseCase(
    private val repository: MessageRepository
) {
    suspend operator fun invoke(messageId: String) {
        repository.deleteMessage(messageId)
    }
}
