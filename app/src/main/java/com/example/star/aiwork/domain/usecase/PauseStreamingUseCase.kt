package com.example.star.aiwork.domain.usecase

import com.example.star.aiwork.data.repository.AiRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 停止当前流式会话的用例。
 */
class PauseStreamingUseCase(
    private val aiRepository: AiRepository
) {
    suspend operator fun invoke(taskId: String): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext runCatching {
            try {
                aiRepository.cancelStreaming(taskId)
            } catch (e: Exception) {
                // 取消操作应该静默处理，即使失败也不应该抛出异常
                // 记录日志但不抛出异常
                android.util.Log.d("PauseStreamingUseCase", "Cancel streaming failed for taskId: $taskId", e)
                // 即使发生异常，也返回成功，因为取消操作本身不应该被视为错误
            }
            Unit // 显式返回 Unit 以确保类型匹配
        }
    }
}

