/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.star.aiwork.infra.cache

import com.example.star.aiwork.data.local.datasource.message.MessageCacheDataSource
import com.example.star.aiwork.domain.model.MessageEntity
import java.util.LinkedHashMap

/**
 * LRU Cache 用于管理多个会话的消息列表。
 * 
 * 使用 LinkedHashMap 实现 LRU（Least Recently Used）缓存策略。
 * 当缓存大小超过限制时，会自动移除最久未使用的消息列表。
 * 
 * @param maxSize 缓存的最大容量（会话数量），默认为 10
 */
class MessageCacheDataSourceImpl(
    private val maxSize: Int = 10
) : MessageCacheDataSource {
    // 使用 LinkedHashMap 实现 LRU：accessOrder = true 表示按访问顺序排序
    private val cache: LinkedHashMap<String, List<MessageEntity>> = object : LinkedHashMap<String, List<MessageEntity>>(
        maxSize + 1, // 初始容量设为 maxSize + 1，避免扩容
        0.75f,      // 负载因子
        true        // accessOrder = true，按访问顺序排序（LRU 的关键）
    ) {
        // 重写 removeEldestEntry，当大小超过 maxSize 时自动移除最旧的条目
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<MessageEntity>>?): Boolean {
            return size > maxSize
        }
    }

    /**
     * 获取指定会话的消息列表缓存。
     * 如果存在，会将其标记为最近使用（移动到链表末尾）。
     * 
     * @param sessionId 会话 ID
     * @return 消息列表，如果不存在则返回 null
     */
    @Synchronized
    override fun getMessages(sessionId: String): List<MessageEntity>? {
        return cache[sessionId] // LinkedHashMap 的 get 操作会自动更新访问顺序
    }

    /**
     * 将消息列表添加到缓存中。
     * 如果已存在，会更新并标记为最近使用。
     * 
     * @param sessionId 会话 ID
     * @param messages 消息列表
     */
    @Synchronized
    override fun putMessages(sessionId: String, messages: List<MessageEntity>) {
        cache[sessionId] = messages
    }

    /**
     * 移除指定会话的消息缓存数据。
     * 
     * @param sessionId 会话 ID
     * @return 被移除的消息列表，如果不存在则返回 null
     */
    @Synchronized
    override fun removeMessages(sessionId: String): List<MessageEntity>? {
        return cache.remove(sessionId)
    }

    /**
     * 清空所有缓存的数据。
     */
    @Synchronized
    override fun clear() {
        cache.clear()
    }

    /**
     * 获取当前缓存的大小（缓存的会话数量）。
     */
    @Synchronized
    override fun size(): Int {
        return cache.size
    }

    /**
     * 获取所有缓存的会话 ID。
     */
    @Synchronized
    override fun getAllSessionIds(): Set<String> {
        return cache.keys.toSet()
    }
}

