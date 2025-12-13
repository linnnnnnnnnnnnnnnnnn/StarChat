package com.example.star.aiwork.data.repository

import com.example.star.aiwork.data.local.datasource.message.MessageCacheDataSource
import com.example.star.aiwork.data.local.datasource.message.MessageLocalDataSource
import com.example.star.aiwork.domain.model.MessageEntity
import com.example.star.aiwork.domain.model.MessageRole
import com.example.star.aiwork.domain.repository.MessageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.onEach

/**
 * 消息仓库实现。
 * 
 * 统一管理消息数据的缓存和数据库访问。
 * 采用缓存优先策略：
 * - 读取：优先从缓存读取，缓存未命中时从数据库读取并写入缓存
 * - 写入：同时更新缓存和数据库，保证数据一致性
 * 
 * 上层调用者无需关心数据来源，由仓库统一管理。
 */
class MessageRepositoryImpl(
    private val cacheDataSource: MessageCacheDataSource,
    private val localDataSource: MessageLocalDataSource
) : MessageRepository {

    /**
     * 创建或更新消息。
     * 同时更新缓存和数据库。
     * 
     * @param message 消息实体
     */
    override suspend fun upsertMessage(message: MessageEntity) {
        // 同时更新缓存和数据库
        localDataSource.upsertMessage(message)
        // 更新缓存：需要重新加载该会话的所有消息
        val cachedMessages = cacheDataSource.getMessages(message.sessionId)
        if (cachedMessages != null) {
            // 如果缓存存在，更新缓存中的消息
            val updatedMessages = cachedMessages.map { 
                if (it.id == message.id) message else it
            }.let { list ->
                if (cachedMessages.none { it.id == message.id }) {
                    // 如果是新消息，添加到列表
                    list + message
                } else {
                    list
                }
            }
            cacheDataSource.putMessages(message.sessionId, updatedMessages)
        }
    }

    /**
     * 根据 ID 获取单个消息。
     * 优先从缓存读取，缓存未命中时从数据库读取。
     * 
     * @param id 消息 ID
     * @return 消息实体，如果不存在则返回 null
     */
    override suspend fun getMessage(id: String): MessageEntity? {
        // 先尝试从缓存中查找（需要遍历所有会话的缓存）
        cacheDataSource.getAllSessionIds().forEach { sessionId ->
            cacheDataSource.getMessages(sessionId)?.find { it.id == id }?.let {
                return it
            }
        }
        // 缓存未命中，从数据库读取
        return localDataSource.getMessage(id)
    }

    /**
     * 观察指定会话的所有消息。
     * 返回 Flow，当消息列表发生变化时会自动更新。
     * 优先从缓存读取初始值，然后监听数据库变化并更新缓存。
     * 
     * @param sessionId 会话 ID
     * @return 消息列表的 Flow
     */
    override fun observeMessages(sessionId: String): Flow<List<MessageEntity>> {
        return localDataSource.observeMessages(sessionId)
            .onStart {
                // 先从缓存读取初始值
                val cached = cacheDataSource.getMessages(sessionId)
                if (cached != null) {
                    // 确保缓存中的消息也按 createdAt 排序
                    emit(cached.sortedBy { it.createdAt })
                }
            }
            .onEach { messages ->
                // 数据库变化时更新缓存，确保缓存中的消息也按 createdAt 排序
                val sortedMessages = messages.sortedBy { it.createdAt }
                cacheDataSource.putMessages(sessionId, sortedMessages)
            }
    }

    /**
     * 分页获取指定会话的消息。
     * 从数据库获取，因为分页数据不适合缓存。
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
     * 同时从缓存和数据库删除。
     * 
     * @param sessionId 会话 ID
     */
    override suspend fun deleteMessagesBySession(sessionId: String) {
        cacheDataSource.removeMessages(sessionId)
        localDataSource.deleteMessagesBySession(sessionId)
    }

    /**
     * 删除指定 ID 的消息。
     * 同时从缓存和数据库删除。
     * 
     * @param messageId 消息 ID
     */
    override suspend fun deleteMessage(messageId: String) {
        localDataSource.deleteMessage(messageId)
        // 从缓存中移除该消息
        cacheDataSource.getAllSessionIds().forEach { sessionId ->
            val cachedMessages = cacheDataSource.getMessages(sessionId)
            if (cachedMessages != null && cachedMessages.any { it.id == messageId }) {
                val updatedMessages = cachedMessages.filter { it.id != messageId }
                if (updatedMessages.isEmpty()) {
                    cacheDataSource.removeMessages(sessionId)
                } else {
                    cacheDataSource.putMessages(sessionId, updatedMessages)
                }
            }
        }
    }

    /**
     * 预热缓存。
     * 将指定会话的消息加载到缓存中。
     * 
     * @param sessionId 会话 ID
     * @param limit 限制数量，默认为 100
     */
    suspend fun warmupCache(sessionId: String, limit: Int = 100) {
        val messages = localDataSource.getMessagesByPage(sessionId, 0, limit)
        if (messages.isNotEmpty()) {
            cacheDataSource.putMessages(sessionId, messages)
        }
    }

    /**
     * 获取指定会话中最后一条助手消息。
     * 
     * @param sessionId 会话 ID
     * @return 最后一条助手消息，如果不存在则返回 null
     */
    override suspend fun getLastAssistantMessage(sessionId: String): MessageEntity? {
        val messages = observeMessages(sessionId).first()
        return messages.lastOrNull { it.role == MessageRole.ASSISTANT }
    }

    /**
     * 删除指定会话中最后一条助手消息。
     * 用于回滚操作。
     * 
     * @param sessionId 会话 ID
     * @return 是否成功删除
     */
    override suspend fun deleteLastAssistantMessage(sessionId: String): Boolean {
        val lastAssistantMessage = getLastAssistantMessage(sessionId)
        return if (lastAssistantMessage != null) {
            deleteMessage(lastAssistantMessage.id)
            true
        } else {
            false
        }
    }

    /**
     * 获取指定会话的缓存消息（仅用于调试）。
     * 
     * @param sessionId 会话 ID
     * @return 缓存中的消息列表，如果不存在则返回 null
     */
    override fun getCachedMessages(sessionId: String): List<MessageEntity>? {
        return cacheDataSource.getMessages(sessionId)
    }
}

