package com.example.star.aiwork.domain.model.embedding

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 全局向量嵌入实体。
 * 不与特定会话关联的全局向量嵌入。
 *
 * @property id 向量 ID
 * @property text 原始内容
 * @property embedding 向量（768维）
 */
@Entity(tableName = "global_embeddings")
data class GlobalEmbeddingEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val text: String,                 // 原始内容
    val embedding: FloatArray         // 向量（768维）
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GlobalEmbeddingEntity

        if (id != other.id) return false
        if (text != other.text) return false
        if (!embedding.contentEquals(other.embedding)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + text.hashCode()
        result = 31 * result + embedding.contentHashCode()
        return result
    }
}

