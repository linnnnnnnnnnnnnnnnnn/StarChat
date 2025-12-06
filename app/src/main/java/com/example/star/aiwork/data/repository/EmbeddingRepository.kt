package com.example.star.aiwork.data.repository

import com.example.star.aiwork.domain.model.embedding.Embedding

/**
 * 向量嵌入仓库接口
 */
interface EmbeddingRepository {
    /**
     * 获取所有向量
     */
    suspend fun getAllEmbeddings(): List<Embedding>

    /**
     * 保存向量
     */
    suspend fun saveEmbedding(embedding: Embedding)
}

