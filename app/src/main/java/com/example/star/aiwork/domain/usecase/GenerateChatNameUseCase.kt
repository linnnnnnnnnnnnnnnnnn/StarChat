package com.example.star.aiwork.domain.usecase

import com.example.star.aiwork.data.repository.AiRepository
import com.example.star.aiwork.domain.TextGenerationParams
import com.example.star.aiwork.domain.model.ChatDataItem
import com.example.star.aiwork.domain.model.MessageRole
import com.example.star.aiwork.domain.model.Model
import com.example.star.aiwork.domain.model.ProviderSetting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 负责生成对话标题的UseCase。
 * 
 * 将用户的第一条消息发送给AI模型，通过提示词约束模型返回一个简洁的标题。
 */
class GenerateChatNameUseCase(
    private val aiRepository: AiRepository
) {
    /**
     * 默认的系统提示词，用于指导模型生成对话标题。
     */
    private val defaultPrompt = """
        请根据用户的消息，生成一个简洁、准确的对话标题。
        要求：
        1. 标题应该简洁明了，不超过20个字符
        2. 标题应该准确概括用户消息的核心内容
        3. 只返回标题文本，不要包含任何其他说明或标点符号
        4. 如果用户消息是问题，标题应该反映问题的主题
        5. 如果用户消息是陈述，标题应该反映陈述的主要内容
        
        请只返回标题，不要有其他内容。
    """.trimIndent()

    /**
     * 生成对话标题。
     * 
     * @param userMessage 用户的第一条消息内容
     * @param providerSetting AI提供商设置
     * @param model AI模型配置
     * @param prompt 可选的系统提示词，如果不提供则使用默认提示词
     * @param temperature 生成温度参数，默认0.3以获得更稳定的标题
     * @param maxTokens 最大token数，默认50（标题通常很短）
     * @return 返回一个Flow<String>，用于传输模型生成的标题
     */
    operator fun invoke(
        userMessage: String,
        providerSetting: ProviderSetting,
        model: Model,
        prompt: String? = null,
        temperature: Float = 0.3f,
        maxTokens: Int = 50
    ): Flow<String> {
        // 使用提供的prompt或默认prompt
        val systemPrompt = prompt ?: defaultPrompt
        
        // 构建消息列表：系统消息 + 用户消息
        val messages = listOf(
            ChatDataItem(
                role = MessageRole.SYSTEM.name.lowercase(),
                content = systemPrompt
            ),
            ChatDataItem(
                role = MessageRole.USER.name.lowercase(),
                content = userMessage
            )
        )
        
        // 构建文本生成参数
        val params = TextGenerationParams(
            model = model,
            temperature = temperature,
            maxTokens = maxTokens
        )
        
        // 调用AI仓库的streamChat方法，返回Flow<String>
        return aiRepository.streamChat(
            history = messages,
            providerSetting = providerSetting,
            params = params
        )
    }
}

