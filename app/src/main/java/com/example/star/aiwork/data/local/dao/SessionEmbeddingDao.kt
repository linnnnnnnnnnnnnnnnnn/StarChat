package com.example.star.aiwork.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.star.aiwork.domain.model.embedding.SessionEmbeddingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionEmbeddingDao {
    @Upsert
    suspend fun upsertEmbedding(embedding: SessionEmbeddingEntity)

    @Query("SELECT * FROM session_embeddings WHERE id = :id")
    suspend fun getEmbedding(id: Int): SessionEmbeddingEntity?

    @Query("SELECT * FROM session_embeddings WHERE sessionId = :sessionId")
    suspend fun getEmbeddingsBySession(sessionId: String): List<SessionEmbeddingEntity>

    @Query("SELECT * FROM session_embeddings WHERE sessionId = :sessionId")
    fun observeEmbeddingsBySession(sessionId: String): Flow<List<SessionEmbeddingEntity>>

    @Query("SELECT * FROM session_embeddings WHERE sessionId = :sessionId AND text = :text")
    suspend fun getEmbeddingBySessionAndText(sessionId: String, text: String): SessionEmbeddingEntity?

    @Query("SELECT * FROM session_embeddings")
    fun getAllEmbeddings(): Flow<List<SessionEmbeddingEntity>>

    @Query("SELECT * FROM session_embeddings")
    suspend fun getAll(): List<SessionEmbeddingEntity>

    @Upsert
    suspend fun insert(embedding: SessionEmbeddingEntity)

    @Query("DELETE FROM session_embeddings WHERE id = :id")
    suspend fun deleteEmbedding(id: Int)

    @Query("DELETE FROM session_embeddings WHERE sessionId = :sessionId")
    suspend fun deleteEmbeddingsBySession(sessionId: String)

    @Query("DELETE FROM session_embeddings")
    suspend fun deleteAllEmbeddings()
}

