package com.example.star.aiwork.data.local.datasource.message

import com.example.star.aiwork.domain.model.MessageEntity

/**
 * 消息缓存数据源接口。
 * 
 * 提供基于内存的 LRU 缓存功能，用于快速访问最近使用的会话消息数据。
 * 缓存策略参考 SessionCacheDataSource 的实现。
 */
interface MessageCacheDataSource {

    /**
     * 获取指定会话的消息列表缓存。
     * 如果存在，会将其标记为最近使用（移动到链表末尾）。
     * 
     * @param sessionId 会话 ID
     * @return 消息列表，如果不存在则返回 null
     */
    fun getMessages(sessionId: String): List<MessageEntity>?

    /**
     * 将消息列表添加到缓存中。
     * 如果已存在，会更新并标记为最近使用。
     * 
     * @param sessionId 会话 ID
     * @param messages 消息列表
     */
    fun putMessages(sessionId: String, messages: List<MessageEntity>)

    /**
     * 移除指定会话的消息缓存数据。
     * 
     * @param sessionId 会话 ID
     * @return 被移除的消息列表，如果不存在则返回 null
     */
    fun removeMessages(sessionId: String): List<MessageEntity>?

    /**
     * 清空所有缓存的数据。
     */
    fun clear()

    /**
     * 获取当前缓存的大小（缓存的会话数量）。
     */
    fun size(): Int

    /**
     * 获取所有缓存的会话 ID。
     */
    fun getAllSessionIds(): Set<String>
}

