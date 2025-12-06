package com.example.star.aiwork.data.repository.mapper

import com.example.star.aiwork.domain.model.Embedding
import com.example.star.aiwork.domain.model.EmbeddingEntity

/**
 * 将 EmbeddingEntity 转换为 Embedding 域模型
 */
fun EmbeddingEntity.toDomain(): Embedding {
    return Embedding(
        id = id,
        text = text,
        embedding = embedding
    )
}

/**
 * 将 Embedding 域模型转换为 EmbeddingEntity
 */
fun Embedding.toEntity(): EmbeddingEntity {
    return EmbeddingEntity(
        id = id,
        text = text,
        embedding = embedding
    )
}

