package com.example.star.aiwork.domain.usecase.embedding

import android.util.Log
import com.example.star.aiwork.domain.model.Model
import com.example.star.aiwork.domain.model.ProviderSetting
import com.example.star.aiwork.domain.model.embedding.Embedding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 处理缓冲区满时的批量记忆保存用例。
 * 
 * 当记忆缓冲区满了时，使用 AI 模型判断哪些消息应该被写入长期记忆，
 * 然后保存被选中的消息到数据库。
 * 
 * @param filterMemoryMessagesUseCase 用于判断哪些消息需要保存
 * @param saveEmbeddingUseCase 用于保存消息到数据库
 */
class ProcessBufferFullUseCase(
    private val filterMemoryMessagesUseCase: FilterMemoryMessagesUseCase,
    private val saveEmbeddingUseCase: SaveEmbeddingUseCase
) {
    /**
     * 处理缓冲区满的情况。
     * 调用 FilterMemoryMessagesUseCase 判断哪些消息需要保存，然后保存它们。
     * 
     * @param items 缓冲区中的消息列表，每个消息包含文本和 embedding
     * @param providerSetting AI 提供商设置
     * @param model AI 模型配置
     */
    suspend operator fun invoke(
        items: List<BufferedMemoryItem>,
        providerSetting: ProviderSetting,
        model: Model
    ) {
        Log.d("ProcessBufferFull", "=".repeat(80))
        Log.d("ProcessBufferFull", "🔄 [批量处理] Buffer 已满，开始批量处理")
        
        if (items.isEmpty()) {
            Log.w("ProcessBufferFull", "⚠️ [批量处理] 消息列表为空，跳过处理")
            return
        }

        Log.d("ProcessBufferFull", "   └─ 待处理消息数量: ${items.size}")
        items.forEachIndexed { index, item ->
            Log.d("ProcessBufferFull", "   [$index] ${item.text.take(60)}${if (item.text.length > 60) "..." else ""} (embedding: ${item.embedding.size}维)")
        }

        Log.d("ProcessBufferFull", "   └─ Provider: ${providerSetting.name}, Model: ${model.modelId}")

        try {
            // 提取文本列表
            val texts = items.map { it.text }
            Log.d("ProcessBufferFull", "📤 [批量处理] 调用 FilterMemoryMessagesUseCase 进行 AI 判断")
            Log.d("ProcessBufferFull", "   └─ 发送 ${texts.size} 条消息文本给 AI 模型")
            
            // 调用 FilterMemoryMessagesUseCase 判断哪些需要保存
            val indicesToSave = filterMemoryMessagesUseCase(
                messages = texts,
                providerSetting = providerSetting,
                model = model
            )
            
            Log.d("ProcessBufferFull", "📥 [批量处理] AI 模型返回结果")
            Log.d("ProcessBufferFull", "   └─ 需要保存的消息索引: $indicesToSave")
            Log.d("ProcessBufferFull", "   └─ 需要保存的消息数量: ${indicesToSave.size}/${items.size}")
            
            if (indicesToSave.isEmpty()) {
                Log.d("ProcessBufferFull", "⏭️ [批量处理] AI 模型判断没有消息需要写入长期记忆")
                Log.d("ProcessBufferFull", "=".repeat(80))
                return
            }
            
            // 记录被选中的消息详情
            indicesToSave.forEach { index ->
                if (index >= 0 && index < items.size) {
                    val item = items[index]
                    Log.d("ProcessBufferFull", "   ✅ 索引 $index 被选中: ${item.text.take(60)}${if (item.text.length > 60) "..." else ""}")
                } else {
                    Log.w("ProcessBufferFull", "   ⚠️ 无效索引: $index (总数: ${items.size})")
                }
            }
            
            // 在后台线程执行保存操作
            Log.d("ProcessBufferFull", "💾 [批量处理] 开始保存被选中的消息到数据库")
            withContext(Dispatchers.IO) {
                var successCount = 0
                var failCount = 0
                
                indicesToSave.forEach { index ->
                    if (index >= 0 && index < items.size) {
                        try {
                            val item = items[index]
                            Log.d("ProcessBufferFull", "   💾 正在保存索引 $index...")
                            
                            // 创建 Embedding 对象并保存
                            val embedding = Embedding(
                                id = 0, // 数据库会自动生成
                                text = item.text,
                                embedding = item.embedding
                            )
                            
                            saveEmbeddingUseCase(embedding)
                            successCount++
                            Log.d("ProcessBufferFull", "   ✅ 索引 $index 保存成功")
                        } catch (e: Exception) {
                            failCount++
                            Log.e("ProcessBufferFull", "   ❌ 索引 $index 保存失败: ${e.message}", e)
                        }
                    }
                }
                
                Log.d("ProcessBufferFull", "📊 [批量处理] 保存统计")
                Log.d("ProcessBufferFull", "   └─ 成功: $successCount, 失败: $failCount, 总计: ${indicesToSave.size}")
            }
            
            Log.d("ProcessBufferFull", "✅ [批量处理] 批量处理完成")
            Log.d("ProcessBufferFull", "=".repeat(80))
            
        } catch (e: Exception) {
            Log.e("ProcessBufferFull", "❌ [批量处理] 批量处理失败: ${e.message}", e)
            Log.e("ProcessBufferFull", "   └─ 异常类型: ${e.javaClass.simpleName}")
            e.printStackTrace()
            Log.d("ProcessBufferFull", "=".repeat(80))
            // 发生错误时静默处理，不影响正常流程
        }
    }

    /**
     * 缓冲区中的记忆项。
     * 包含文本和对应的 embedding 向量。
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
}

