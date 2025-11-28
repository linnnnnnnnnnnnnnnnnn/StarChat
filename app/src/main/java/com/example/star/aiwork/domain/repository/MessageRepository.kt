package com.example.star.aiwork.domain.repository

import com.example.star.aiwork.domain.model.MessageEntity
import kotlinx.coroutines.flow.Flow

interface MessageRepository {
    suspend fun insertMessage(message: MessageEntity)
    suspend fun getMessage(id: String): MessageEntity?
    suspend fun getMessages(sessionId: String): List<MessageEntity>

    fun observeMessages(sessionId: String): Flow<List<MessageEntity>>
    suspend fun deleteMessage(id: String)
    suspend fun deleteMessagesBySession(sessionId: String)
}
