package com.example.star.aiwork.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.star.aiwork.domain.model.embedding.GlobalEmbeddingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GlobalEmbeddingDao {
    @Upsert
    suspend fun upsertEmbedding(embedding: GlobalEmbeddingEntity)

    @Query("SELECT * FROM global_embeddings WHERE id = :id")
    suspend fun getEmbedding(id: Int): GlobalEmbeddingEntity?

    @Query("SELECT * FROM global_embeddings WHERE text = :text")
    suspend fun getEmbeddingByText(text: String): GlobalEmbeddingEntity?

    @Query("SELECT * FROM global_embeddings")
    fun getAllEmbeddings(): Flow<List<GlobalEmbeddingEntity>>

    @Query("SELECT * FROM global_embeddings")
    suspend fun getAll(): List<GlobalEmbeddingEntity>

    @Upsert
    suspend fun insert(embedding: GlobalEmbeddingEntity)

    @Query("DELETE FROM global_embeddings WHERE id = :id")
    suspend fun deleteEmbedding(id: Int)

    @Query("DELETE FROM global_embeddings")
    suspend fun deleteAllEmbeddings()
}

