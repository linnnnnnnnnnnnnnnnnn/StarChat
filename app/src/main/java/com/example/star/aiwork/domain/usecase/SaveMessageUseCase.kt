package com.example.star.aiwork.domain.usecase

import com.example.star.aiwork.domain.model.MessageEntity
import com.example.star.aiwork.domain.model.MessageRole
import com.example.star.aiwork.domain.model.MessageType
import com.example.star.aiwork.domain.model.MessageStatus
import com.example.star.aiwork.domain.model.MessageMetadata
import com.example.star.aiwork.domain.repository.MessageRepository
import com.example.star.aiwork.domain.repository.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * 保存消息到 Repository 的用例。
 * 负责将消息实体保存到数据库，并更新会话时间戳。
 */
class SaveMessageUseCase(
    private val messageRepository: MessageRepository,
    private val sessionRepository: SessionRepository
) {
    /**
     * 保存消息到 Repository
     *
     * @param sessionId 会话ID
     * @param role 消息角色
     * @param content 消息内容
     * @param type 消息类型，默认为 TEXT
     * @param metadata 消息元数据
     * @param status 消息状态，默认为 DONE
     * @param updateSessionTimestamp 是否更新会话时间戳，默认为 true
     * @return 生成的消息ID
     */
    suspend operator fun invoke(
        sessionId: String,
        role: MessageRole,
        content: String,
        type: MessageType = MessageType.TEXT,
        metadata: MessageMetadata = MessageMetadata(),
        status: MessageStatus = MessageStatus.DONE,
        updateSessionTimestamp: Boolean = true
    ): String = withContext(Dispatchers.IO) {
        val messageId = UUID.randomUUID().toString()
        val entity = MessageEntity(
            id = messageId,
            sessionId = sessionId,
            role = role,
            type = type,
            content = content,
            metadata = metadata,
            createdAt = System.currentTimeMillis(),
            status = status
        )
        messageRepository.upsertMessage(entity)
        
        // 更新会话时间戳
        if (updateSessionTimestamp) {
            sessionRepository.updateSessionTimestamp(sessionId)
        }
        
        messageId
    }
    
    /**
     * 根据 UI 层的 Message 对象保存消息
     * 用于兼容现有代码
     *
     * @param sessionId 会话ID
     * @param author 消息作者（UI 层的 author 字符串）
     * @param content 消息内容
     * @param imageUrl 图片URL（可选）
     * @param isLoading 是否正在加载
     * @param authorMe 用户自己的标识符（用于判断是否为用户消息）
     * @return 生成的消息ID
     */
    suspend fun saveFromUIMessage(
        sessionId: String,
        author: String,
        content: String,
        imageUrl: String? = null,
        isLoading: Boolean = false,
        authorMe: String = "Me"
    ): String {
        val role = when (author) {
            authorMe -> MessageRole.USER
            "AI", "assistant", "model" -> MessageRole.ASSISTANT
            "System", "system" -> MessageRole.SYSTEM
            else -> MessageRole.USER
        }
        val type = if (imageUrl != null) MessageType.IMAGE else MessageType.TEXT
        val metadata = if (imageUrl != null) MessageMetadata(remoteUrl = imageUrl) else MessageMetadata()
        val status = if (isLoading) MessageStatus.STREAMING else MessageStatus.DONE
        
        return invoke(
            sessionId = sessionId,
            role = role,
            content = content,
            type = type,
            metadata = metadata,
            status = status
        )
    }
}

