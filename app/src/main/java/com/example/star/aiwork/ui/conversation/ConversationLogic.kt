/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.star.aiwork.ui.conversation

import android.content.Context
import android.net.Uri
import com.example.star.aiwork.domain.TextGenerationParams
import com.example.star.aiwork.domain.model.ChatDataItem
import com.example.star.aiwork.domain.model.MessageRole
import com.example.star.aiwork.domain.model.Model
import com.example.star.aiwork.domain.model.ProviderSetting
import com.example.star.aiwork.data.model.LlmError
import com.example.star.aiwork.domain.usecase.MessagePersistenceGateway
import com.example.star.aiwork.domain.usecase.PauseStreamingUseCase
import com.example.star.aiwork.domain.usecase.RollbackMessageUseCase
import com.example.star.aiwork.domain.usecase.SendMessageUseCase
import com.example.star.aiwork.infra.util.toBase64
import com.example.star.aiwork.ui.ai.UIMessage
import com.example.star.aiwork.ui.ai.UIMessagePart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext

/**
 * Handles the business logic for processing messages in the conversation.
 * Includes sending messages to AI providers, handling fallbacks, and auto-looping agents.
 */
class ConversationLogic(
    private val uiState: ConversationUiState,
    private val context: Context,
    private val authorMe: String,
    private val timeNow: String,
    private val sendMessageUseCase: SendMessageUseCase,
    private val pauseStreamingUseCase: PauseStreamingUseCase,
    private val rollbackMessageUseCase: RollbackMessageUseCase,
    private val sessionId: String,
    private val getProviderSettings: () -> List<ProviderSetting>,
    private val persistenceGateway: MessagePersistenceGateway? = null
) {

    private var activeTaskId: String? = null

    /**
     * 取消当前的流式生成。
     */
    suspend fun cancelStreaming() {
        val taskId = activeTaskId
        if (taskId != null) {
            // 无论成功还是失败，都要清除状态
            pauseStreamingUseCase(taskId).fold(
                onSuccess = {
                    activeTaskId = null
                    withContext(Dispatchers.Main) {
                        uiState.isGenerating = false
                    }
                },
                onFailure = { error ->
                    // 取消失败时也清除状态，但不显示错误（取消操作本身不应该报错）
                    activeTaskId = null
                    withContext(Dispatchers.Main) {
                        uiState.isGenerating = false
                    }
                    // 记录日志但不显示给用户
                    android.util.Log.d("ConversationLogic", "Cancel streaming failed: ${error.message}")
                }
            )
        } else {
            // 如果没有活跃任务，直接清除状态
            withContext(Dispatchers.Main) {
                uiState.isGenerating = false
            }
        }
    }

    suspend fun processMessage(
        inputContent: String,
        providerSetting: ProviderSetting?,
        model: Model?,
        isAutoTriggered: Boolean = false,
        loopCount: Int = 0,
        retrieveKnowledge: suspend (String) -> String = { "" },
        isRetry: Boolean = false
    ) {
        // 1. 如果是用户手动发送，立即显示消息；自动追问也显示在 UI 上
        // 如果是重试 (isRetry=true)，则跳过 UI 消息添加
        if (!isRetry) {
            if (!isAutoTriggered) {
                val currentImageUri = uiState.selectedImageUri
                uiState.addMessage(
                    Message(
                        author = authorMe,
                        content = inputContent,
                        timestamp = timeNow,
                        imageUrl = currentImageUri?.toString()
                    )
                )
                // 清空已选择的图片
                uiState.selectedImageUri = null
            } else {
                // 自动追问消息，可以显示不同的样式或前缀，这里简单处理
                uiState.addMessage(Message(authorMe, "[Auto-Loop ${loopCount}] $inputContent", timeNow))
            }
        }

        // 2. 调用 LLM 获取响应
        if (providerSetting != null && model != null) {
            try {
                // ✅ 设置生成状态为 true
                withContext(Dispatchers.Main) {
                    uiState.isGenerating = true
                }
                
                // RAG Retrieval: 仅对非自动触发的消息尝试检索知识库
                val knowledgeContext = if (!isAutoTriggered) {
                    retrieveKnowledge(inputContent)
                } else {
                    ""
                }

                // 构建增强后的输入内容
                val augmentedInput = if (knowledgeContext.isNotBlank()) {
                    """
                    [Context from Knowledge Base]
                    $knowledgeContext
                    
                    [User Question]
                    $inputContent
                    """.trimIndent()
                } else {
                    inputContent
                }

                val activeAgent = uiState.activeAgent

                // 构造实际要发送的用户消息（考虑模板）
                // 仅对第一条用户原始输入应用模板，自动循环的消息通常是系统生成的指令，不应用模板
                // 注意：我们使用 augmentedInput 进行模板替换或直接发送
                val finalUserContent = if (activeAgent != null && !isAutoTriggered) {
                    activeAgent.messageTemplate.replace("{{ message }}", augmentedInput)
                } else {
                    augmentedInput
                }

                // 收集上下文消息：最近的聊天历史
                val contextMessages = uiState.messages.asReversed()
                    .filter { it.author != "System" } // 过滤掉 System (错误/提示) 消息，避免污染上下文
                    .map { msg ->
                        val role = if (msg.author == authorMe) MessageRole.USER else MessageRole.ASSISTANT
                        val parts = mutableListOf<UIMessagePart>()

                        // 文本部分
                        if (msg.content.isNotEmpty()) {
                            parts.add(UIMessagePart.Text(msg.content))
                        }

                        UIMessage(role = role, parts = parts)
                    }.takeLast(10).toMutableList()

                // **组装完整的消息列表 (Prompt Construction)**
                val messagesToSend = mutableListOf<UIMessage>()

                // 1. 系统提示词 (System Prompt)
                if (activeAgent != null && activeAgent.systemPrompt.isNotEmpty()) {
                    messagesToSend.add(UIMessage(
                        role = MessageRole.SYSTEM,
                        parts = listOf(UIMessagePart.Text(activeAgent.systemPrompt))
                    ))
                }

                // 2. 少样本示例 (Few-shot Examples)
                if (activeAgent != null) {
                    activeAgent.presetMessages.forEach { preset ->
                        messagesToSend.add(UIMessage(
                            role = preset.role,
                            parts = listOf(UIMessagePart.Text(preset.content))
                        ))
                    }
                }

                // 4. 历史对话 (Conversation History)
                messagesToSend.addAll(contextMessages)

                // 5. 当前用户输入 (Current Input)
                // 同样的逻辑：如果是新的一轮对话（非从历史中取出），我们需要确保它在列表中
                // 如果从历史中取出的最后一条和当前输入重复（或 UI 已经添加了），需要小心处理
                // 注意：我们在前面 UI 上添加的是 raw inputContent，但发送给 LLM 的是 finalUserContent (augmented)
                // 历史记录里存的是 raw content。所以 contextMessages 里的最后一条也是 raw content。
                // 我们现在要添加当前这一轮的"真实"请求（包含 context）。

                // 如果 contextMessages 中已经包含了用户刚刚发的 raw message (因为我们先 addMessage 到 uiState)，
                // 我们可能不想重复发一遍 raw message，而是发 augmented version。
                // uiState.messages 包含所有显示的消息。
                // 我们刚才 `uiState.addMessage` 添加了 inputContent。
                // `uiState.messages` 最前面是刚刚添加的消息。
                // `contextMessages` 是 takeLast(10) 并且 asReversed()，所以它包含了刚刚添加的消息作为最后一条。

                // 我们需要把最后一条（即当前的 raw input）替换为 augmented input，或者干脆移除它，单独添加 finalUserContent。
                if (messagesToSend.isNotEmpty() && messagesToSend.last().role == MessageRole.USER) {
                    // 简单起见，我们移除它，并用我们构造的 finalUserContent 代替
                    messagesToSend.removeAt(messagesToSend.lastIndex)
                }

                // 构建当前消息 parts
                val currentMessageParts = mutableListOf<UIMessagePart>()
                currentMessageParts.add(UIMessagePart.Text(finalUserContent))

                // 如果用户选择了图片，也加入到当前输入中
                if (!isAutoTriggered) {
                    val lastUserMsg = uiState.messages.firstOrNull { it.author == authorMe && it.author != "System" }
                    if (lastUserMsg?.imageUrl != null) {
                        try {
                            val imageUri = Uri.parse(lastUserMsg.imageUrl)
                            val base64Image = context.contentResolver.openInputStream(imageUri)?.use { inputStream ->
                                inputStream.readBytes().toBase64()
                            }
                            if (base64Image != null) {
                                val mimeType = context.contentResolver.getType(imageUri) ?: "image/jpeg"
                                currentMessageParts.add(UIMessagePart.Image(url = "data:$mimeType;base64,$base64Image"))
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                messagesToSend.add(UIMessage(
                    role = MessageRole.USER,
                    parts = currentMessageParts
                ))

                val params = TextGenerationParams(
                    model = model,
                    temperature = uiState.temperature,
                    maxTokens = uiState.maxTokens
                )

                // ✅ 添加一个带加载状态的空 AI 消息作为容器
                withContext(Dispatchers.Main) {
                    uiState.addMessage(Message("AI", "", timeNow, isLoading = true))
                }

                // 转换为 ChatDataItem
                val historyChat: List<ChatDataItem> = messagesToSend.dropLast(1).map { message ->
                    toChatDataItem(message)
                }
                val userMessage: ChatDataItem = toChatDataItem(messagesToSend.last())

                val sendResult = sendMessageUseCase(
                    sessionId = sessionId,
                    userMessage = userMessage,
                    history = historyChat,
                    providerSetting = providerSetting,
                    params = TextGenerationParams(
                        model = model,
                        temperature = uiState.temperature,
                        maxTokens = uiState.maxTokens
                    )
                )

                activeTaskId = sendResult.taskId

                var fullResponse = ""
                var lastUpdateTime = 0L
                val UPDATE_INTERVAL_MS = 500L

                // ✅ 无论流式还是非流式，都从 stream 收集响应
                sendResult.stream.collect { delta ->
                    fullResponse += delta
                    withContext(Dispatchers.Main) {
                        // ✅ 第一次收到内容时，移除加载状态
                        if (delta.isNotEmpty()) {
                            uiState.updateLastMessageLoadingState(false)
                        }
                        // ✅ 流式响应时逐字显示，非流式响应时一次性显示
                        if (uiState.streamResponse) {
                            uiState.appendToLastMessage(delta)
                        }

                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastUpdateTime >= UPDATE_INTERVAL_MS) {
                            persistenceGateway?.replaceLastAssistantMessage(
                                sessionId,
                                ChatDataItem(
                                    role = MessageRole.ASSISTANT.name.lowercase(),
                                    content = fullResponse
                                )
                            )
                            lastUpdateTime = currentTime
                        }
                    }
                }
                
                // ✅ 流式响应结束后，如果是非流式模式，一次性显示完整内容
                withContext(Dispatchers.Main) {
                    if (!uiState.streamResponse && fullResponse.isNotBlank()) {
                        uiState.updateLastMessageLoadingState(false)
                        uiState.appendToLastMessage(fullResponse)
                    }
                    // ✅ 响应结束后，设置生成状态为 false
                    uiState.isGenerating = false
                }

                // 流式响应结束后，更新最终内容到数据库（标记为完成状态）
                if (fullResponse.isNotBlank()) {
                    persistenceGateway?.replaceLastAssistantMessage(
                        sessionId,
                        ChatDataItem(
                            role = MessageRole.ASSISTANT.name.lowercase(),
                            content = fullResponse
                        )
                    )
                }

                // --- Auto-Loop Logic with Planner ---
                if (uiState.isAutoLoopEnabled && loopCount < uiState.maxLoopCount && fullResponse.isNotBlank()) {

                    // Step 2: 调用 Planner 模型生成下一步追问
                    val plannerSystemPrompt = """
                                        You are a task planner agent.
                                        Analyze the previous AI response and generate a short, specific instruction for the next step to deepen the task or solve remaining issues.
                                        If the task appears complete or no further meaningful steps are needed, reply with exactly "STOP".
                                        Output ONLY the instruction or "STOP".
                                    """.trimIndent()

                    val plannerUserMessage = ChatDataItem(
                        role = "user",
                        content = "Previous Response:\n$fullResponse"
                    )

                    val plannerHistory = listOf(
                        ChatDataItem(
                            role = "system",
                            content = plannerSystemPrompt
                        )
                    )

                    // 使用相同的 provider/model 进行规划
                    val plannerParams = TextGenerationParams(
                        model = model,
                        temperature = 0.3f, // 使用较低温度以获得更确定的指令
                        maxTokens = 100
                    )

                    val plannerResult = sendMessageUseCase(
                        sessionId = sessionId + "_planner", // 使用不同的 sessionId 避免混淆
                        userMessage = plannerUserMessage,
                        history = plannerHistory,
                        providerSetting = providerSetting,
                        params = plannerParams
                    )

                    var nextInstruction = ""
                    try {
                        plannerResult.stream.collect { delta ->
                            nextInstruction += delta
                        }
                    } catch (e: CancellationException) {
                        // 协程取消异常，重新抛出
                        throw e
                    } catch (e: Exception) {
                        // 检查是否为取消相关的异常
                        val isCancelled = e is LlmError.CancelledError || 
                                e.javaClass.simpleName.contains("CancelledError") ||
                                e.message?.contains("请求已取消") == true ||
                                e.message?.contains("Cancelled") == true ||
                                e.cause is LlmError.CancelledError ||
                                e.cause is CancellationException
                        
                        if (isCancelled) {
                            // 取消操作，停止 planner
                            nextInstruction = "STOP"
                        } else {
                            // 其他异常，记录但不中断流程
                            android.util.Log.e("ConversationLogic", "Planner stream error: ${e.message}", e)
                            nextInstruction = "STOP"
                        }
                    }
                    nextInstruction = nextInstruction.trim()

                    if (nextInstruction != "STOP" && nextInstruction.isNotEmpty()) {
                        // 递归调用，使用 Planner 生成的指令
                        processMessage(
                            inputContent = nextInstruction,
                            providerSetting = providerSetting,
                            model = model,
                            isAutoTriggered = true,
                            loopCount = loopCount + 1,
                            retrieveKnowledge = retrieveKnowledge
                        )
                    }
                }

            } catch (e: Exception) {
                // ✅ 异常时也要设置生成状态为 false
                withContext(Dispatchers.Main) {
                    uiState.isGenerating = false
                }
                
                // 检查是否为取消操作导致的异常，如果是则不显示错误
                val isCancelled = e is LlmError.CancelledError || 
                        e.javaClass.simpleName.contains("CancelledError") ||
                        e.message?.contains("请求已取消") == true ||
                        e.message?.contains("Cancelled") == true ||
                        e.cause is LlmError.CancelledError
                
                if (isCancelled) {
                    // 取消操作是正常的，不显示错误
                    withContext(Dispatchers.Main) {
                        val lastMsg = uiState.messages.firstOrNull()
                        if (lastMsg?.author == "AI" && (lastMsg.content.isEmpty() || lastMsg.isLoading)) {
                            uiState.removeFirstMessage()
                        }
                    }
                    return
                }
                
                // 检查是否为 SiliconCloud 兜底失效导致的异常
                // 异常可能被 LlmError 包装，所以需要检查 message 和 cause
                // 同时也处理 "请求参数无效" (LlmError.RequestError) 的情况，认为这也可能意味着免费策略失效
                val errorMessage = e.message ?: ""
                val causeMessage = e.cause?.message ?: ""

                val isFallbackInvalidated = errorMessage.contains("SiliconCloud fallback strategy invalidated") ||
                        causeMessage.contains("SiliconCloud fallback strategy invalidated")

                // 如果是 RequestError (通常表现为 422/400) 且我们正在尝试 SiliconCloud 的免费模型
                // 这可能意味着之前的 "sk-..." key 彻底失效被拒了
                val isRequestError = e.javaClass.simpleName.contains("RequestError") || errorMessage.contains("请求参数无效")

                // 如果是认证错误 (401/403)
                val isAuthError = e.javaClass.simpleName.contains("AuthenticationError") || errorMessage.contains("认证失败")

                if (isFallbackInvalidated || isRequestError || isAuthError) {
                    // ✅ 移除加载状态的空消息
                    withContext(Dispatchers.Main) {
                        val lastMsg = uiState.messages.firstOrNull()
                        if (lastMsg?.author == "AI" && (lastMsg.content.isEmpty() || lastMsg.isLoading)) {
                            uiState.removeFirstMessage()
                        }

                        val reason = if (isFallbackInvalidated) "兜底失效" else "API 请求被拒 ($errorMessage)"
                        uiState.addMessage(Message("System", "SiliconCloud $reason，正在切换到本地 Ollama...", timeNow))
                    }

                    // 构造 Ollama 兜底设置
                    // 优先使用用户已配置且启用的 Ollama 设置，以复用正确的 Base URL
                    val existingOllama = getProviderSettings()
                        .filterIsInstance<ProviderSetting.Ollama>()
                        .firstOrNull { it.enabled }

                    // 如果找不到，使用默认配置，但将 localhost 改为 10.0.2.2 以适配模拟器环境
                    val fallbackSetting = existingOllama ?: ProviderSetting.Ollama(
                        baseUrl = "http://10.0.2.2:11434"
                    )

                    // 尝试使用用户 Ollama 配置中的第一个模型作为兜底
                    val fallbackModel = fallbackSetting.models.firstOrNull() ?: Model(
                        modelId = "",
                        displayName = ""
                    )

                    // 递归重试（新的 processMessage 调用会重新设置 isGenerating）
                    processMessage(
                        inputContent = inputContent,
                        providerSetting = fallbackSetting,
                        model = fallbackModel,
                        isAutoTriggered = isAutoTriggered,
                        loopCount = loopCount,
                        retrieveKnowledge = retrieveKnowledge,
                        isRetry = true
                    )
                    return
                }

                // ✅ 其他错误也移除加载状态，但不显示错误消息
                withContext(Dispatchers.Main) {
                    val lastMsg = uiState.messages.firstOrNull()
                    if (lastMsg?.author == "AI" && (lastMsg.content.isEmpty() || lastMsg.isLoading)) {
                        uiState.removeFirstMessage()
                    }
                    // 不显示错误消息，只记录日志
                }
                // 记录错误日志但不显示给用户
                android.util.Log.e("ConversationLogic", "Error processing message: ${e.message}", e)
            }
        } else {
            // ✅ 如果没有 provider 或 model，设置生成状态为 false
            withContext(Dispatchers.Main) {
                uiState.isGenerating = false
                uiState.addMessage(
                    Message("System", "No AI Provider configured.", timeNow)
                )
            }
        }
    }

    private fun toChatDataItem(message: UIMessage): ChatDataItem {
        val builder = StringBuilder()
        message.parts.forEach { part ->
            when (part) {
                is UIMessagePart.Text -> builder.append(part.text)
                is UIMessagePart.Image -> builder.append("\n[image:${part.url}]")
                else -> {}
            }
        }
        return ChatDataItem(
            role = message.role.name.lowercase(),
            content = builder.toString()
        )
    }
}