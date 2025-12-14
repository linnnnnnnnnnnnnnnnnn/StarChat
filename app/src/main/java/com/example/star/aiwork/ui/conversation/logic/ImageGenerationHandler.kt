package com.example.star.aiwork.ui.conversation.logic

import com.example.star.aiwork.domain.ImageGenerationParams
import com.example.star.aiwork.domain.model.Model
import com.example.star.aiwork.domain.model.ProviderSetting
import com.example.star.aiwork.domain.usecase.ImageGenerationUseCase
import com.example.star.aiwork.domain.usecase.SaveMessageUseCase
import com.example.star.aiwork.domain.usecase.UpdateMessageUseCase
import com.example.star.aiwork.domain.model.MessageMetadata
import com.example.star.aiwork.domain.model.MessageStatus
import com.example.star.aiwork.ui.conversation.ConversationUiState
import com.example.star.aiwork.ui.conversation.Message
import com.example.star.aiwork.ui.conversation.util.ConversationErrorHelper.getErrorMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ImageGenerationHandler(
    private val uiState: ConversationUiState,
    private val imageGenerationUseCase: ImageGenerationUseCase,
    private val saveMessageUseCase: SaveMessageUseCase,
    private val updateMessageUseCase: UpdateMessageUseCase,
    private val sessionId: String,
    private val timeNow: String,
    private val onSessionUpdated: suspend (sessionId: String) -> Unit
) {
    suspend fun generateImage(
        providerSetting: ProviderSetting,
        model: Model,
        prompt: String
    ) {
        val messageId = withContext(Dispatchers.IO) {
            saveMessageUseCase.saveFromUIMessage(
                sessionId = sessionId,
                author = "AI",
                content = "",
                isLoading = true
            )
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
                        // 更新消息内容、状态和图片URL metadata
                        updateMessageUseCase.updateWithMetadata(
                            messageId = messageId,
                            content = "Generated Image:",
                            status = MessageStatus.DONE,
                            metadata = MessageMetadata(remoteUrl = imageUrl)
                        )
                        // 创建图片消息
                        saveMessageUseCase.saveFromUIMessage(
                            sessionId = sessionId,
                            author = "AI",
                            content = "",
                            imageUrl = imageUrl
                        )
                    } else {
                        updateMessageUseCase(
                            messageId = messageId,
                            content = "Failed to generate image: Empty result.",
                            status = MessageStatus.DONE
                        )
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
                    updateMessageUseCase.deleteIfEmpty(messageId)
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
