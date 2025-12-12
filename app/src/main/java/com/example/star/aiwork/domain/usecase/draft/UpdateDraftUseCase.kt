package com.example.star.aiwork.domain.usecase.draft

import com.example.star.aiwork.domain.model.DraftEntity
import com.example.star.aiwork.domain.repository.DraftRepository

class UpdateDraftUseCase(
    private val repository: DraftRepository
) {
    suspend operator fun invoke(sessionId: String, content: String) {
        if (content.isEmpty()) {
            repository.deleteDraft(sessionId)
        } else {
            val draft = DraftEntity(
                sessionId = sessionId,
                content = content,
                updatedAt = System.currentTimeMillis()
            )
            repository.upsertDraft(draft)
        }
    }
}
