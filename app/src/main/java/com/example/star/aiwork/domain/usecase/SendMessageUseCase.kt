package com.example.star.aiwork.domain.usecase

import com.example.star.aiwork.data.repository.AiRepository
import com.example.star.aiwork.domain.TextGenerationParams
import com.example.star.aiwork.domain.model.ChatDataItem
import com.example.star.aiwork.domain.model.MessageEntity
import com.example.star.aiwork.domain.model.MessageMetadata
import com.example.star.aiwork.domain.model.MessageRole
import com.example.star.aiwork.domain.model.MessageStatus
import com.example.star.aiwork.domain.model.MessageType
import com.example.star.aiwork.domain.model.ProviderSetting
import com.example.star.aiwork.domain.repository.MessageRepository
import com.example.star.aiwork.domain.repository.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 负责发送消息并拉起流式生成。
 */
class SendMessageUseCase(
    private val aiRepository: AiRepository,
    private val messageRepository: MessageRepository,
    private val sessionRepository: SessionRepository,
    private val scope: CoroutineScope
) {

    data class Output(
        val stream: Flow<String>,
        val taskId: String
    )

    operator fun invoke(
        sessionId: String,
        userMessage: ChatDataItem,
        history: List<ChatDataItem>,
        providerSetting: ProviderSetting,
        params: TextGenerationParams,
        taskId: String = UUID.randomUUID().toString()
    ): Output {
        scope.launch(Dispatchers.IO) {
            // 保存用户消息
            val userEntity = chatDataItemToMessageEntity(sessionId, userMessage)
            messageRepository.upsertMessage(userEntity)
            sessionRepository.updateSessionTimestamp(sessionId)
            
            // 占位的 assistant message，等待流式结果补充
            val assistantEntity = chatDataItemToMessageEntity(
                sessionId,
                ChatDataItem(role = MessageRole.ASSISTANT.name.lowercase(), content = "")
            )
            messageRepository.upsertMessage(assistantEntity)
        }

        val stream = aiRepository.streamChat(history + userMessage, providerSetting, params, taskId)
            .onStart {
                // 可以在此通知 UI 启动中
            }

        return Output(stream = stream, taskId = taskId)
    }

    /**
     * 将 ChatDataItem 转换为 MessageEntity
     */
    private fun chatDataItemToMessageEntity(
        sessionId: String,
        chatDataItem: ChatDataItem
    ): MessageEntity {
        val role = when (chatDataItem.role.lowercase()) {
            "user" -> MessageRole.USER
            "assistant" -> MessageRole.ASSISTANT
            "system" -> MessageRole.SYSTEM
            "tool" -> MessageRole.TOOL
            else -> MessageRole.USER
        }

        val type = when {
            chatDataItem.content.contains("[image:") -> MessageType.IMAGE
            chatDataItem.content.contains("[audio:") -> MessageType.AUDIO
            chatDataItem.role.lowercase() == "system" -> MessageType.SYSTEM
            else -> MessageType.TEXT
        }

        val status = when {
            chatDataItem.content.isEmpty() && role == MessageRole.ASSISTANT -> MessageStatus.SENDING
            chatDataItem.content.isNotEmpty() && role == MessageRole.ASSISTANT -> MessageStatus.STREAMING
            else -> MessageStatus.DONE
        }

        val metadata = MessageMetadata(localFilePath = chatDataItem.localFilePath)

        return MessageEntity(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            role = role,
            type = type,
            content = chatDataItem.content,
            metadata = metadata,
            parentMessageId = null,
            createdAt = System.currentTimeMillis(),
            status = status
        )
    }
}

