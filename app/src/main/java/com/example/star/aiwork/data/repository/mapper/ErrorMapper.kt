package com.example.star.aiwork.data.repository.mapper

import com.example.star.aiwork.data.model.LlmError
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.CancellationException

/**
 * 将任意 Throwable 转换为领域层 LlmError
 */
fun Throwable.toLlmError(): LlmError {
    // 如果已经是 LlmError，直接返回
    if (this is LlmError) return this

    // 如果是协程取消，转换为 CancelledError
    if (this is CancellationException) {
        return LlmError.CancelledError("任务已取消", this)
    }

    return when (this) {
        is SocketTimeoutException, is TimeoutException -> {
            LlmError.NetworkError("请求超时，请检查网络环境", this)
        }
        is UnknownHostException -> {
            LlmError.NetworkError("无法解析主机，请检查网络连接", this)
        }
        is IOException -> {
            // 这里可以处理更多细分的 IO 异常
            if (message?.contains("Canceled") == true) {
                LlmError.CancelledError("请求被中断", this)
            } else {
                LlmError.NetworkError("网络连接异常", this)
            }
        }
        else -> {
            // 如果你有自定义的 HttpException (例如来自 Retrofit 或自己封装的)，在这里处理状态码
            // 假设你有一个包含 code 的 HttpException
            /* is HttpException -> when(this.code()) {
                401 -> LlmError.AuthenticationError("认证失败", this)
                429 -> LlmError.RateLimitError("请求过于频繁", this)
                in 500..599 -> LlmError.ServerError("服务器内部错误", this)
                else -> LlmError.NetworkError("HTTP ${this.code()}", this)
            }
            */
            LlmError.UnknownError(this.message ?: "未知错误", this)
        }
    }
}