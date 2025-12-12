package com.example.star.aiwork.domain.usecase.session

import com.example.star.aiwork.domain.model.SessionEntity
import com.example.star.aiwork.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow

class GetSessionListUseCase(
    private val repository: SessionRepository
) {
    operator fun invoke(): Flow<List<SessionEntity>> = repository.observeSessions()
}
