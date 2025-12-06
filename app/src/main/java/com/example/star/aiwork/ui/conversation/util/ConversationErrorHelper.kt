package com.example.star.aiwork.ui.conversation.util

import com.example.star.aiwork.data.model.LlmError


object ConversationErrorHelper {



    /**
     * 此时传入的 error 必定是 LlmError，因为 Repository 层已经做过转换
     */
    fun getErrorMessage(error: Throwable): String {
        // 防御性转换，虽然理论上应该是 LlmError
        val llmError = if (error is LlmError) error else LlmError.UnknownError(cause = error)

        return when (llmError) {
            is LlmError.NetworkError -> {
                // 如果需要保留原始信息，可以使用 llmError.message
                "网络连接失败，请检查您的网络设置"
                // context.getString(R.string.error_network)
            }
            is LlmError.AuthenticationError -> "API Key 无效或过期，请检查设置"
            is LlmError.RateLimitError -> "请求太快了，请稍作休息"
            is LlmError.ServerError -> "AI 服务商暂时不可用，请稍后重试"
            is LlmError.RequestError -> "请求参数有误，请重置对话尝试"
            is LlmError.CancelledError -> "" // 取消通常不需要弹出的错误提示
            is LlmError.UnknownError -> "发生了意外错误: ${llmError.message}"
        }
    }

    /**
     * 格式化错误消息，包含错误类型和解决建议
     */
//    fun formatErrorMessage(error: Exception): String {
//        return when (error) {
//            is LlmError.NetworkError -> {
//                formatNetworkError(error)
//            }
//            is LlmError.AuthenticationError -> {
//                "API密钥无效或已过期，请检查您的API密钥"
//            }
//            is LlmError.RateLimitError -> {
//                "请求频率过高，请稍后再试"
//            }
//            is LlmError.ServerError -> {
//                "服务器错误，请稍后重试，或联系技术支持"
//            }
//            is LlmError.RequestError -> {
//                "请求参数错误：${error.message ?: "请求格式或参数有误"}\n\n请检查输入内容，或联系技术支持"
//            }
//            is LlmError.UnknownError -> {
//                "发生了意外错误，请重试操作，如问题持续请联系技术支持"
//            }
//            else -> {
//                // 处理其他类型的异常
//                if (error.message?.contains("网络", ignoreCase = true) == true ||
//                    error.message?.contains("connection", ignoreCase = true) == true
//                ) {
//                    "网络错误，请检查网络连接后重试"
//                } else {
//                    "系统错误，请重试操作，如问题持续请联系技术支持"
//                }
//            }
//        }
//    }

    /**
     * 格式化网络错误信息
     */
//    private fun formatNetworkError(error: LlmError.NetworkError): String {
//        val message = error.message ?: "网络连接失败"
//
//        return when {
//            message.contains("超时") || message.contains("timeout", ignoreCase = true) -> {
//                "网络超时，请检查网络连接，或稍后重试"
//            }
//            message.contains("连接") || message.contains("connection", ignoreCase = true) -> {
//                "网络错误，请检查网络连接，或尝试切换网络"
//            }
//            else -> {
//                "网络错误，请检查网络连接后重试"
//            }
//        }
//    }

    /**
     * 检查异常是否是取消操作相关的。
     * 检查异常本身或其根本原因是否是 CancellationException。
     */
//    fun isCancellationRelatedException(e: Exception): Boolean {
//        // 检查异常本身是否是 CancellationException
//        if (e is kotlinx.coroutines.CancellationException) {
//            return true
//        }
//
//        // 检查异常的根本原因（cause）是否是 CancellationException
//        var cause: Throwable? = e.cause
//        while (cause != null) {
//            if (cause is kotlinx.coroutines.CancellationException) {
//                return true
//            }
//            cause = cause.cause
//        }
//
//        return false
//    }
}