package com.example.star.aiwork.data.repository

import com.example.star.aiwork.data.local.datasource.DraftLocalDataSource
import com.example.star.aiwork.data.local.mapper.DraftMapper
import com.example.star.aiwork.domain.model.DraftEntity
import com.example.star.aiwork.domain.repository.DraftRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DraftRepositoryImpl(
    private val local: DraftLocalDataSource
) : DraftRepository {

    override suspend fun upsertDraft(draft: DraftEntity) {
        local.upsertDraft(DraftMapper.toRecord(draft))
    }

    override suspend fun getDraft(sessionId: String): DraftEntity? =
        local.getDraft(sessionId)?.let { DraftMapper.toEntity(it) }

    override fun observeDraft(sessionId: String): Flow<DraftEntity?> =
        local.observeDraft(sessionId).map { it?.let { DraftMapper.toEntity(it) } }

    override suspend fun deleteDraft(sessionId: String) {
        local.deleteDraft(sessionId)
    }
}
