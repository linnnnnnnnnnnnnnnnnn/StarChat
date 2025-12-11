package com.example.star.aiwork.data.local.datasource.session

import com.example.star.aiwork.domain.model.SessionEntity

/**
 * 会话缓存数据源接口。
 * 
 * 提供基于内存的 LRU 缓存功能，用于快速访问最近使用的会话数据。
 * 缓存策略参考 ConversationUiStateCache 的实现。
 */
interface SessionCacheDataSource {

    /**
     * 获取指定会话的缓存数据。
     * 如果存在，会将其标记为最近使用（移动到链表末尾）。
     * 
     * @param sessionId 会话 ID
     * @return 会话实体，如果不存在则返回 null
     */
    fun get(sessionId: String): SessionEntity?

    /**
     * 获取或创建指定会话的缓存数据。
     * 如果不存在，会创建新的缓存条目并添加到缓存中。
     * 如果缓存已满，会自动移除最久未使用的数据。
     * 
     * @param sessionId 会话 ID
     * @param factory 用于创建新会话实体的工厂函数
     * @return 会话实体
     */
    fun getOrCreate(
        sessionId: String,
        factory: (String) -> SessionEntity
    ): SessionEntity

    /**
     * 将会话数据添加到缓存中。
     * 如果已存在，会更新并标记为最近使用。
     * 
     * @param sessionId 会话 ID
     * @param session 会话实体
     */
    fun put(sessionId: String, session: SessionEntity)

    /**
     * 移除指定会话的缓存数据。
     * 
     * @param sessionId 会话 ID
     * @return 被移除的会话实体，如果不存在则返回 null
     */
    fun remove(sessionId: String): SessionEntity?

    /**
     * 清空所有缓存的数据。
     */
    fun clear()

    /**
     * 获取当前缓存的大小。
     */
    fun size(): Int

    /**
     * 获取所有缓存的会话 ID。
     */
    fun getAllSessionIds(): Set<String>

    /**
     * 获取所有缓存的会话数据。
     */
    fun getAllSessions(): Map<String, SessionEntity>
}

