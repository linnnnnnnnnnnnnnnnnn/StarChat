package com.example.star.aiwork.domain.usecase.embedding

import com.example.star.aiwork.infra.embedding.EmbeddingService

/**
 * 计算文本向量嵌入的用例。
 * 根据用户输入的文本，使用 EmbeddingService 生成对应的向量表示。
 * 
 * 注意：此方法在后台线程执行，不会阻塞 UI 线程。
 * EmbeddingService 内部使用 withContext(Dispatchers.Default) 确保在后台线程执行。
 *
 * @param embeddingService 向量嵌入服务，用于执行实际的向量计算
 */
class ComputeEmbeddingUseCase(
    private val embeddingService: EmbeddingService
) {
    /**
     * 执行向量计算。
     * 
     * 此方法在后台线程执行，不会阻塞 UI 线程。
     *
     * @param text 用户输入的文本
     * @return 计算得到的向量数组，如果计算失败则返回 null
     */
    suspend operator fun invoke(text: String): FloatArray? {
        return embeddingService.embed(text)
    }
}

