package com.example.star.aiwork.data.repository

import com.example.star.aiwork.data.local.datasource.message.MessageLocalDataSource
import com.example.star.aiwork.domain.model.MessageEntity
import com.example.star.aiwork.domain.repository.MessageRepository
import kotlinx.coroutines.flow.Flow

/**
 * 消息仓库实现。
 * 
 * 统一管理消息数据的数据库访问。
 * 消息数据通常按会话查询，数据量大，因此直接从数据库读取，不进行缓存。
 * 
 * 上层调用者无需关心数据来源，由仓库统一管理。
 */
class MessageRepositoryImpl(
    private val localDataSource: MessageLocalDataSource
) : MessageRepository {

    /**
     * 创建或更新消息。
     * 
     * @param message 消息实体
     */
    override suspend fun upsertMessage(message: MessageEntity) {
        localDataSource.upsertMessage(message)
    }

    /**
     * 根据 ID 获取单个消息。
     * 
     * @param id 消息 ID
     * @return 消息实体，如果不存在则返回 null
     */
    override suspend fun getMessage(id: String): MessageEntity? {
        return localDataSource.getMessage(id)
    }

    /**
     * 观察指定会话的所有消息。
     * 返回 Flow，当消息列表发生变化时会自动更新。
     * 
     * @param sessionId 会话 ID
     * @return 消息列表的 Flow
     */
    override fun observeMessages(sessionId: String): Flow<List<MessageEntity>> {
        return localDataSource.observeMessages(sessionId)
    }

    /**
     * 分页获取指定会话的消息。
     * 
     * @param sessionId 会话 ID
     * @param page 页码（从 0 开始）
     * @param pageSize 每页大小
     * @return 消息列表
     */
    override suspend fun getMessagesByPage(sessionId: String, page: Int, pageSize: Int): List<MessageEntity> {
        return localDataSource.getMessagesByPage(sessionId, page, pageSize)
    }

    /**
     * 删除指定会话的所有消息。
     * 
     * @param sessionId 会话 ID
     */
    override suspend fun deleteMessagesBySession(sessionId: String) {
        localDataSource.deleteMessagesBySession(sessionId)
    }

    /**
     * 删除指定 ID 的消息。
     * 
     * @param messageId 消息 ID
     */
    override suspend fun deleteMessage(messageId: String) {
        localDataSource.deleteMessage(messageId)
    }
}

