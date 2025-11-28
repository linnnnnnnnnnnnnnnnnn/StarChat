package com.example.star.aiwork.data.local.mapper

import com.example.star.aiwork.data.local.record.SessionRecord
import com.example.star.aiwork.domain.model.SessionEntity
import com.example.star.aiwork.domain.model.SessionMetadata

object SessionMapper {

    fun toEntity(record: SessionRecord): SessionEntity {
        return SessionEntity(
            id = record.id,
            name = record.name,
            createdAt = record.createdAt,
            updatedAt = record.updatedAt,
            pinned = record.pinned,
            archived = record.archived,
            metadata = SessionMetadata() // 简化，Record 无 metadata
        )
    }

    fun toRecord(entity: SessionEntity): SessionRecord {
        return SessionRecord(
            id = entity.id,
            name = entity.name,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            pinned = entity.pinned,
            archived = entity.archived
        )
    }
}
