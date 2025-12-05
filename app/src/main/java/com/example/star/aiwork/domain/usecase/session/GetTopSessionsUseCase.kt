package com.example.star.aiwork.domain.usecase.session

import com.example.star.aiwork.data.local.datasource.SessionLocalDataSource
import com.example.star.aiwork.domain.model.SessionEntity

/**
 * 获取前 N 条会话的 UseCase（用于预热缓存）
 */
class GetTopSessionsUseCase(
    private val dataSource: SessionLocalDataSource
) {
    suspend operator fun invoke(limit: Int): List<SessionEntity> {
        return dataSource.getTopSessions(limit)
    }
}

