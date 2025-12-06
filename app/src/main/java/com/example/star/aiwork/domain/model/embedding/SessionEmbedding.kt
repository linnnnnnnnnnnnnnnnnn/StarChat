package com.example.star.aiwork.domain.model.embedding

/**
 * 会话专属的向量嵌入域模型。
 *
 * @property id 向量 ID
 * @property sessionId 所属会话 ID
 * @property text 原始文本内容
 * @property embedding 向量数组（768维）
 */
data class SessionEmbedding(
    val id: Int = 0,
    val sessionId: String,
    val text: String,
    val embedding: FloatArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SessionEmbedding

        if (id != other.id) return false
        if (sessionId != other.sessionId) return false
        if (text != other.text) return false
        if (!embedding.contentEquals(other.embedding)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + sessionId.hashCode()
        result = 31 * result + text.hashCode()
        result = 31 * result + embedding.contentHashCode()
        return result
    }
}

