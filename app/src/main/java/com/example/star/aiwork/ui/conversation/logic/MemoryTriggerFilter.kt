package com.example.star.aiwork.ui.conversation.logic

import com.example.star.aiwork.domain.usecase.embedding.ComputeEmbeddingUseCase
import com.example.star.aiwork.domain.usecase.embedding.SaveEmbeddingUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 记忆触发过滤器
 * 
 * 检测用户输入中的记忆触发词和模式，当匹配时自动保存为嵌入向量。
 */
class MemoryTriggerFilter(
    private val computeEmbeddingUseCase: ComputeEmbeddingUseCase?,
    private val saveEmbeddingUseCase: SaveEmbeddingUseCase?
) {
    
    companion object {
        /**
         * 显式触发词列表
         */
        private val EXPLICIT_TRIGGERS = listOf(
            "记住", "帮我记", "加入记忆", "牢记",
            "以后你都", "永远记", "保存到记忆"
        )

        /**
         * 身份模式（正则表达式）
         */
        private val IDENTITY_PATTERNS = listOf(
            Regex("我叫(.+?)"),
            Regex("我是(.+?)"),
            Regex("我住在(.+?)"),
            Regex("我来自(.+?)"),
            Regex("我的职业是(.+?)")
        )

        /**
         * 偏好模式（正则表达式）
         */
        private val PREFERENCE_PATTERNS = listOf(
            Regex("我喜欢(.+?)"),
            Regex("我更喜欢(.+?)"),
            Regex("我希望你(.+?)"),
            Regex("以后请你(.+?)"),
            Regex("你以后回答我(.+?)")
        )

        /**
         * 长期目标模式（正则表达式）
         */
        private val LONG_TERM_GOALS = listOf(
            Regex("我想在未来(.+?)"),
            Regex("我接下来(.+?)"),
            Regex("我计划(.+?)"),
            Regex("我打算(.+?)"),
            Regex("我的目标是(.+?)")
        )
    }

    /**
     * 检查输入文本是否匹配任何记忆触发模式
     * 
     * @param text 用户输入的文本
     * @return 如果匹配则返回 true，否则返回 false
     */
    fun shouldSaveAsMemory(text: String): Boolean {
        if (text.isBlank()) return false
        
        val trimmedText = text.trim()
        
        // 检查显式触发词
        if (EXPLICIT_TRIGGERS.any { trigger -> trimmedText.contains(trigger) }) {
            return true
        }
        
        // 检查身份模式
        if (IDENTITY_PATTERNS.any { pattern -> pattern.containsMatchIn(trimmedText) }) {
            return true
        }
        
        // 检查偏好模式
        if (PREFERENCE_PATTERNS.any { pattern -> pattern.containsMatchIn(trimmedText) }) {
            return true
        }
        
        // 检查长期目标模式
        if (LONG_TERM_GOALS.any { pattern -> pattern.containsMatchIn(trimmedText) }) {
            return true
        }
        
        return false
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
                    // 创建 Embedding 对象并保存
                    val embeddingModel = com.example.star.aiwork.domain.model.embedding.Embedding(
                        id = 0, // 数据库会自动生成
                        text = text,
                        embedding = embedding
                    )
                    
                    saveEmbeddingUseCase(embeddingModel)
                }
            }
        } catch (e: Exception) {
            // 静默处理错误，不影响正常消息流程
            android.util.Log.e("MemoryTriggerFilter", "Failed to save memory: ${e.message}", e)
        }
    }
}

