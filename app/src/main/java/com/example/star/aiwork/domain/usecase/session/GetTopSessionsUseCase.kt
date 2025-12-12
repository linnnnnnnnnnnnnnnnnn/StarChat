package com.example.star.aiwork.domain.usecase.session

import com.example.star.aiwork.domain.model.SessionEntity
import com.example.star.aiwork.domain.repository.SessionRepository

/**
 * 获取前 N 条会话的 UseCase（用于预热缓存）
 */
class GetTopSessionsUseCase(
    private val repository: SessionRepository
) {
    suspend operator fun invoke(limit: Int): List<SessionEntity> {
        return repository.getTopSessions(limit)
    }
}

