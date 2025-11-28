package com.example.star.aiwork.data.local.mapper

import com.example.star.aiwork.data.local.record.DraftRecord
import com.example.star.aiwork.domain.model.DraftEntity

object DraftMapper {

    fun toEntity(record: DraftRecord): DraftEntity {
        return DraftEntity(
            sessionId = record.sessionId,
            content = record.content ?: "",   // null 转 ""
            updatedAt = record.updatedAt
        )
    }

    fun toRecord(entity: DraftEntity): DraftRecord {
        return DraftRecord(
            sessionId = entity.sessionId,
            content = entity.content.ifEmpty { null }, // 空字符串转 null
            updatedAt = entity.updatedAt
        )
    }
}
