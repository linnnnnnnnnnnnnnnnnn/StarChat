package com.example.star.aiwork.domain.usecase

import com.example.star.aiwork.domain.model.MessageStatus
import com.example.star.aiwork.domain.model.MessageMetadata
import com.example.star.aiwork.domain.repository.MessageRepository
import com.example.star.aiwork.domain.repository.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 负责更新消息内容和状态的用例。
 * 主要用于处理取消、错误等特殊场景的消息更新。
 */
class UpdateMessageUseCase(
    private val messageRepository: MessageRepository,
    private val sessionRepository: SessionRepository
) {
    /**
     * 更新消息内容和状态
     *
     * @param messageId 消息ID
     * @param content 新的消息内容
     * @param status 新的消息状态，默认为 DONE
     * @param updateSessionTimestamp 是否更新会话时间戳，默认为 true
     */
    suspend operator fun invoke(
        messageId: String,
        content: String,
        status: MessageStatus = MessageStatus.DONE,
        updateSessionTimestamp: Boolean = true
    ) = withContext(Dispatchers.IO) {
        val existingMessage = messageRepository.getMessage(messageId)
        if (existingMessage != null) {
            val updatedEntity = existingMessage.copy(
                content = content,
                status = status
            )
            messageRepository.upsertMessage(updatedEntity)
            
            // 更新会话时间戳
            if (updateSessionTimestamp) {
                sessionRepository.updateSessionTimestamp(existingMessage.sessionId)
            }
        }
    }
    
    /**
     * 更新消息内容、状态和元数据
     *
     * @param messageId 消息ID
     * @param content 新的消息内容
     * @param status 新的消息状态，默认为 DONE
     * @param metadata 新的消息元数据（可选，如果为 null 则保留原有 metadata）
     * @param updateSessionTimestamp 是否更新会话时间戳，默认为 true
     */
    suspend fun updateWithMetadata(
        messageId: String,
        content: String,
        status: MessageStatus = MessageStatus.DONE,
        metadata: MessageMetadata? = null,
        updateSessionTimestamp: Boolean = true
    ) = withContext(Dispatchers.IO) {
        val existingMessage = messageRepository.getMessage(messageId)
        if (existingMessage != null) {
            val updatedEntity = existingMessage.copy(
                content = content,
                status = status,
                metadata = metadata ?: existingMessage.metadata
            )
            messageRepository.upsertMessage(updatedEntity)
            
            // 更新会话时间戳
            if (updateSessionTimestamp) {
                sessionRepository.updateSessionTimestamp(existingMessage.sessionId)
            }
        }
    }
    
    /**
     * 删除消息（如果消息内容为空）
     *
     * @param messageId 消息ID
     * @return 是否成功删除
     */
    suspend fun deleteIfEmpty(messageId: String): Boolean = withContext(Dispatchers.IO) {
        val existingMessage = messageRepository.getMessage(messageId)
        return@withContext if (existingMessage != null && existingMessage.content.isBlank()) {
            messageRepository.deleteMessage(messageId)
            true
        } else {
            false
        }
    }
}

