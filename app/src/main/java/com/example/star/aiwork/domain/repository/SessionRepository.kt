package com.example.star.aiwork.domain.repository

import com.example.star.aiwork.domain.model.SessionEntity
import kotlinx.coroutines.flow.Flow

/**
 * 会话仓库接口。
 * 
 * 定义会话数据的访问接口，由 Data 层实现。
 * Domain 层和 UseCase 只依赖此接口，不依赖具体实现。
 */
interface SessionRepository {

    /**
     * 创建或更新会话。
     * 
     * @param session 会话实体
     */
    suspend fun upsertSession(session: SessionEntity)

    /**
     * 获取单个会话。
     * 
     * @param id 会话 ID
     * @return 会话实体，如果不存在则返回 null
     */
    suspend fun getSession(id: String): SessionEntity?

    /**
     * 获取所有会话列表（会话列表 UI 用）。
     * 
     * @return 会话列表的 Flow
     */
    fun observeSessions(): Flow<List<SessionEntity>>

    /**
     * 获取前 N 条会话（用于预热缓存）。
     * 
     * @param limit 限制数量
     * @return 会话列表
     */
    suspend fun getTopSessions(limit: Int): List<SessionEntity>

    /**
     * 删除会话。
     * 
     * @param id 会话 ID
     */
    suspend fun deleteSession(id: String)

    /**
     * 删除所有会话。
     */
    suspend fun deleteAllSessions()

    /**
     * 搜索会话。
     * 
     * @param query 搜索关键词
     * @return 搜索结果列表的 Flow
     */
    fun searchSessions(query: String): Flow<List<SessionEntity>>
}

