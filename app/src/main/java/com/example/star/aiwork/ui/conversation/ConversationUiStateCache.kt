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

package com.example.star.aiwork.ui.conversation

import java.util.LinkedHashMap

/**
 * LRU Cache 用于管理多个会话的 ConversationUiState。
 * 
 * 使用 LinkedHashMap 实现 LRU（Least Recently Used）缓存策略。
 * 当缓存大小超过限制时，会自动移除最久未使用的状态。
 * 
 * @param maxSize 缓存的最大容量，默认为 5
 */
class ConversationUiStateCache(
    private val maxSize: Int = 5
) {
    // 使用 LinkedHashMap 实现 LRU：accessOrder = true 表示按访问顺序排序
    private val cache: LinkedHashMap<String, ConversationUiState> = object : LinkedHashMap<String, ConversationUiState>(
        maxSize + 1, // 初始容量设为 maxSize + 1，避免扩容
        0.75f,      // 负载因子
        true        // accessOrder = true，按访问顺序排序（LRU 的关键）
    ) {
        // 重写 removeEldestEntry，当大小超过 maxSize 时自动移除最旧的条目
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ConversationUiState>?): Boolean {
            return size > maxSize
        }
    }

    /**
     * 获取指定会话的 UI 状态。
     * 如果存在，会将其标记为最近使用（移动到链表末尾）。
     * 
     * @param sessionId 会话 ID
     * @return UI 状态，如果不存在则返回 null
     */
    @Synchronized
    fun get(sessionId: String): ConversationUiState? {
        return cache[sessionId] // LinkedHashMap 的 get 操作会自动更新访问顺序
    }

    /**
     * 获取或创建指定会话的 UI 状态。
     * 如果不存在，会创建新的状态并添加到缓存中。
     * 如果缓存已满，会自动移除最久未使用的状态。
     * 
     * @param sessionId 会话 ID
     * @param sessionName 会话名称
     * @param factory 用于创建新 UI 状态的工厂函数
     * @return UI 状态
     */
    @Synchronized
    fun getOrCreate(
        sessionId: String,
        sessionName: String,
        factory: (String, String) -> ConversationUiState
    ): ConversationUiState {
        return cache[sessionId] ?: run {
            val newState = factory(sessionId, sessionName)
            cache[sessionId] = newState
            newState
        }
    }

    /**
     * 将 UI 状态添加到缓存中。
     * 如果已存在，会更新并标记为最近使用。
     * 
     * @param sessionId 会话 ID
     * @param state UI 状态
     */
    @Synchronized
    fun put(sessionId: String, state: ConversationUiState) {
        cache[sessionId] = state
    }

    /**
     * 移除指定会话的 UI 状态。
     * 
     * @param sessionId 会话 ID
     * @return 被移除的 UI 状态，如果不存在则返回 null
     */
    @Synchronized
    fun remove(sessionId: String): ConversationUiState? {
        return cache.remove(sessionId)
    }

    /**
     * 清空所有缓存的状态。
     */
    @Synchronized
    fun clear() {
        cache.clear()
    }

    /**
     * 获取当前缓存的大小。
     */
    @Synchronized
    fun size(): Int {
        return cache.size
    }

    /**
     * 获取所有缓存的会话 ID。
     */
    @Synchronized
    fun getAllSessionIds(): Set<String> {
        return cache.keys.toSet()
    }

    /**
     * 获取所有缓存的 UI 状态。
     */
    @Synchronized
    fun getAllStates(): Map<String, ConversationUiState> {
        return cache.toMap() // 返回副本，避免外部修改
    }
}

