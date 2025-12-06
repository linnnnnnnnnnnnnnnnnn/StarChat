package com.example.star.aiwork.domain.usecase.embedding

import com.example.star.aiwork.data.repository.EmbeddingRepository
import com.example.star.aiwork.domain.model.embedding.Embedding

/**
 * 保存向量嵌入的用例
 */
class SaveEmbeddingUseCase(
    private val repository: EmbeddingRepository
) {
    /**
     * 执行保存操作
     *
     * @param embedding 要保存的向量
     */
    suspend operator fun invoke(embedding: Embedding) {
        repository.saveEmbedding(embedding)
    }
}

