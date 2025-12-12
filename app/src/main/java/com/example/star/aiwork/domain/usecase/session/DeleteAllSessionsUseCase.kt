package com.example.star.aiwork.domain.usecase.session

import com.example.star.aiwork.domain.repository.SessionRepository

class DeleteAllSessionsUseCase(private val repository: SessionRepository) {
    suspend operator fun invoke() {
        repository.deleteAllSessions()
    }
}
