package com.example.star.aiwork.domain.repository

import com.example.star.aiwork.domain.model.MessageEntity
import kotlinx.coroutines.flow.Flow

/**
 * 消息仓库接口。
 * 
 * 定义消息数据的访问接口，由 Data 层实现。
 * Domain 层和 UseCase 只依赖此接口，不依赖具体实现。
 */
interface MessageRepository {

    /**
     * 创建或更新消息。
     * 
     * @param message 消息实体
     */
    suspend fun upsertMessage(message: MessageEntity)

    /**
     * 根据 ID 获取单个消息。
     * 
     * @param id 消息 ID
     * @return 消息实体，如果不存在则返回 null
     */
    suspend fun getMessage(id: String): MessageEntity?

    /**
     * 观察指定会话的所有消息。
     * 返回 Flow，当消息列表发生变化时会自动更新。
     * 
     * @param sessionId 会话 ID
     * @return 消息列表的 Flow
     */
    fun observeMessages(sessionId: String): Flow<List<MessageEntity>>

    /**
     * 分页获取指定会话的消息。
     * 
     * @param sessionId 会话 ID
     * @param page 页码（从 0 开始）
     * @param pageSize 每页大小
     * @return 消息列表
     */
    suspend fun getMessagesByPage(sessionId: String, page: Int, pageSize: Int): List<MessageEntity>

    /**
     * 删除指定会话的所有消息。
     * 
     * @param sessionId 会话 ID
     */
    suspend fun deleteMessagesBySession(sessionId: String)

    /**
     * 删除指定 ID 的消息。
     * 
     * @param messageId 消息 ID
     */
    suspend fun deleteMessage(messageId: String)

    /**
     * 获取指定会话中最后一条助手消息。
     * 
     * @param sessionId 会话 ID
     * @return 最后一条助手消息，如果不存在则返回 null
     */
    suspend fun getLastAssistantMessage(sessionId: String): MessageEntity?

    /**
     * 删除指定会话中最后一条助手消息。
     * 用于回滚操作。
     * 
     * @param sessionId 会话 ID
     * @return 是否成功删除
     */
    suspend fun deleteLastAssistantMessage(sessionId: String): Boolean

    /**
     * 获取指定会话的缓存消息（仅用于调试）。
     * 
     * @param sessionId 会话 ID
     * @return 缓存中的消息列表，如果不存在则返回 null
     */
    fun getCachedMessages(sessionId: String): List<MessageEntity>?
}

