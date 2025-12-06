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
import com.example.star.aiwork.domain.usecase.embedding.ComputeEmbeddingUseCase
import com.example.star.aiwork.domain.usecase.embedding.SearchEmbeddingUseCase
import com.example.star.aiwork.infra.util.toBase64
import com.example.star.aiwork.ui.ai.UIMessage
import com.example.star.aiwork.ui.ai.UIMessagePart
import com.example.star.aiwork.ui.conversation.ConversationUiState
import java.io.ByteArrayOutputStream

object MessageConstructionHelper {

    suspend fun constructMessagesToSend(
        uiState: ConversationUiState,
        authorMe: String,
        inputContent: String,
        isAutoTriggered: Boolean,
        activeAgent: Agent?,
        retrieveKnowledge: suspend (String) -> String,
        context: Context,
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
            computeEmbeddingUseCase,
            searchEmbeddingUseCase,
            topK
        )
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
        computeEmbeddingUseCase: ComputeEmbeddingUseCase? = null,
        searchEmbeddingUseCase: SearchEmbeddingUseCase? = null,
        topK: Int = 3
    ): List<UIMessage> {

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

        // 获取历史消息
        val contextMessages = uiState.messages.asReversed()
            .filter { it.author != "System" }
            .map { msg ->
                val role = if (msg.author == authorMe) MessageRole.USER else MessageRole.ASSISTANT
                val parts = mutableListOf<UIMessagePart>()
                if (msg.content.isNotEmpty()) {
                    parts.add(UIMessagePart.Text(msg.content))
                }
                UIMessage(role = role, parts = parts)
            }.takeLast(10).toMutableList()

        // 在向模型发送信息之前，计算用户输入的 embedding 并搜索相关句子
        val relatedSentences = mutableListOf<String>()
        if (!isAutoTriggered && computeEmbeddingUseCase != null && searchEmbeddingUseCase != null && inputContent.isNotBlank()) {
            try {
                // 计算用户输入的 embedding
                val queryEmbedding = computeEmbeddingUseCase(inputContent)
                if (queryEmbedding != null) {
                    // 搜索 top-k 相关的句子
                    val searchResults = searchEmbeddingUseCase(queryEmbedding, topK)
                    // 提取文本（注意是 text 而不是 floatarray）
                    relatedSentences.addAll(searchResults.map { it.first.text })
                    Log.d("MessageConstruction", "找到 ${relatedSentences.size} 条相关句子")
                }
            } catch (e: Exception) {
                Log.e("MessageConstruction", "计算 embedding 或搜索相关句子失败", e)
            }
        }

        // 将搜索到的相关句子添加到历史记录后面（作为系统消息或用户消息的上下文）
        if (relatedSentences.isNotEmpty()) {
            val relatedContextText = relatedSentences.joinToString("\n") { "- $it" }
            val relatedContextMessage = UIMessage(
                role = MessageRole.SYSTEM,
                parts = listOf(UIMessagePart.Text("相关上下文信息：\n$relatedContextText"))
            )
            // 在历史消息后面添加相关句子
            contextMessages.add(relatedContextMessage)
        }

        val messagesToSend = mutableListOf<UIMessage>()

        activeAgent?.systemPrompt?.takeIf { it.isNotEmpty() }?.let {
            messagesToSend.add(UIMessage(role = MessageRole.SYSTEM, parts = listOf(UIMessagePart.Text(it))))
        }

        activeAgent?.presetMessages?.forEach { preset ->
            messagesToSend.add(UIMessage(role = preset.role, parts = listOf(UIMessagePart.Text(preset.content))))
        }

        messagesToSend.addAll(contextMessages)

        if (messagesToSend.isNotEmpty() && messagesToSend.last().role == MessageRole.USER) {
            messagesToSend.removeAt(messagesToSend.lastIndex)
        }

        val currentMessageParts = mutableListOf<UIMessagePart>()
        currentMessageParts.add(UIMessagePart.Text(finalUserContent))

        if (!isAutoTriggered) {
            val lastUserMsg = uiState.messages.firstOrNull { it.author == authorMe && it.author != "System" }
            if (lastUserMsg?.imageUrl != null) {
                try {
                    val imageUri = Uri.parse(lastUserMsg.imageUrl)

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
                    Log.e("MessageConstruction", "Failed to process and scale image URI: ${lastUserMsg.imageUrl}", t)
                }
            }
        }

        messagesToSend.add(UIMessage(
            role = MessageRole.USER,
            parts = currentMessageParts
        ))
        
        return messagesToSend
    }

    /**
     * Converts a UIMessage to a ChatDataItem for database persistence.
     */
    fun toChatDataItem(message: UIMessage): ChatDataItem {
        val builder = StringBuilder()
        var localFilePath: String? = null

        message.parts.forEach { part ->
            when (part) {
                is UIMessagePart.Text -> builder.append(part.text)
                is UIMessagePart.Image -> {
                    // IMPORTANT: Only append a placeholder for the content.
                    // The actual image path is stored in localFilePath.
                    if (builder.isNotEmpty()) builder.append(" ")
                    builder.append("[image]")
                    localFilePath = part.originalUri
                }
                else -> {}
            }
        }
        return ChatDataItem(
            role = message.role.name.lowercase(),
            content = builder.toString().trim(),
            localFilePath = localFilePath
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
