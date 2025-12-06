package com.example.star.aiwork.data.repository.mapper

import com.example.star.aiwork.domain.model.embedding.Embedding
import com.example.star.aiwork.domain.model.embedding.EmbeddingEntity
import com.example.star.aiwork.domain.model.embedding.GlobalEmbedding
import com.example.star.aiwork.domain.model.embedding.GlobalEmbeddingEntity
import com.example.star.aiwork.domain.model.embedding.SessionEmbedding
import com.example.star.aiwork.domain.model.embedding.SessionEmbeddingEntity

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

/**
 * 将 SessionEmbeddingEntity 转换为 SessionEmbedding 域模型
 */
fun SessionEmbeddingEntity.toDomain(): SessionEmbedding {
    return SessionEmbedding(
        id = id,
        sessionId = sessionId,
        text = text,
        embedding = embedding
    )
}

/**
 * 将 SessionEmbedding 域模型转换为 SessionEmbeddingEntity
 */
fun SessionEmbedding.toEntity(): SessionEmbeddingEntity {
    return SessionEmbeddingEntity(
        id = id,
        sessionId = sessionId,
        text = text,
        embedding = embedding
    )
}

/**
 * 将 GlobalEmbeddingEntity 转换为 GlobalEmbedding 域模型
 */
fun GlobalEmbeddingEntity.toDomain(): GlobalEmbedding {
    return GlobalEmbedding(
        id = id,
        text = text,
        embedding = embedding
    )
}

/**
 * 将 GlobalEmbedding 域模型转换为 GlobalEmbeddingEntity
 */
fun GlobalEmbedding.toEntity(): GlobalEmbeddingEntity {
    return GlobalEmbeddingEntity(
        id = id,
        text = text,
        embedding = embedding
    )
}

