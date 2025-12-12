package com.example.star.aiwork.domain.usecase.session

import com.example.star.aiwork.domain.model.SessionEntity
import com.example.star.aiwork.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow

class SearchSessionsUseCase(private val repository: SessionRepository) {
    operator fun invoke(query: String): Flow<List<SessionEntity>> {
        return repository.searchSessions(query)
    }
}
