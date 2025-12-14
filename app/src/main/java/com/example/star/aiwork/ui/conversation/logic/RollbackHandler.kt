package com.example.star.aiwork.ui.conversation.logic

import android.util.Log
import com.example.star.aiwork.data.model.LlmError
import com.example.star.aiwork.domain.TextGenerationParams
import com.example.star.aiwork.domain.model.ChatDataItem
import com.example.star.aiwork.domain.model.MessageRole
import com.example.star.aiwork.domain.model.Model
import com.example.star.aiwork.domain.model.ProviderSetting
import com.example.star.aiwork.domain.usecase.RollbackMessageUseCase
import com.example.star.aiwork.domain.usecase.SaveMessageUseCase
import com.example.star.aiwork.domain.repository.MessageRepository
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
    private val saveMessageUseCase: SaveMessageUseCase,
    private val messageRepository: MessageRepository?,
    private val streamingResponseHandler: StreamingResponseHandler,
    private val sessionId: String,
    private val authorMe: String,
    private val timeNow: String,
    private val onMessageIdCreated: ((String) -> Unit)? = null
) {

    suspend fun rollbackAndRegenerate(
        providerSetting: ProviderSetting?,
        model: Model?,
        scope: CoroutineScope,
        isCancelledCheck: () -> Boolean,
        onJobCreated: (Job, Job?) -> Unit,
        onTaskIdUpdated: suspend (String?) -> Unit
    ) {
        if (providerSetting == null || model == null) {
            // 错误消息不保存到数据库，只添加到临时错误消息列表
            withContext(Dispatchers.Main) {
                uiState.temporaryErrorMessages = listOf(
                    Message("System", "No AI Provider configured.", timeNow)
                )
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
                        val messageId = saveMessageUseCase.saveFromUIMessage(
                            sessionId = sessionId,
                            author = "AI",
                            content = "",
                            isLoading = true,
                            authorMe = authorMe
                        )
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
        } catch (e: Exception) {
            if (e is CancellationException || e is LlmError.CancelledError) {
                withContext(Dispatchers.Main) {
                    uiState.isGenerating = false
                }
                return
            }
            
            // 错误消息不保存到数据库，只添加到临时错误消息列表
            val errorMessage = getErrorMessage(e)
            withContext(Dispatchers.Main) {
                uiState.temporaryErrorMessages = listOf(
                    Message("System", errorMessage, timeNow)
                )
                uiState.isGenerating = false
            }
            e.printStackTrace()
        }
    }
}
