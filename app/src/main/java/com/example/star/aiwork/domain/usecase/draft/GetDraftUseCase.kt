package com.example.star.aiwork.domain.usecase.draft

import com.example.star.aiwork.domain.model.DraftEntity
import com.example.star.aiwork.domain.repository.DraftRepository

class GetDraftUseCase(
    private val repository: DraftRepository
) {
    suspend operator fun invoke(sessionId: String): DraftEntity? =
        repository.getDraft(sessionId)
}
