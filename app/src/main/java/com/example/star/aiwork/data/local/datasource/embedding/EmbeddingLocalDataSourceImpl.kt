package com.example.star.aiwork.data.local.datasource.embedding

import android.content.Context
import com.example.star.aiwork.data.local.EmbeddingDatabaseProvider
import com.example.star.aiwork.domain.model.embedding.EmbeddingEntity
import kotlinx.coroutines.flow.Flow

class EmbeddingLocalDataSourceImpl(context: Context) : EmbeddingLocalDataSource {

    private val embeddingDao = EmbeddingDatabaseProvider.getDatabase(context).embeddingDao()

    override suspend fun upsertEmbedding(embedding: EmbeddingEntity) {
        embeddingDao.upsertEmbedding(embedding)
    }

    override suspend fun getEmbedding(id: Int): EmbeddingEntity? {
        return embeddingDao.getEmbedding(id)
    }

    override suspend fun getEmbeddingByText(text: String): EmbeddingEntity? {
        return embeddingDao.getEmbeddingByText(text)
    }

    override fun observeAllEmbeddings(): Flow<List<EmbeddingEntity>> {
        return embeddingDao.getAllEmbeddings()
    }

    override suspend fun deleteEmbedding(id: Int) {
        embeddingDao.deleteEmbedding(id)
    }

    override suspend fun deleteAllEmbeddings() {
        embeddingDao.deleteAllEmbeddings()
    }
}

