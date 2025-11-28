package com.example.star.aiwork.data.repository

import com.example.star.aiwork.data.local.datasource.MessageLocalDataSource
import com.example.star.aiwork.data.local.mapper.MessageMapper
import com.example.star.aiwork.domain.model.MessageEntity
import com.example.star.aiwork.domain.repository.MessageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MessageRepositoryImpl(
    private val local: MessageLocalDataSource
) : MessageRepository {

    override suspend fun insertMessage(message: MessageEntity) {
        local.insertMessage(MessageMapper.toRecord(message))
    }

    override suspend fun getMessage(id: String): MessageEntity? =
        local.getMessage(id)?.let { MessageMapper.toEntity(it) }

    override suspend fun getMessages(sessionId: String): List<MessageEntity> =
        local.getMessages(sessionId).map { MessageMapper.toEntity(it) }

    override fun observeMessages(sessionId: String): Flow<List<MessageEntity>> =
        local.observeMessages(sessionId)
            .map { list -> list.map { MessageMapper.toEntity(it) } }

    override suspend fun deleteMessage(id: String) {
        local.deleteMessage(id)
    }

    override suspend fun deleteMessagesBySession(sessionId: String) {
        local.deleteMessagesBySession(sessionId)
    }
}
