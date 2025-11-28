package com.example.star.aiwork.data.local.mapper

import com.example.star.aiwork.data.local.record.MessageRecord
import com.example.star.aiwork.domain.model.*

object MessageMapper {

    fun toEntity(record: MessageRecord): MessageEntity {
        return MessageEntity(
            id = record.id,
            sessionId = record.sessionId,
            role = MessageRole.valueOf(record.role),
            type = MessageType.TEXT,    // 简化：Record 中没有 type，则默认 TEXT
            content = record.content,
            metadata = MessageMetadata(),  // 简化：Record 无 metadata
            parentMessageId = record.parentMessageId,
            createdAt = record.createdAt,
            status = MessageStatus.values()[record.status]
        )
    }

    fun toRecord(entity: MessageEntity): MessageRecord {
        return MessageRecord(
            id = entity.id,
            sessionId = entity.sessionId,
            role = entity.role.name,
            content = entity.content,
            createdAt = entity.createdAt,
            status = entity.status.ordinal,
            parentMessageId = entity.parentMessageId
        )
    }
}
