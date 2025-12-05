package com.example.star.aiwork.ui.conversation.util

import android.util.Log
import com.example.star.aiwork.domain.TextGenerationParams
import com.example.star.aiwork.domain.model.ChatDataItem
import com.example.star.aiwork.domain.model.Model
import com.example.star.aiwork.ui.ai.UIMessage
import com.example.star.aiwork.ui.ai.UIMessagePart

object ConversationLogHelper {
    private const val logTag = "ConversationLogic"

    /**
     * 打印发送给模型的全部内容（包括历史记录）
     */
    fun logAllMessagesToSend(
        sessionId: String,
        model: Model,
        params: TextGenerationParams,
        messagesToSend: List<UIMessage>,
        historyChat: List<ChatDataItem>,
        userMessage: ChatDataItem,
        isAutoTriggered: Boolean,
        loopCount: Int
    ) {
        Log.d(logTag, "=".repeat(100))
        Log.d(logTag, "📤 [processMessage] 准备发送消息给模型")
        Log.d(logTag, "-".repeat(100))
        Log.d(logTag, "会话ID: $sessionId")
        Log.d(logTag, "模型ID: ${model.modelId}")
        Log.d(logTag, "模型名称: ")
        Log.d(logTag, "参数: temperature=${params.temperature}, maxTokens=${params.maxTokens}")
        Log.d(logTag, "是否自动触发: $isAutoTriggered, 循环次数: $loopCount")
        Log.d(logTag, "-".repeat(100))
        Log.d(logTag, "完整消息列表 (共 ${messagesToSend.size} 条):")

        messagesToSend.forEachIndexed { index, message ->
            val roleName = message.role.name
            val contentBuilder = StringBuilder()

            message.parts.forEach { part ->
                when (part) {
                    is UIMessagePart.Text -> {
                        val text = part.text
                        if (text.length > 500) {
                            contentBuilder.append("${text.take(500)}... [已截断，总长度: ${text.length}]")
                        } else {
                            contentBuilder.append(text)
                        }
                    }
                    is UIMessagePart.Image -> {
                        val imageUrl = part.url
                        val imageInfo = if (imageUrl.length > 100) {
                            "${imageUrl.take(100)}... [已截断]"
                        } else {
                            imageUrl
                        }
                        contentBuilder.append("\n[图片: $imageInfo]")
                    }
                    else -> {
                        contentBuilder.append("\n[其他类型: ${part::class.simpleName}]")
                    }
                }
            }

            val content = contentBuilder.toString().trim()
            Log.d(logTag, "")
            Log.d(logTag, "消息 #${index + 1} [${roleName}]:")
            Log.d(logTag, content)
            if (content.isEmpty()) {
                Log.d(logTag, "[空内容]")
            }
        }

        Log.d(logTag, "-".repeat(100))
        Log.d(logTag, "历史消息 (historyChat, 共 ${historyChat.size} 条):")
        historyChat.forEachIndexed { index, item ->
            val content = if (item.content.length > 500) {
                "${item.content.take(500)}... [已截断，总长度: ${item.content.length}]"
            } else {
                item.content
            }
            Log.d(logTag, "  历史 #${index + 1} [${item.role}]: $content")
        }

        Log.d(logTag, "-".repeat(100))
        Log.d(logTag, "当前用户消息 (userMessage):")
        val userContent = if (userMessage.content.length > 500) {
            "${userMessage.content.take(500)}... [已截断，总长度: ${userMessage.content.length}]"
        } else {
            userMessage.content
        }
        Log.d(logTag, "  [${userMessage.role}]: $userContent")
        Log.d(logTag, "=".repeat(100))
    }

    /**
     * 打印异常及其 cause 链，帮助分析实际的底层错误类型（例如具体的网络异常）。
     */
    fun logThrowableChain(tag: String, prefix: String, throwable: Throwable) {
        var current: Throwable? = throwable
        var level = 0
        while (current != null && level < 6) {
            Log.e(
                tag,
                "$prefix | level=$level type=${current.javaClass.name}, message=${current.message}"
            )
            current = current.cause
            level++
        }
    }
}