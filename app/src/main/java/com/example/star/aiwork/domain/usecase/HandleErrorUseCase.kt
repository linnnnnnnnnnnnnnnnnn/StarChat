package com.example.star.aiwork.domain.usecase

import android.util.Log
import com.example.star.aiwork.data.model.LlmError
import com.example.star.aiwork.domain.model.MessageStatus
import com.example.star.aiwork.domain.repository.MessageRepository
import com.example.star.aiwork.ui.conversation.util.ConversationErrorHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 错误处理的结果类型
 */
sealed class ErrorHandlingResult {
    /**
     * 取消异常，应该忽略
     */
    data object Cancelled : ErrorHandlingResult()

    /**
     * 应该显示错误消息
     */
    data class ShouldDisplayError(
        val errorMessage: String,
        val shouldDeleteMessage: Boolean = false
    ) : ErrorHandlingResult()
}

/**
 * 处理消息发送错误的用例。
 * 负责判断错误类型、更新消息状态等。
 */
class HandleErrorUseCase(
    private val messageRepository: MessageRepository?,
    private val updateMessageUseCase: UpdateMessageUseCase?
) {
    /**
     * 处理错误
     *
     * @param error 发生的异常
     * @param currentMessageId 当前正在处理的消息 ID（可能为 null）
     * @return 错误处理结果
     */
    suspend operator fun invoke(
        error: Exception,
        currentMessageId: String?
    ): ErrorHandlingResult = withContext(Dispatchers.IO) {
        Log.e("HandleErrorUseCase", "❌ handleError triggered: ${error.javaClass.simpleName} - ${error.message}", error)

        // 1. 检查是否是取消异常
        if (error is CancellationException || error is LlmError.CancelledError) {
            Log.d("HandleErrorUseCase", "⚠️ Error is cancellation related, ignoring.")
            // 更新消息状态为完成（如果存在流式消息）
            if (currentMessageId != null) {
                updateMessageIfExists(currentMessageId, updateSessionTimestamp = false)
            }
            return@withContext ErrorHandlingResult.Cancelled
        }

        // 2. 需要显示错误
        Log.e("HandleErrorUseCase", "❌ Displaying error message.")
        
        // 检查是否需要删除空消息
        var shouldDeleteMessage = false
        if (currentMessageId != null) {
            val existingMessage = messageRepository?.getMessage(currentMessageId)
            if (existingMessage != null) {
                if (existingMessage.content.isBlank()) {
                    // 如果是重试产生的空消息，删除它
                    shouldDeleteMessage = true
                } else {
                    // 更新消息状态为完成
                    updateMessageIfExists(currentMessageId, updateSessionTimestamp = false)
                }
            }
        }
        
        val errorMessage = ConversationErrorHelper.getErrorMessage(error)
        return@withContext ErrorHandlingResult.ShouldDisplayError(
            errorMessage = errorMessage,
            shouldDeleteMessage = shouldDeleteMessage
        )
    }

    /**
     * 更新消息状态（如果消息存在）
     */
    private suspend fun updateMessageIfExists(
        messageId: String,
        updateSessionTimestamp: Boolean
    ) {
        val existingMessage = messageRepository?.getMessage(messageId)
        if (existingMessage != null && existingMessage.content.isNotEmpty()) {
            updateMessageUseCase?.invoke(
                messageId = messageId,
                content = existingMessage.content,
                status = MessageStatus.DONE,
                updateSessionTimestamp = updateSessionTimestamp
            )
        }
    }
}

