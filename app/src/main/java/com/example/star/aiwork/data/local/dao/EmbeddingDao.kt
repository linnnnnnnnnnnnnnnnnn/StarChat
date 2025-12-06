package com.example.star.aiwork.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.star.aiwork.domain.model.EmbeddingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EmbeddingDao {
    @Upsert
    suspend fun upsertEmbedding(embedding: EmbeddingEntity)

    @Query("SELECT * FROM embeddings WHERE id = :id")
    suspend fun getEmbedding(id: Int): EmbeddingEntity?

    @Query("SELECT * FROM embeddings WHERE text = :text")
    suspend fun getEmbeddingByText(text: String): EmbeddingEntity?

    @Query("SELECT * FROM embeddings")
    fun getAllEmbeddings(): Flow<List<EmbeddingEntity>>

    @Query("SELECT * FROM embeddings")
    suspend fun getAll(): List<EmbeddingEntity>

    @Upsert
    suspend fun insert(embedding: EmbeddingEntity)

    @Query("DELETE FROM embeddings WHERE id = :id")
    suspend fun deleteEmbedding(id: Int)

    @Query("DELETE FROM embeddings")
    suspend fun deleteAllEmbeddings()
}

