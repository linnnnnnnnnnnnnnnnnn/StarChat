package com.example.star.aiwork.data.repository

import com.example.star.aiwork.data.local.datasource.session.SessionCacheDataSource
import com.example.star.aiwork.data.local.datasource.session.SessionLocalDataSource
import com.example.star.aiwork.domain.model.SessionEntity
import com.example.star.aiwork.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow

/**
 * 会话仓库实现。
 * 
 * 统一管理会话数据的缓存和数据库访问。
 * 采用缓存优先策略：
 * - 读取：优先从缓存读取，缓存未命中时从数据库读取并写入缓存
 * - 写入：同时更新缓存和数据库，保证数据一致性
 * 
 * 上层调用者无需关心数据来源，由仓库统一管理。
 */
class SessionRepositoryImpl(
    private val cacheDataSource: SessionCacheDataSource,
    private val localDataSource: SessionLocalDataSource
) : SessionRepository {

    /**
     * 创建或更新会话。
     * 同时更新缓存和数据库。
     * 
     * @param session 会话实体
     */
    override suspend fun upsertSession(session: SessionEntity) {
        // 同时更新缓存和数据库
        cacheDataSource.put(session.id, session)
        localDataSource.upsertSession(session)
    }

    /**
     * 获取单个会话。
     * 优先从缓存读取，缓存未命中时从数据库读取并写入缓存。
     * 
     * @param id 会话 ID
     * @return 会话实体，如果不存在则返回 null
     */
    override suspend fun getSession(id: String): SessionEntity? {
        // 先从缓存读取
        val cached = cacheDataSource.get(id)
        if (cached != null) {
            return cached
        }
        
        // 缓存未命中，从数据库读取
        val fromDb = localDataSource.getSession(id)
        if (fromDb != null) {
            // 写入缓存，以便下次快速访问
            cacheDataSource.put(id, fromDb)
        }
        
        return fromDb
    }

    /**
     * 获取所有会话列表（会话列表 UI 用）。
     * 从数据库获取，因为需要完整的列表数据。
     * 
     * @return 会话列表的 Flow
     */
    override fun observeSessions(): Flow<List<SessionEntity>> {
        return localDataSource.observeSessions()
    }

    /**
     * 获取前 N 条会话（用于预热缓存）。
     * 从数据库获取并写入缓存。
     * 
     * @param limit 限制数量
     * @return 会话列表
     */
    override suspend fun getTopSessions(limit: Int): List<SessionEntity> {
        val sessions = localDataSource.getTopSessions(limit)
        // 将获取的会话写入缓存
        sessions.forEach { session ->
            cacheDataSource.put(session.id, session)
        }
        return sessions
    }

    /**
     * 删除会话。
     * 同时从缓存和数据库删除。
     * 
     * @param id 会话 ID
     */
    override suspend fun deleteSession(id: String) {
        cacheDataSource.remove(id)
        localDataSource.deleteSession(id)
    }

    /**
     * 删除所有会话。
     * 同时清空缓存和数据库。
     */
    override suspend fun deleteAllSessions() {
        cacheDataSource.clear()
        localDataSource.deleteAllSessions()
    }

    /**
     * 搜索会话。
     * 从数据库搜索，因为需要全文搜索功能。
     * 
     * @param query 搜索关键词
     * @return 搜索结果列表的 Flow
     */
    override fun searchSessions(query: String): Flow<List<SessionEntity>> {
        return localDataSource.searchSessions(query)
    }

    /**
     * 预热缓存。
     * 将指定数量的最近会话加载到缓存中。
     * 
     * @param limit 预热的会话数量，默认为 10
     */
    suspend fun warmupCache(limit: Int = 10) {
        getTopSessions(limit)
    }
}

