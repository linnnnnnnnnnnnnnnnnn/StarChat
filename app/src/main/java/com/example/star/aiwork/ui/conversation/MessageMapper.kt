package com.example.star.aiwork.ui.conversation

import com.example.star.aiwork.domain.model.MessageEntity
import com.example.star.aiwork.domain.model.MessageRole
import com.example.star.aiwork.domain.model.MessageStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MessageMapper(private val userAuthor: String) {

    private val timeFormatter = SimpleDateFormat("h:mm a", Locale.getDefault())

    fun toUiModel(entity: MessageEntity): Message {
        val author = when (entity.role) {
            MessageRole.USER -> userAuthor
            MessageRole.ASSISTANT -> "model"
            else -> "System"
        }
        return Message(
            author = author,
            content = entity.content,
            timestamp = timeFormatter.format(Date(entity.createdAt)),
            imageUrl = entity.metadata.remoteUrl,
            isLoading = entity.status == MessageStatus.SENDING
        )
    }
}
