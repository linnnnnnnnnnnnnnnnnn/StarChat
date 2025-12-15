package com.example.star.aiwork.domain.usecase.embedding

import com.example.star.aiwork.domain.usecase.embedding.ComputeEmbeddingUseCase
import com.example.star.aiwork.domain.usecase.embedding.SaveEmbeddingUseCase
import com.example.star.aiwork.domain.usecase.embedding.ShouldSaveAsMemoryUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.star.aiwork.domain.model.embedding.Embedding

/**
 * 记忆触发过滤器
 *
 * 检测用户输入中的记忆触发词和模式，当匹配时添加到 buffer 中。
 * buffer 满了之后会通过 FilterMemoryMessagesUseCase 进行批量判断并保存。
 */
class MemoryTriggerFilter(
    private val shouldSaveAsMemoryUseCase: ShouldSaveAsMemoryUseCase,
    private val computeEmbeddingUseCase: ComputeEmbeddingUseCase?,
    private val saveEmbeddingUseCase: SaveEmbeddingUseCase?,
    private val memoryBuffer: MemoryBuffer?
) {

    /**
     * 检查输入文本是否匹配任何记忆触发模式
     *
     * @param text 用户输入的文本
     * @return 如果匹配则返回 true，否则返回 false
     */
    fun shouldSaveAsMemory(text: String): Boolean {
        return shouldSaveAsMemoryUseCase(text)
    }

    /**
     * 处理记忆保存
     * 如果输入匹配触发模式，则计算嵌入向量并保存
     *
     * @param text 用户输入的文本
     */
    suspend fun processMemoryIfNeeded(text: String) {
        if (!shouldSaveAsMemory(text)) {
            return
        }

        // 如果用例未提供，则跳过
        if (computeEmbeddingUseCase == null || saveEmbeddingUseCase == null) {
            return
        }

        try {
            // 在后台线程执行
            withContext(Dispatchers.IO) {
                // 计算嵌入向量
                val embedding = computeEmbeddingUseCase(text)

                if (embedding != null) {
                    saveMemoryWithEmbedding(text, embedding)
                }
            }
        } catch (_: Exception) {
            // 静默处理错误，不影响正常消息流程
        }
    }

    /**
     * 使用已计算的嵌入向量处理记忆
     * 如果输入匹配触发模式，则添加到 buffer 中，等待批量处理
     *
     * @param text 用户输入的文本
     * @param embedding 已计算的嵌入向量
     */
    suspend fun processMemoryIfNeededWithEmbedding(text: String, embedding: FloatArray) {
        if (!shouldSaveAsMemory(text)) {
            return
        }

        // 如果 buffer 未提供，则跳过
        if (memoryBuffer == null) {
            return
        }

        try {
            // 在后台线程异步执行，不阻塞消息发送
            withContext(Dispatchers.IO) {
                val item = BufferedMemoryItem(text, embedding)
                memoryBuffer.add(item)
            }
        } catch (_: Exception) {
            // 静默处理错误，不影响正常消息流程
        }
    }

    /**
     * 直接保存记忆（用于批量处理后的保存）
     */
    suspend fun saveMemoryWithEmbedding(text: String, embedding: FloatArray) {
        if (saveEmbeddingUseCase == null) {
            return
        }

        val embeddingModel = Embedding(
            id = 0, // 数据库会自动生成
            text = text,
            embedding = embedding
        )

        saveEmbeddingUseCase(embeddingModel)
    }
}


