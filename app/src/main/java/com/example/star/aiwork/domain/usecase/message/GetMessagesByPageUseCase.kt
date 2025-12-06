package com.example.star.aiwork.domain.usecase.message

import com.example.star.aiwork.data.local.datasource.MessageLocalDataSource
import com.example.star.aiwork.domain.model.MessageEntity

class GetMessagesByPageUseCase(private val messageLocalDataSource: MessageLocalDataSource) {
    suspend operator fun invoke(sessionId: String, page: Int, pageSize: Int): List<MessageEntity> {
        return messageLocalDataSource.getMessagesByPage(sessionId, page, pageSize)
    }
}