package com.example.star.aiwork.domain.usecase.embedding

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 记忆缓冲区
 *
 * 用于缓存通过过滤器的消息（包含文本和 embedding），
 * 当 buffer 满了（size == maxSize）时，触发批量处理。
 */
data class BufferedMemoryItem(
    val text: String,
    val embedding: FloatArray
) {
    // FloatArray 需要自定义 equals 和 hashCode
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BufferedMemoryItem

        if (text != other.text) return false
        if (!embedding.contentEquals(other.embedding)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = text.hashCode()
        result = 31 * result + embedding.contentHashCode()
        return result
    }
}

class MemoryBuffer(
    private val maxSize: Int = 5,
    private val onBufferFull: suspend (List<BufferedMemoryItem>) -> Unit
) {
    private val mutex = Mutex()
    private val buffer = mutableListOf<BufferedMemoryItem>()

    /**
     * 添加一条消息到 buffer
     * 如果 buffer 满了，会触发 onBufferFull 回调
     */
    suspend fun add(item: BufferedMemoryItem) {
        val itemsToProcess = mutex.withLock {
            buffer.add(item)
            val currentSize = buffer.size

            if (currentSize >= maxSize) {
                val items = buffer.toList()
                buffer.clear()
                items
            } else {
                null
            }
        }

        // 在锁外执行回调，避免阻塞
        itemsToProcess?.let {
            onBufferFull(it)
        }
    }

    /**
     * 获取当前 buffer 的大小
     */
    suspend fun size(): Int {
        return mutex.withLock {
            buffer.size
        }
    }

    /**
     * 清空 buffer
     */
    suspend fun clear() {
        mutex.withLock {
            buffer.clear()
        }
    }

    /**
     * 手动触发处理（即使 buffer 未满）
     * 用于应用关闭等场景
     */
    suspend fun flush() {
        val itemsToProcess = mutex.withLock {
            if (buffer.isNotEmpty()) {
                val items = buffer.toList()
                buffer.clear()
                items
            } else {
                null
            }
        }

        // 在锁外执行回调，避免阻塞
        itemsToProcess?.let {
            onBufferFull(it)
        }
    }
}


