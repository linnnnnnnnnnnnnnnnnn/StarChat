package com.example.star.aiwork.data.local.datasource

import com.example.star.aiwork.domain.model.embedding.EmbeddingEntity
import kotlinx.coroutines.flow.Flow

interface EmbeddingLocalDataSource {

    /**
     * 创建或更新向量
     */
    suspend fun upsertEmbedding(embedding: EmbeddingEntity)

    /**
     * 根据 ID 获取向量
     */
    suspend fun getEmbedding(id: Int): EmbeddingEntity?

    /**
     * 根据文本获取向量
     */
    suspend fun getEmbeddingByText(text: String): EmbeddingEntity?

    /**
     * 获取所有向量列表
     */
    fun observeAllEmbeddings(): Flow<List<EmbeddingEntity>>

    /**
     * 删除向量
     */
    suspend fun deleteEmbedding(id: Int)

    /**
     * 删除所有向量
     */
    suspend fun deleteAllEmbeddings()
}

