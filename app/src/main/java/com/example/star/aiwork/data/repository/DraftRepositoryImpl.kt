package com.example.star.aiwork.data.repository

import com.example.star.aiwork.data.local.datasource.draft.DraftLocalDataSource
import com.example.star.aiwork.domain.model.DraftEntity
import kotlinx.coroutines.flow.Flow

/**
 * 草稿仓库实现。
 * 
 * 统一管理草稿数据的数据库访问。
 * 草稿数据通常按会话存储，每个会话只有一个草稿，数据量小，因此直接从数据库读取。
 * 
 * 上层调用者无需关心数据来源，由仓库统一管理。
 */
class DraftRepositoryImpl(
    private val localDataSource: DraftLocalDataSource
) {

    /**
     * 创建或更新草稿。
     * 
     * @param draft 草稿实体
     */
    suspend fun upsertDraft(draft: DraftEntity) {
        localDataSource.upsertDraft(draft)
    }

    /**
     * 根据会话 ID 获取草稿。
     * 
     * @param sessionId 会话 ID
     * @return 草稿实体，如果不存在则返回 null
     */
    suspend fun getDraft(sessionId: String): DraftEntity? {
        return localDataSource.getDraft(sessionId)
    }

    /**
     * 观察指定会话的草稿。
     * 返回 Flow，当草稿发生变化时会自动更新。
     * 
     * @param sessionId 会话 ID
     * @return 草稿实体的 Flow
     */
    fun observeDraft(sessionId: String): Flow<DraftEntity?> {
        return localDataSource.observeDraft(sessionId)
    }

    /**
     * 删除指定会话的草稿。
     * 
     * @param sessionId 会话 ID
     */
    suspend fun deleteDraft(sessionId: String) {
        localDataSource.deleteDraft(sessionId)
    }
}

