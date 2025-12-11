package com.example.star.aiwork.domain.usecase.session

import com.example.star.aiwork.data.local.datasource.draft.DraftLocalDataSource
import com.example.star.aiwork.data.local.datasource.message.MessageLocalDataSource
import com.example.star.aiwork.data.local.datasource.session.SessionLocalDataSource

class DeleteSessionUseCase(
    private val sessionDataSource: SessionLocalDataSource,
    private val messageDataSource: MessageLocalDataSource,
    private val draftDataSource: DraftLocalDataSource
) {
    suspend operator fun invoke(sessionId: String) {
        messageDataSource.deleteMessagesBySession(sessionId)
        draftDataSource.deleteDraft(sessionId)
        sessionDataSource.deleteSession(sessionId)
    }
}
