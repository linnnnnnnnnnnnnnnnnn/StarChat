package com.example.star.aiwork.domain.usecase.message

import android.util.Log
import com.example.star.aiwork.domain.model.ChatDataItem
import com.example.star.aiwork.domain.model.MessageRole
import com.example.star.aiwork.domain.repository.MessageRepository
import com.example.star.aiwork.domain.usecase.embedding.ComputeEmbeddingUseCase
import com.example.star.aiwork.domain.usecase.embedding.SearchEmbeddingUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * 根据当前会话历史和本次用户输入，构造要发送给模型的消息列表的 UseCase。
 *
 * - 会自动从 [MessageRepository] 读取上下文历史消息；
 * - 可以选择性地从知识库检索补充上下文（由调用方提供 [retrieveKnowledge]）；
 * - 可以选择性地基于 Embedding 搜索长期记忆，并注入为 SYSTEM 消息；
 * - 返回构造好的历史消息（不包含当前用户消息）、当前用户消息以及可复用的 embedding。
 */
class ConstructMessagesUseCase(
    private val messageRepository: MessageRepository,
    private val computeEmbeddingUseCase: ComputeEmbeddingUseCase? = null,
    private val searchEmbeddingUseCase: SearchEmbeddingUseCase? = null,
) {

    data class Result(
        /** 发送给模型的历史上下文消息（不包含本次用户消息） */
        val history: List<ChatDataItem>,
        /** 本次用户消息（已根据知识库等做过上下文增强） */
        val userMessage: ChatDataItem,
        /** 可复用的 embedding（例如用于记忆存储），如果未计算则为 null */
        val computedEmbedding: FloatArray? = null,
    )

    suspend operator fun invoke(
        sessionId: String,
        /** 原始用户输入文本 */
        inputContent: String,
        /** 是否为自动触发（如自动补全、记忆整理等），自动触发时不会搜索长期记忆 */
        isAutoTriggered: Boolean,
        /** 直接传入已构造好的知识库上下文（可选） */
        knowledgeContext: String? = null,
        /** 当 [knowledgeContext] 为空时，可选的知识检索函数，由调用方提供 */
        retrieveKnowledge: (suspend (String) -> String)? = null,
        /** 从长期记忆中检索的条目数量 */
        topK: Int = 3,
        /** 获取历史消息时的最大条数 */
        historyLimit: Int = 10,
    ): Result = withContext(Dispatchers.IO) {
        // 1. 知识库上下文增强
        val finalKnowledgeContext = knowledgeContext ?: if (!isAutoTriggered && retrieveKnowledge != null) {
            retrieveKnowledge(inputContent)
        } else {
            ""
        }

        val augmentedInput = if (finalKnowledgeContext.isNotBlank()) {
            """
            [Context from Knowledge Base]
            $finalKnowledgeContext
            
            [User Question]
            $inputContent
            """.trimIndent()
        } else {
            inputContent
        }

        val finalUserContent = augmentedInput

        // 2. 从 MessageRepository 读取上下文历史消息
        val allMessages = messageRepository.observeMessages(sessionId).first()
        // 为了与原有逻辑保持一致：排除 SYSTEM，按时间顺序取最近 N 条
        val historyEntities = allMessages
            .filter { it.role != MessageRole.SYSTEM }
            .sortedBy { it.createdAt }
            .takeLast(historyLimit)

        val history = historyEntities.map { entity ->
            ChatDataItem(
                role = entity.role.name.lowercase(),
                content = entity.content,
                localFilePath = entity.metadata.localFilePath,
                imageBase64 = null,
            )
        }.toMutableList()

        // 3. 计算 embedding 并搜索长期记忆
        val relatedSentences = mutableListOf<String>()
        var computedEmbedding: FloatArray? = null

        if (!isAutoTriggered &&
            computeEmbeddingUseCase != null &&
            searchEmbeddingUseCase != null &&
            inputContent.isNotBlank()
        ) {
            try {
                Log.d("ConstructMessagesUseCase", "开始搜索长期记忆: inputContent='$inputContent', topK=$topK")
                val queryEmbedding = computeEmbeddingUseCase(inputContent)
                if (queryEmbedding != null) {
                    computedEmbedding = queryEmbedding
                    Log.d("ConstructMessagesUseCase", "成功计算 embedding，维度: ${queryEmbedding.size}")
                    val searchResults = searchEmbeddingUseCase(queryEmbedding, topK)
                    Log.d("ConstructMessagesUseCase", "搜索返回 ${searchResults.size} 条结果")
                    relatedSentences.addAll(searchResults.map { it.first.text })
                    Log.d("ConstructMessagesUseCase", "✅ 找到 ${relatedSentences.size} 条相关句子")
                    relatedSentences.forEachIndexed { index, sentence ->
                        Log.d(
                            "ConstructMessagesUseCase",
                            "  相关句子 ${index + 1}: ${sentence.take(100)}${if (sentence.length > 100) "..." else ""}",
                        )
                    }
                } else {
                    Log.w("ConstructMessagesUseCase", "⚠️ 计算 embedding 返回 null")
                }
            } catch (e: Exception) {
                Log.e("ConstructMessagesUseCase", "❌ 计算 embedding 或搜索相关句子失败", e)
            }
        } else {
            val reasons = mutableListOf<String>()
            if (isAutoTriggered) reasons.add("isAutoTriggered=true")
            if (computeEmbeddingUseCase == null) reasons.add("computeEmbeddingUseCase=null")
            if (searchEmbeddingUseCase == null) reasons.add("searchEmbeddingUseCase=null")
            if (inputContent.isBlank()) reasons.add("inputContent为空")
            Log.d("ConstructMessagesUseCase", "⏭️ 跳过长期记忆搜索: ${reasons.joinToString(", ")}")
        }

        // 4. 将长期记忆注入为 SYSTEM 消息
        if (relatedSentences.isNotEmpty()) {
            val relatedContextText = relatedSentences.joinToString("\n") { "- $it" }
            val longTermMemoryText = """
                [长期记忆 - 从用户历史对话中提取的相关信息]
                以下是基于当前问题从长期记忆中检索到的相关信息，这些信息来自用户之前的对话记录：
                
                $relatedContextText
                
                请注意：上述内容是长期记忆，不是当前对话的历史记录。你可以参考这些长期记忆，如果记忆与用户信息关联度低，你也可以忽略。
            """.trimIndent()

            history.add(
                ChatDataItem(
                    role = MessageRole.SYSTEM.name.lowercase(),
                    content = longTermMemoryText,
                    localFilePath = null,
                    imageBase64 = null,
                ),
            )

            Log.d(
                "ConstructMessagesUseCase",
                "✅ 长期记忆已添加到 history，当前 history 数量: ${history.size}",
            )
        } else {
            Log.d("ConstructMessagesUseCase", "⚠️ 没有找到相关句子，长期记忆不会被添加到消息中")
        }

        // 5. 确保历史中不包含“当前这条用户输入”
        if (history.isNotEmpty() && history.last().role.equals("user", ignoreCase = true)) {
            // 最后一条通常是当前这条用户输入，在构造 history 时需要去掉
            history.removeAt(history.lastIndex)
        }

        val userMessage = ChatDataItem(
            role = MessageRole.USER.name.lowercase(),
            content = finalUserContent,
            localFilePath = null,
            imageBase64 = null,
        )

        Result(
            history = history,
            userMessage = userMessage,
            computedEmbedding = computedEmbedding,
        )
    }
}


