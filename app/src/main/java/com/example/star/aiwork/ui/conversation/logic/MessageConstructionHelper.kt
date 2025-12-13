package com.example.star.aiwork.ui.conversation.logic

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.util.Log
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Size
import com.example.star.aiwork.domain.model.Agent
import com.example.star.aiwork.domain.model.ChatDataItem
import com.example.star.aiwork.domain.model.MessageRole
import com.example.star.aiwork.domain.repository.MessageRepository
import com.example.star.aiwork.domain.usecase.embedding.ComputeEmbeddingUseCase
import com.example.star.aiwork.domain.usecase.embedding.SearchEmbeddingUseCase
import com.example.star.aiwork.infra.util.toBase64
import com.example.star.aiwork.ui.ai.UIMessage
import com.example.star.aiwork.ui.ai.UIMessagePart
import com.example.star.aiwork.ui.conversation.ConversationUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * 消息构造结果，包含消息列表和计算好的 embedding（如果计算了的话）
 */
data class MessageConstructionResult(
    val messages: List<UIMessage>,
    val computedEmbedding: FloatArray? = null
)

object MessageConstructionHelper {

    suspend fun constructMessagesToSend(
        uiState: ConversationUiState,
        authorMe: String,
        inputContent: String,
        isAutoTriggered: Boolean,
        activeAgent: Agent?,
        retrieveKnowledge: suspend (String) -> String,
        context: Context,
        messageRepository: MessageRepository?,
        sessionId: String,
        computeEmbeddingUseCase: ComputeEmbeddingUseCase? = null,
        searchEmbeddingUseCase: SearchEmbeddingUseCase? = null,
        topK: Int = 3
    ): List<UIMessage> {
        return constructMessages(
            uiState,
            authorMe,
            inputContent,
            isAutoTriggered,
            activeAgent,
            null,
            retrieveKnowledge,
            context,
            messageRepository,
            sessionId,
            computeEmbeddingUseCase,
            searchEmbeddingUseCase,
            topK
        ).messages
    }

    suspend fun constructMessages(
        uiState: ConversationUiState,
        authorMe: String,
        inputContent: String,
        isAutoTriggered: Boolean,
        activeAgent: Agent?,
        knowledgeContext: String? = null,
        retrieveKnowledge: (suspend (String) -> String)? = null,
        context: Context,
        messageRepository: MessageRepository?,
        sessionId: String,
        computeEmbeddingUseCase: ComputeEmbeddingUseCase? = null,
        searchEmbeddingUseCase: SearchEmbeddingUseCase? = null,
        topK: Int = 3
    ): MessageConstructionResult {

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

        val finalUserContent = if (activeAgent != null && !isAutoTriggered) {
            activeAgent.messageTemplate.replace("{{ message }}", augmentedInput)
        } else {
            augmentedInput
        }

        // 获取历史消息（当前对话的历史聊天记录）
        // 注意：USER 和 ASSISTANT 角色的消息本身就是历史聊天记录，模型可以通过角色区分
        // 从 Repository 获取消息，而不是从 uiState.messages
        val contextMessages = withContext(Dispatchers.IO) {
            messageRepository?.observeMessages(sessionId)?.first()
                ?.filter { it.role != MessageRole.SYSTEM }
                ?.reversed()
                ?.takeLast(10)
                ?.map { entity ->
                    val parts = mutableListOf<UIMessagePart>()
                    if (entity.content.isNotEmpty()) {
                        parts.add(UIMessagePart.Text(entity.content))
                    }
                    UIMessage(role = entity.role, parts = parts)
                }?.toMutableList() ?: mutableListOf()
        }

        // 在向模型发送信息之前，计算用户输入的 embedding 并搜索相关句子
        val relatedSentences = mutableListOf<String>()
        var computedEmbedding: FloatArray? = null
        if (!isAutoTriggered && computeEmbeddingUseCase != null && searchEmbeddingUseCase != null && inputContent.isNotBlank()) {
            try {
                Log.d("MessageConstruction", "开始搜索长期记忆: inputContent='$inputContent', topK=$topK")
                // 计算用户输入的 embedding
                val queryEmbedding = computeEmbeddingUseCase(inputContent)
                if (queryEmbedding != null) {
                    // 保存计算好的 embedding，供后续保存使用，避免重复计算
                    computedEmbedding = queryEmbedding
                    Log.d("MessageConstruction", "成功计算 embedding，维度: ${queryEmbedding.size}")
                    // 搜索 top-k 相关的句子
                    val searchResults = searchEmbeddingUseCase(queryEmbedding, topK)
                    Log.d("MessageConstruction", "搜索返回 ${searchResults.size} 条结果")
                    // 提取文本（注意是 text 而不是 floatarray）
                    relatedSentences.addAll(searchResults.map { it.first.text })
                    Log.d("MessageConstruction", "✅ 找到 ${relatedSentences.size} 条相关句子")
                    relatedSentences.forEachIndexed { index, sentence ->
                        Log.d("MessageConstruction", "  相关句子 ${index + 1}: ${sentence.take(100)}${if (sentence.length > 100) "..." else ""}")
                    }
                } else {
                    Log.w("MessageConstruction", "⚠️ 计算 embedding 返回 null")
                }
            } catch (e: Exception) {
                Log.e("MessageConstruction", "❌ 计算 embedding 或搜索相关句子失败", e)
            }
        } else {
            val reasons = mutableListOf<String>()
            if (isAutoTriggered) reasons.add("isAutoTriggered=true")
            if (computeEmbeddingUseCase == null) reasons.add("computeEmbeddingUseCase=null")
            if (searchEmbeddingUseCase == null) reasons.add("searchEmbeddingUseCase=null")
            if (inputContent.isBlank()) reasons.add("inputContent为空")
            Log.d("MessageConstruction", "⏭️ 跳过长期记忆搜索: ${reasons.joinToString(", ")}")
        }

        // 将搜索到的相关句子添加到历史记录后面（作为系统消息或用户消息的上下文）
        if (relatedSentences.isNotEmpty()) {
            val relatedContextText = relatedSentences.joinToString("\n") { "- $it" }
            val relatedContextMessage = UIMessage(
                role = MessageRole.SYSTEM,
                parts = listOf(UIMessagePart.Text("""
                    [长期记忆 - 从用户历史对话中提取的相关信息]
                    以下是基于当前问题从长期记忆中检索到的相关信息，这些信息来自用户之前的对话记录：
                    
                    $relatedContextText
                    
                    请注意：上述内容是长期记忆，不是当前对话的历史记录。你可以参考这些长期记忆，如果记忆与用户信息关联度低，你也可以忽略。
                """.trimIndent()))
            )
            // 在历史消息后面添加相关句子
            contextMessages.add(relatedContextMessage)
            Log.d("MessageConstruction", "✅ 长期记忆已添加到 contextMessages，当前 contextMessages 数量: ${contextMessages.size}")
        } else {
            Log.d("MessageConstruction", "⚠️ 没有找到相关句子，长期记忆不会被添加到消息中")
        }

        val messagesToSend = mutableListOf<UIMessage>()

        activeAgent?.systemPrompt?.takeIf { it.isNotEmpty() }?.let {
            messagesToSend.add(UIMessage(role = MessageRole.SYSTEM, parts = listOf(UIMessagePart.Text(it))))
        }

        activeAgent?.presetMessages?.forEach { preset ->
            messagesToSend.add(UIMessage(role = preset.role, parts = listOf(UIMessagePart.Text(preset.content))))
        }

        Log.d("MessageConstruction", "准备添加 contextMessages 到 messagesToSend，contextMessages 数量: ${contextMessages.size}")
        // 检查 contextMessages 中是否包含长期记忆
        val hasLongTermMemory = contextMessages.any { message ->
            message.role == MessageRole.SYSTEM && 
            message.parts.any { part ->
                part is UIMessagePart.Text && part.text.contains("[长期记忆")
            }
        }
        if (hasLongTermMemory) {
            Log.d("MessageConstruction", "✅ contextMessages 中包含长期记忆")
        } else {
            Log.d("MessageConstruction", "⚠️ contextMessages 中不包含长期记忆")
        }
        
        messagesToSend.addAll(contextMessages)
        Log.d("MessageConstruction", "✅ 已添加 contextMessages，当前 messagesToSend 数量: ${messagesToSend.size}")

        if (messagesToSend.isNotEmpty() && messagesToSend.last().role == MessageRole.USER) {
            messagesToSend.removeAt(messagesToSend.lastIndex)
        }

        val currentMessageParts = mutableListOf<UIMessagePart>()
        currentMessageParts.add(UIMessagePart.Text(finalUserContent))

        if (!isAutoTriggered) {
            // 从 Repository 获取最后一条用户消息
            val lastUserMsg = withContext(Dispatchers.IO) {
                messageRepository?.observeMessages(sessionId)?.first()
                    ?.findLast { it.role == MessageRole.USER }
            }
            val imageUrl = lastUserMsg?.metadata?.remoteUrl ?: lastUserMsg?.metadata?.localFilePath
            if (imageUrl != null) {
                try {
                    val imageUri = Uri.parse(imageUrl)

                    // --- FIX: Take persistable URI permission to access the image after app restart ---
                    try {
                        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                        context.contentResolver.takePersistableUriPermission(imageUri, flags)
                    } catch (e: SecurityException) {
                        Log.e("MessageConstruction", "Failed to take persistable URI permission for $imageUri", e)
                        // This might happen if the URI provider doesn't support persistable permissions.
                        // The image will likely fail to load on next app launch.
                    }
                    // --- END FIX ---
                    
                    // Use the new helper to get a scaled-down Base64 string
                    val base64Image = createScaledBase64Image(context, imageUri)

                    if (base64Image != null) {
                        val mimeType = context.contentResolver.getType(imageUri) ?: "image/jpeg"
                        currentMessageParts.add(UIMessagePart.Image(
                            url = "data:$mimeType;base64,$base64Image",
                            originalUri = imageUri.toString()
                        ))
                    }
                } catch (t: Throwable) {
                    //Log.e("MessageConstruction", "Failed to process and scale image URI: ${lastUserMsg.imageUrl}", t)
                }
            }
        }

        messagesToSend.add(UIMessage(
            role = MessageRole.USER,
            parts = currentMessageParts
        ))
        
        return MessageConstructionResult(
            messages = messagesToSend,
            computedEmbedding = computedEmbedding
        )
    }

    /**
     * Converts a UIMessage to a ChatDataItem for database persistence.
     */
    fun toChatDataItem(message: UIMessage): ChatDataItem {
        val builder = StringBuilder()
        var localFilePath: String? = null
        var imageBase64: String? = null

        message.parts.forEach { part ->
            when (part) {
                is UIMessagePart.Text -> builder.append(part.text)
                is UIMessagePart.Image -> {
                    // IMPORTANT: Only append a placeholder for the content.
                    // The actual image path is stored in localFilePath.
                    if (builder.isNotEmpty()) builder.append(" ")
                    builder.append("[image]")
                    localFilePath = part.originalUri
                    // Save the base64 url (data:image/...) to imageBase64 field for transmission
                    imageBase64 = part.url
                }
                else -> {}
            }
        }
        return ChatDataItem(
            role = message.role.name.lowercase(),
            content = builder.toString().trim(),
            localFilePath = localFilePath,
            imageBase64 = imageBase64
        )
    }

    /**
     * Loads an image from a URI, scales it down, and converts it to a Base64 string.
     * This prevents OutOfMemoryErrors and reduces payload size for the AI model.
     */
    private suspend fun createScaledBase64Image(context: Context, imageUri: Uri): String? {
        val imageLoader = ImageLoader(context)
        val request = ImageRequest.Builder(context)
            .data(imageUri)
            // Scale the image to a max size of 800x800 pixels.
            .size(Size(800, 800))
            .allowHardware(false) // Required for easy conversion to software bitmap.
            .build()

        val result = imageLoader.execute(request)

        if (result is SuccessResult) {
            val bitmap = (result.drawable as BitmapDrawable).bitmap
            ByteArrayOutputStream().use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                val byteArray = outputStream.toByteArray()
                return byteArray.toBase64()
            }
        }
        return null
    }
}
