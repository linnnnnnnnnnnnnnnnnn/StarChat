package com.example.star.aiwork.ui.conversation.logic

import com.example.star.aiwork.domain.ImageGenerationParams
import com.example.star.aiwork.domain.model.ChatDataItem
import com.example.star.aiwork.domain.model.MessageRole
import com.example.star.aiwork.domain.model.Model
import com.example.star.aiwork.domain.model.ProviderSetting
import com.example.star.aiwork.domain.usecase.ImageGenerationUseCase
import com.example.star.aiwork.domain.repository.MessageRepository
import com.example.star.aiwork.domain.repository.SessionRepository
import com.example.star.aiwork.domain.model.MessageEntity
import com.example.star.aiwork.domain.model.MessageType
import com.example.star.aiwork.domain.model.MessageStatus
import com.example.star.aiwork.domain.model.MessageMetadata
import com.example.star.aiwork.ui.conversation.ConversationUiState
import com.example.star.aiwork.ui.conversation.Message
import com.example.star.aiwork.ui.conversation.util.ConversationErrorHelper.getErrorMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ImageGenerationHandler(
    private val uiState: ConversationUiState,
    private val imageGenerationUseCase: ImageGenerationUseCase,
    private val messageRepository: MessageRepository?,
    private val sessionRepository: SessionRepository?,
    private val sessionId: String,
    private val timeNow: String,
    private val onSessionUpdated: suspend (sessionId: String) -> Unit
) {
    
    private suspend fun saveMessageToRepository(message: Message): String {
        val messageId = java.util.UUID.randomUUID().toString()
        val role = when (message.author) {
            "AI", "assistant", "model" -> MessageRole.ASSISTANT
            "System", "system" -> MessageRole.SYSTEM
            else -> MessageRole.USER
        }
        val entity = MessageEntity(
            id = messageId,
            sessionId = sessionId,
            role = role,
            type = if (message.imageUrl != null) MessageType.IMAGE else MessageType.TEXT,
            content = message.content,
            metadata = MessageMetadata(remoteUrl = message.imageUrl),
            createdAt = System.currentTimeMillis(),
            status = if (message.isLoading) MessageStatus.STREAMING else MessageStatus.DONE
        )
        messageRepository?.upsertMessage(entity)
        return messageId
    }
    
    private suspend fun updateMessageInRepository(messageId: String, content: String, imageUrl: String? = null) {
        val existingMessage = messageRepository?.getMessage(messageId)
        if (existingMessage != null) {
            val updatedEntity = existingMessage.copy(
                content = content,
                metadata = existingMessage.metadata.copy(remoteUrl = imageUrl ?: existingMessage.metadata.remoteUrl),
                status = MessageStatus.DONE
            )
            messageRepository.upsertMessage(updatedEntity)
        }
    }
    suspend fun generateImage(
        providerSetting: ProviderSetting,
        model: Model,
        prompt: String
    ) {
        val messageId = withContext(Dispatchers.IO) {
            saveMessageToRepository(Message("AI", "", timeNow, isLoading = true))
        }

        val result = imageGenerationUseCase(
            providerSetting = providerSetting,
            params = ImageGenerationParams(
                model = model,
                prompt = prompt,
                numOfImages = 1
            )
        )

        result.fold(
            onSuccess = { imageResult ->
                withContext(Dispatchers.IO) {
                    val firstImage = imageResult.items.firstOrNull()
                    if (firstImage != null && firstImage.data != null) {
                        val imageUrl = if (firstImage.data.startsWith("http")) {
                            firstImage.data
                        } else {
                            "data:${firstImage.mimeType};base64,${firstImage.data}"
                        }
                        updateMessageInRepository(messageId, "Generated Image:", imageUrl)
                        // 创建图片消息
                        saveMessageToRepository(
                            Message(
                                author = "AI",
                                content = "",
                                timestamp = timeNow,
                                imageUrl = imageUrl
                            )
                        )
                        // 消息已经通过 updateMessageInRepository() 和 saveMessageToRepository() 保存到数据库
                        // 更新会话的 updatedAt 时间戳
                        sessionRepository?.updateSessionTimestamp(sessionId)
                    } else {
                        updateMessageInRepository(messageId, "Failed to generate image: Empty result.")
                    }
                    onSessionUpdated(sessionId)
                }
                withContext(Dispatchers.Main) {
                    uiState.isGenerating = false
                }
            },
            onFailure = { error ->
                withContext(Dispatchers.IO) {
                    // 删除空消息
                    messageRepository?.deleteMessage(messageId)
                }
                // 错误消息不保存到数据库，只添加到临时错误消息列表
                val errorMessage = getErrorMessage(error)
                withContext(Dispatchers.Main) {
                    uiState.temporaryErrorMessages = listOf(
                        Message("System", errorMessage, timeNow)
                    )
                    uiState.isGenerating = false
                }
                error.printStackTrace()
            }
        )
    }
}
