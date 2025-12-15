package com.example.star.aiwork.ui.conversation.logic

import com.example.star.aiwork.domain.model.ChatDataItem
import com.example.star.aiwork.ui.ai.UIMessage
import com.example.star.aiwork.ui.ai.UIMessagePart

/**
 * Helper for UI <-> Domain message conversion.
 *
 * 之前这里包含了较多与 Domain 相关的业务逻辑（历史消息、长期记忆、向量搜索等），
 * 这些逻辑已经下沉到 Domain 层的 UseCase。
 *
 * 当前 Helper 仅保留 UI 层与 Domain 层模型之间的简单转换，避免 UI 直接依赖 Domain 细节。
 */
object MessageConstructionHelper {

    /**
     * Converts a UIMessage to a ChatDataItem for database persistence.
     */
    fun toChatDataItem(message: UIMessage): ChatDataItem {
        val builder = StringBuilder()

        message.parts.forEach { part ->
            when (part) {
                is UIMessagePart.Text -> builder.append(part.text)
                else -> {}
            }
        }
        return ChatDataItem(
            role = message.role.name.lowercase(),
            content = builder.toString().trim(),
            localFilePath = null,
            imageBase64 = null
        )
    }
}

