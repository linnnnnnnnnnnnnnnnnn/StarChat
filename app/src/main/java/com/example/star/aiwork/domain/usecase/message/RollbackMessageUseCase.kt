package com.example.star.aiwork.domain.usecase.message

import com.example.star.aiwork.data.local.datasource.MessageLocalDataSource

@Deprecated("Use RollbackHandler instead")
class RollbackMessageUseCase(
    private val messageDataSource: MessageLocalDataSource
) {
    suspend operator fun invoke(messageId: String) {
        messageDataSource.deleteMessage(messageId)
    }
}
