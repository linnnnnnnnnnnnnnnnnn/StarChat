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
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        val taskId: String,
        val assistantMessageId: String  // 返回创建的 ASSISTANT 消息ID
    )

    operator fun invoke(
        sessionId: String,
        userMessage: ChatDataItem,
        history: List<ChatDataItem>,
        providerSetting: ProviderSetting,
        params: TextGenerationParams,
        taskId: String = UUID.randomUUID().toString()
    ): Output {
        // 创建用户消息
        val userEntity = chatDataItemToMessageEntity(sessionId, userMessage)
        val userTimestamp = userEntity.createdAt
        
        // 占位的 assistant message，等待流式结果补充
        // 确保 ASSISTANT 消息的 createdAt 比 USER 消息晚至少 1 毫秒，保证顺序正确
        val assistantEntity = chatDataItemToMessageEntity(
            sessionId,
            ChatDataItem(role = MessageRole.ASSISTANT.name.lowercase(), content = "")
        ).copy(
            createdAt = userTimestamp + 1
        )
        
        // 异步保存消息到数据库，不阻塞调用线程
        scope.launch(Dispatchers.IO) {
            messageRepository.upsertMessage(userEntity)
            messageRepository.upsertMessage(assistantEntity)
            sessionRepository.updateSessionTimestamp(sessionId)
        }

        // 包装 stream，在收集时自动更新消息状态和内容
        val assistantMessageId = assistantEntity.id
        // 使用对象来保存状态，确保在 Flow 操作符中正确共享
        val state = object {
            var accumulatedContent = ""
            var isFirstChunk = true
        }
        
        val stream = aiRepository.streamChat(history + userMessage, providerSetting, params, taskId)
            .onStart {
                // 可以在此通知 UI 启动中
            }
            .onEach { chunk ->
                state.accumulatedContent += chunk
                
                // 在 domain 层更新消息状态和内容
                withContext(Dispatchers.IO) {
                    val existingMessage = messageRepository.getMessage(assistantMessageId)
                    if (existingMessage != null) {
                        // 收到第一个chunk时，将状态从 SENDING 更新为 STREAMING
                        val newStatus = if (state.isFirstChunk && chunk.isNotEmpty()) {
                            state.isFirstChunk = false
                            MessageStatus.STREAMING
                        } else {
                            MessageStatus.STREAMING
                        }
                        
                        val updatedEntity = existingMessage.copy(
                            content = state.accumulatedContent,
                            status = newStatus
                        )
                        messageRepository.upsertMessage(updatedEntity)
                    }
                }
            }
            .onCompletion { cause ->
                // 流完成时，更新消息状态为 DONE（如果没有错误）
                // 使用异步执行，避免阻塞流的完成
                if (cause == null && state.accumulatedContent.isNotBlank()) {
                    scope.launch(Dispatchers.IO) {
                        val existingMessage = messageRepository.getMessage(assistantMessageId)
                        if (existingMessage != null) {
                            val updatedEntity = existingMessage.copy(
                                content = state.accumulatedContent,
                                status = MessageStatus.DONE
                            )
                            messageRepository.upsertMessage(updatedEntity)
                            sessionRepository.updateSessionTimestamp(sessionId)
                        }
                    }
                }
            }

        return Output(
            stream = stream,
            taskId = taskId,
            assistantMessageId = assistantMessageId
        )
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

