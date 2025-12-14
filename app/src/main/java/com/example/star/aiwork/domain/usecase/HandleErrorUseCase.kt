package com.example.star.aiwork.domain.usecase

import android.util.Log
import com.example.star.aiwork.data.model.LlmError
import com.example.star.aiwork.domain.model.Model
import com.example.star.aiwork.domain.model.MessageStatus
import com.example.star.aiwork.domain.model.ProviderSetting
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
     * 应该使用 fallback provider 和 model
     */
    data class ShouldFallback(
        val fallbackProvider: ProviderSetting,
        val fallbackModel: Model,
        val fallbackMessage: String
    ) : ErrorHandlingResult()

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
 * 负责判断错误类型、决定是否需要 fallback、更新消息状态等。
 */
class HandleErrorUseCase(
    private val messageRepository: MessageRepository?,
    private val updateMessageUseCase: UpdateMessageUseCase?
) {
    /**
     * 处理错误
     *
     * @param error 发生的异常
     * @param currentProviderSetting 当前使用的 provider setting（可能为 null）
     * @param currentModel 当前使用的 model（可能为 null）
     * @param isRetry 是否是重试（如果是重试，则不进行 fallback）
     * @param isFallbackEnabled 是否启用了 fallback
     * @param fallbackProviderId fallback provider ID（可能为 null）
     * @param fallbackModelId fallback model ID（可能为 null）
     * @param allProviderSettings 所有可用的 provider settings（用于查找 fallback provider）
     * @param currentMessageId 当前正在处理的消息 ID（可能为 null）
     * @return 错误处理结果
     */
    suspend operator fun invoke(
        error: Exception,
        currentProviderSetting: ProviderSetting?,
        currentModel: Model?,
        isRetry: Boolean,
        isFallbackEnabled: Boolean,
        fallbackProviderId: String?,
        fallbackModelId: String?,
        allProviderSettings: List<ProviderSetting>,
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

        // 2. 检查是否需要 fallback
        if (!isRetry && // 仅在尚未重试过的情况下尝试兜底
            isFallbackEnabled &&
            fallbackProviderId != null &&
            fallbackModelId != null
        ) {
            Log.d("HandleErrorUseCase", "🔍 Fallback config found: providerId=$fallbackProviderId, modelId=$fallbackModelId")

            val fallbackProvider = allProviderSettings.find { it.id == fallbackProviderId }
            val fallbackModel = fallbackProvider?.models?.find { it.id == fallbackModelId }
                ?: fallbackProvider?.models?.find { it.modelId == fallbackModelId }

            // 避免在当前已经是兜底配置的情况下陷入死循环
            // 比较 provider ID 和 model ID（同时检查 id 和 modelId）
            val isSameAsCurrent = currentProviderSetting?.id == fallbackProviderId &&
                (currentModel?.id == fallbackModel?.id || 
                 (currentModel?.modelId.isNullOrBlank().not() && 
                  currentModel?.modelId == fallbackModel?.modelId))

            Log.d("HandleErrorUseCase", "🔍 Fallback candidates: provider=${fallbackProvider?.name}, model=${fallbackModel?.displayName}")
            Log.d("HandleErrorUseCase", "🔍 isSameAsCurrent=$isSameAsCurrent (currentProvider=${currentProviderSetting?.id}, currentModel=${currentModel?.id})")

            if (fallbackProvider != null && fallbackModel != null && !isSameAsCurrent) {
                Log.i("HandleErrorUseCase", "✅ Triggering configured fallback to ${fallbackProvider.name}...")
                
                // 更新当前消息状态（如果存在）
                if (currentMessageId != null) {
                    updateMessageIfExists(currentMessageId, updateSessionTimestamp = false)
                }
                
                val fallbackMessage = "Request failed (${error.message}). Fallback to ${fallbackProvider.name} (${fallbackModel.displayName})..."
                return@withContext ErrorHandlingResult.ShouldFallback(
                    fallbackProvider = fallbackProvider,
                    fallbackModel = fallbackModel,
                    fallbackMessage = fallbackMessage
                )
            } else {
                Log.w("HandleErrorUseCase", "⚠️ Fallback skipped: Provider/Model not found or same as current.")
            }
        } else {
            Log.d("HandleErrorUseCase", "Skipping configured fallback (retry or disabled or missing config).")
        }

        // 3. 没有 fallback，需要显示错误
        Log.e("HandleErrorUseCase", "❌ No fallback triggered. Displaying error message.")
        
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

