package com.example.star.aiwork.data.repository

import com.example.star.aiwork.data.repository.mapper.toDomain
import com.example.star.aiwork.data.repository.mapper.toEntity
import com.example.star.aiwork.data.local.dao.EmbeddingDao
import com.example.star.aiwork.domain.model.Embedding

class EmbeddingRepositoryImpl(
    private val dao: EmbeddingDao
) : EmbeddingRepository {

    override suspend fun getAllEmbeddings(): List<Embedding> {
        return dao.getAll().map { it.toDomain() }
    }

    override suspend fun saveEmbedding(embedding: Embedding) {
        dao.insert(embedding.toEntity())
    }
}

