package com.example.star.aiwork.domain.repository

import com.example.star.aiwork.domain.model.DraftEntity
import kotlinx.coroutines.flow.Flow

/**
 * 草稿仓库接口。
 * 
 * 定义草稿数据的访问接口，由 Data 层实现。
 * Domain 层和 UseCase 只依赖此接口，不依赖具体实现。
 */
interface DraftRepository {

    /**
     * 创建或更新草稿。
     * 
     * @param draft 草稿实体
     */
    suspend fun upsertDraft(draft: DraftEntity)

    /**
     * 根据会话 ID 获取草稿。
     * 
     * @param sessionId 会话 ID
     * @return 草稿实体，如果不存在则返回 null
     */
    suspend fun getDraft(sessionId: String): DraftEntity?

    /**
     * 观察指定会话的草稿。
     * 返回 Flow，当草稿发生变化时会自动更新。
     * 
     * @param sessionId 会话 ID
     * @return 草稿实体的 Flow
     */
    fun observeDraft(sessionId: String): Flow<DraftEntity?>

    /**
     * 删除指定会话的草稿。
     * 
     * @param sessionId 会话 ID
     */
    suspend fun deleteDraft(sessionId: String)
}

