package com.example.star.aiwork.ui.conversation.logic

import com.example.star.aiwork.domain.model.MessageStatus
import com.example.star.aiwork.domain.repository.MessageRepository
import com.example.star.aiwork.domain.repository.SessionRepository
import com.example.star.aiwork.ui.conversation.ConversationUiState
import com.example.star.aiwork.ui.conversation.Message
import com.example.star.aiwork.ui.conversation.util.ConversationLogHelper.logThrowableChain
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StreamingResponseHandler(
    private val uiState: ConversationUiState,
    private val messageRepository: MessageRepository?,
    private val sessionRepository: SessionRepository?,
    private val sessionId: String,
    private val timeNow: String,
    private val onSessionUpdated: suspend (sessionId: String) -> Unit,
    private val onMessageIdCreated: ((String) -> Unit)? = null,
    private val getCurrentMessageId: (() -> String?)? = null
) {

    /**
     * Handles the streaming response from the AI model.
     *
     * @param scope The coroutine scope to launch the collection in.
     * @param stream The flow of strings from the model.
     * @param isCancelledCheck A lambda to check if the process has been cancelled externally.
     * @param onJobCreated A callback to return the streaming job and hint job to the caller for management.
     * @return The full response string if successful, or empty string if failed/cancelled.
     */
    suspend fun handleStreaming(
        scope: CoroutineScope,
        stream: Flow<String>,
        isCancelledCheck: () -> Boolean,
        onJobCreated: (Job, Job?) -> Unit
    ): String {
        var fullResponse = ""
        var lastUpdateTime = 0L
        val UPDATE_INTERVAL_MS = 500L
        var hasShownSlowLoadingHint = false
        var hintTypingJob: Job? = null

        // Variable to capture exception to throw later
        var capturedException: Throwable? = null
        
        // 获取当前流式消息的 ID
        val messageId = getCurrentMessageId?.invoke()

        val streamingJob = scope.launch {
            try {
                stream.asCharTypingStream(charDelayMs = 30L).collect { delta ->
                    fullResponse += delta
                    
                    // 更新 Repository 中的消息（流式输出时更新缓存）
                    if (messageId != null && messageRepository != null) {
                        withContext(Dispatchers.IO) {
                            val existingMessage = messageRepository.getMessage(messageId)
                            if (existingMessage != null) {
                                // 收到第一个chunk时，将状态从 SENDING 更新为 STREAMING
                                // 后续chunk保持 STREAMING 状态
                                val newStatus = MessageStatus.STREAMING
                                
                                val updatedEntity = existingMessage.copy(
                                    content = fullResponse,
                                    status = newStatus
                                )
                                messageRepository.upsertMessage(updatedEntity)
                            }
                        }
                    }
                    
                    withContext(Dispatchers.Main) {
                        if (uiState.streamResponse && delta.isNotEmpty()) {
                            // UI 会通过 observe 自动更新，这里不需要操作 uiState
                        }

                        if (!uiState.streamResponse && delta.isNotEmpty() && !hasShownSlowLoadingHint) {
                            hasShownSlowLoadingHint = true
                            // 注意：非流式模式下的提示消息不再通过 uiState 显示
                            // 因为消息现在通过 Repository 管理
                        }
                    }

                    // 注意：消息已经通过 messageRepository.upsertMessage() 实时更新到数据库
                }
            } catch (streamError: CancellationException) {
                // 任何 CancellationException 都应该被正常处理，不应该被视为错误
                // 无论是用户主动取消还是切换对话后取消，都应该静默处理
                // 不记录为错误，也不向上抛出
                // 如果 isCancelledCheck() 返回 false，说明可能是切换对话后的取消，这是正常的
            } catch (streamError: Exception) {
                logThrowableChain("StreamingHandler", "streamError during collect", streamError)
                // Capture the exception and throw it later to let ConversationLogic handle fallback
                capturedException = streamError
            }
        }

        // Pass back jobs immediately
        onJobCreated(streamingJob, hintTypingJob)

        // Wait for completion
        try {
            streamingJob.join()
        } catch (e: CancellationException) {
            // Expected if cancelled
        }

        try {
            hintTypingJob?.join()
        } catch (e: CancellationException) {
            // Expected if cancelled
        }

        // If an exception occurred during streaming, throw it now so ConversationLogic can handle fallback
        if (capturedException != null) {
            throw capturedException!!
        }

        // 更新 Repository 中的消息状态为完成
        if (messageId != null && messageRepository != null && fullResponse.isNotBlank() && !isCancelledCheck()) {
            withContext(Dispatchers.IO) {
                val existingMessage = messageRepository.getMessage(messageId)
                if (existingMessage != null) {
                    val updatedEntity = existingMessage.copy(
                        content = fullResponse,
                        status = MessageStatus.DONE
                    )
                    messageRepository.upsertMessage(updatedEntity)
                }
            }
        }

        withContext(Dispatchers.Main) {
            uiState.isGenerating = false
        }

        if (fullResponse.isNotBlank() && !isCancelledCheck()) {
            // 消息内容已经通过 messageRepository.upsertMessage() 保存并更新了数据库
            // 更新会话的 updatedAt 时间戳
            sessionRepository?.updateSessionTimestamp(sessionId)
            onSessionUpdated(sessionId)
        }

        return if (isCancelledCheck()) "" else fullResponse
    }

    // Helper methods for char typing effect
    private fun Flow<String>.asCharTypingStream(charDelayMs: Long = 30L): Flow<String> = flow {
        collect { chunk ->
            if (chunk.isEmpty()) return@collect
            if (charDelayMs > 0) {
                for (ch in chunk) {
                    emit(ch.toString())
                    delay(charDelayMs)
                }
            } else {
                for (ch in chunk) {
                    emit(ch.toString())
                }
            }
        }
    }
}
