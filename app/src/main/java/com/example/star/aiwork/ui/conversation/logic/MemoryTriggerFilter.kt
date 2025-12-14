package com.example.star.aiwork.ui.conversation.logic

import android.util.Log
import com.example.star.aiwork.domain.usecase.embedding.ComputeEmbeddingUseCase
import com.example.star.aiwork.domain.usecase.embedding.SaveEmbeddingUseCase
import com.example.star.aiwork.domain.usecase.embedding.ShouldSaveAsMemoryUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
        val result = shouldSaveAsMemoryUseCase(text)
        if (result) {
            val textPreview = text.trim().take(100)
            Log.d("MemoryTriggerFilter", "✅ [过滤检查] 匹配记忆触发模式")
            Log.d("MemoryTriggerFilter", "   └─ 文本: $textPreview${if (text.length > 100) "..." else ""}")
        } else {
            Log.d("MemoryTriggerFilter", "❌ [过滤检查] 未匹配任何模式")
        }
        return result
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
        } catch (e: Exception) {
            // 静默处理错误，不影响正常消息流程
            android.util.Log.e("MemoryTriggerFilter", "Failed to save memory: ${e.message}", e)
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
        Log.d("MemoryTriggerFilter", "🔍 [处理记忆] 开始检查消息是否需要保存")
        Log.d("MemoryTriggerFilter", "   └─ 文本长度: ${text.length}, Embedding 维度: ${embedding.size}")
        
        if (!shouldSaveAsMemory(text)) {
            Log.d("MemoryTriggerFilter", "⏭️ [处理记忆] 未通过过滤器，跳过")
            return
        }
        
        // 如果 buffer 未提供，则跳过
        if (memoryBuffer == null) {
            Log.w("MemoryTriggerFilter", "⚠️ [处理记忆] MemoryBuffer 未提供，无法添加到 buffer")
            return
        }
        
        try {
            // 在后台线程异步执行，不阻塞消息发送
            withContext(Dispatchers.IO) {
                Log.d("MemoryTriggerFilter", "📦 [处理记忆] 准备添加到 buffer")
                val item = BufferedMemoryItem(text, embedding)
                memoryBuffer.add(item)
                Log.d("MemoryTriggerFilter", "✅ [处理记忆] 消息已成功添加到 buffer")
            }
        } catch (e: Exception) {
            // 静默处理错误，不影响正常消息流程
            Log.e("MemoryTriggerFilter", "❌ [处理记忆] 添加到 buffer 失败: ${e.message}", e)
        }
    }

    /**
     * 直接保存记忆（用于批量处理后的保存）
     */
    suspend fun saveMemoryWithEmbedding(text: String, embedding: FloatArray) {
        Log.d("MemoryTriggerFilter", "💾 [保存记忆] 开始保存到数据库")
        Log.d("MemoryTriggerFilter", "   └─ 文本: ${text.take(80)}${if (text.length > 80) "..." else ""}")
        Log.d("MemoryTriggerFilter", "   └─ Embedding 维度: ${embedding.size}")
        
        if (saveEmbeddingUseCase == null) {
            Log.w("MemoryTriggerFilter", "⚠️ [保存记忆] SaveEmbeddingUseCase 未提供，无法保存")
            return
        }
        
        try {
            // 创建 Embedding 对象并保存
            val embeddingModel = com.example.star.aiwork.domain.model.embedding.Embedding(
                id = 0, // 数据库会自动生成
                text = text,
                embedding = embedding
            )
            
            saveEmbeddingUseCase(embeddingModel)
            Log.d("MemoryTriggerFilter", "✅ [保存记忆] 已成功保存到数据库")
        } catch (e: Exception) {
            Log.e("MemoryTriggerFilter", "❌ [保存记忆] 保存失败: ${e.message}", e)
            throw e
        }
    }
}

