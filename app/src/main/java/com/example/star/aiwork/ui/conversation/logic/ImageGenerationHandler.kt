package com.example.star.aiwork.ui.conversation.logic

import com.example.star.aiwork.domain.ImageGenerationParams
import com.example.star.aiwork.domain.model.ChatDataItem
import com.example.star.aiwork.domain.model.MessageRole
import com.example.star.aiwork.domain.model.Model
import com.example.star.aiwork.domain.model.ProviderSetting
import com.example.star.aiwork.domain.usecase.ImageGenerationUseCase
import com.example.star.aiwork.domain.usecase.MessagePersistenceGateway
import com.example.star.aiwork.ui.conversation.ConversationUiState
import com.example.star.aiwork.ui.conversation.Message
import com.example.star.aiwork.ui.conversation.util.ConversationErrorHelper.formatErrorMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ImageGenerationHandler(
    private val uiState: ConversationUiState,
    private val imageGenerationUseCase: ImageGenerationUseCase,
    private val persistenceGateway: MessagePersistenceGateway?,
    private val sessionId: String,
    private val timeNow: String,
    private val onSessionUpdated: suspend (sessionId: String) -> Unit
) {
    suspend fun generateImage(
        providerSetting: ProviderSetting,
        model: Model,
        prompt: String
    ) {
        withContext(Dispatchers.Main) {
            uiState.addMessage(Message("AI", "", timeNow, isLoading = true))
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
                withContext(Dispatchers.Main) {
                    uiState.updateLastMessageLoadingState(false)
                    val firstImage = imageResult.items.firstOrNull()
                    if (firstImage != null && firstImage.data != null) {
                        val imageUrl = if (firstImage.data.startsWith("http")) {
                            firstImage.data
                        } else {
                            "data:${firstImage.mimeType};base64,${firstImage.data}"
                        }
                        uiState.appendToLastMessage("Generated Image:")
                        uiState.addMessage(
                            Message(
                                author = "AI",
                                content = "",
                                timestamp = timeNow,
                                imageUrl = imageUrl
                            )
                        )
                        // Persistence
                        persistenceGateway?.replaceLastAssistantMessage(
                            sessionId,
                            ChatDataItem(
                                role = MessageRole.ASSISTANT.name.lowercase(),
                                content = "Generated Image:\n[image:$imageUrl]"
                            )
                        )
                    } else {
                        uiState.appendToLastMessage("Failed to generate image: Empty result.")
                    }
                    uiState.isGenerating = false
                    onSessionUpdated(sessionId)
                }
            },
            onFailure = { error ->
                withContext(Dispatchers.Main) {
                    uiState.updateLastMessageLoadingState(false)
                    uiState.isGenerating = false
                    // Remove empty message if needed
                    if (uiState.messages.isNotEmpty() && uiState.messages[0].content.isBlank()) {
                        uiState.removeFirstMessage()
                    }
                    val errorMessage = formatErrorMessage(error as? Exception ?: Exception(error.message, error))
                    uiState.addMessage(Message("System", errorMessage, timeNow))
                }
                error.printStackTrace()
            }
        )
    }
}
