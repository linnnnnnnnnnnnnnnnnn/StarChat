package com.example.star.aiwork.ui.conversation.logic

import android.util.Log
import com.example.star.aiwork.data.model.LlmError
import com.example.star.aiwork.domain.TextGenerationParams
import com.example.star.aiwork.domain.model.ChatDataItem
import com.example.star.aiwork.domain.model.MessageRole
import com.example.star.aiwork.domain.model.Model
import com.example.star.aiwork.domain.model.ProviderSetting
import com.example.star.aiwork.domain.usecase.RollbackMessageUseCase
import com.example.star.aiwork.domain.repository.MessageRepository
import com.example.star.aiwork.domain.model.MessageEntity
import com.example.star.aiwork.domain.model.MessageType
import com.example.star.aiwork.domain.model.MessageStatus
import com.example.star.aiwork.domain.model.MessageMetadata
import com.example.star.aiwork.ui.conversation.ConversationUiState
import com.example.star.aiwork.ui.conversation.Message
import kotlinx.coroutines.flow.first
import com.example.star.aiwork.ui.conversation.util.ConversationErrorHelper.getErrorMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext

class RollbackHandler(
    private val uiState: ConversationUiState,
    private val rollbackMessageUseCase: RollbackMessageUseCase,
    private val messageRepository: MessageRepository?,
    private val streamingResponseHandler: StreamingResponseHandler,
    private val sessionId: String,
    private val authorMe: String,
    private val timeNow: String,
    private val onMessageIdCreated: ((String) -> Unit)? = null
) {
    
    private suspend fun saveMessageToRepository(message: Message): String {
        val messageId = java.util.UUID.randomUUID().toString()
        val role = when (message.author) {
            authorMe -> MessageRole.USER
            "AI", "assistant", "model" -> MessageRole.ASSISTANT
            "System", "system" -> MessageRole.SYSTEM
            else -> MessageRole.USER
        }
        val entity = MessageEntity(
            id = messageId,
            sessionId = sessionId,
            role = role,
            type = if (role == MessageRole.SYSTEM) MessageType.SYSTEM else MessageType.TEXT,
            content = message.content,
            createdAt = System.currentTimeMillis(),
            status = if (message.isLoading) MessageStatus.STREAMING else MessageStatus.DONE
        )
        messageRepository?.upsertMessage(entity)
        return messageId
    }

    suspend fun rollbackAndRegenerate(
        providerSetting: ProviderSetting?,
        model: Model?,
        scope: CoroutineScope,
        isCancelledCheck: () -> Boolean,
        onJobCreated: (Job, Job?) -> Unit,
        onTaskIdUpdated: suspend (String?) -> Unit
    ) {
        if (providerSetting == null || model == null) {
            withContext(Dispatchers.IO) {
                saveMessageToRepository(Message("System", "No AI Provider configured.", timeNow))
            }
            return
        }

        // 从 Repository 获取最后一条用户消息
        val lastUserMessage = withContext(Dispatchers.IO) {
            messageRepository?.observeMessages(sessionId)?.first()
                ?.findLast { it.role == MessageRole.USER }
        }
        if (lastUserMessage == null) return

        try {
            withContext(Dispatchers.Main) {
                uiState.isGenerating = true
            }

            // Prepare history for rollback - 从 Repository 获取消息
            val allMessages = withContext(Dispatchers.IO) {
                messageRepository?.observeMessages(sessionId)?.first()
                    ?.filter { it.role != MessageRole.SYSTEM }
                    ?.reversed() ?: emptyList()
            }
            val lastAssistantIndex = allMessages.indexOfLast { it.role != MessageRole.USER }
            val historyMessages = if (lastAssistantIndex >= 0) {
                allMessages.take(lastAssistantIndex) + allMessages.drop(lastAssistantIndex + 1)
            } else {
                allMessages
            }
            
            val contextMessages = historyMessages.map { entity ->
                ChatDataItem(
                    role = entity.role.name.lowercase(),
                    content = entity.content
                )
            }.toMutableList()

            if (historyMessages.none { it.role == MessageRole.USER }) {
                withContext(Dispatchers.Main) { uiState.isGenerating = false }
                return
            }

            val params = TextGenerationParams(
                model = model,
                temperature = uiState.temperature,
                maxTokens = uiState.maxTokens
            )

            Log.d("RollbackHandler", "🔄 [rollbackAndRegenerate] Rolling back...")
            
            val rollbackResult = rollbackMessageUseCase(
                sessionId = sessionId,
                history = contextMessages,
                providerSetting = providerSetting,
                params = params
            )

            rollbackResult.fold(
                onSuccess = { flowResult ->
                    // 删除最后一条助手消息
                    withContext(Dispatchers.IO) {
                        val lastAssistantMessage = messageRepository?.observeMessages(sessionId)?.first()
                            ?.findLast { it.role == MessageRole.ASSISTANT }
                        lastAssistantMessage?.let {
                            messageRepository?.deleteMessage(it.id)
                        }
                        // 创建新的空消息用于流式输出
                        val messageId = saveMessageToRepository(Message("AI", "", timeNow, isLoading = true))
                        onMessageIdCreated?.invoke(messageId)
                    }

                    onTaskIdUpdated(flowResult.taskId)
                    // isCancelled = false (handled by caller or implicit)

                    streamingResponseHandler.handleStreaming(
                        scope = scope,
                        stream = flowResult.stream,
                        isCancelledCheck = isCancelledCheck,
                        onJobCreated = onJobCreated
                    )
                },
                onFailure = { error ->
                    withContext(Dispatchers.IO) {
                        val errorMessage = getErrorMessage(error)
                        saveMessageToRepository(Message("System", errorMessage, timeNow))
                    }
                    withContext(Dispatchers.Main) {
                        uiState.isGenerating = false
                    }
                    error.printStackTrace()
                }
            )
        } catch (e: Exception) {
            if (e is CancellationException || e is LlmError.CancelledError) {
                withContext(Dispatchers.Main) {
                    uiState.isGenerating = false
                }
                return
            }
            
            withContext(Dispatchers.IO) {
                val errorMessage = getErrorMessage(e)
                saveMessageToRepository(Message("System", errorMessage, timeNow))
            }
            withContext(Dispatchers.Main) {
                uiState.isGenerating = false
            }
            e.printStackTrace()
        }
    }
}
