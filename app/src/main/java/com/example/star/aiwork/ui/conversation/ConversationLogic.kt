package com.example.star.aiwork.ui.conversation

import android.content.Context
import android.util.Log
import com.example.star.aiwork.domain.TextGenerationParams
import com.example.star.aiwork.domain.model.ChatDataItem
import com.example.star.aiwork.domain.model.MessageRole
import com.example.star.aiwork.domain.model.Model
import com.example.star.aiwork.domain.model.ModelType
import com.example.star.aiwork.domain.model.ProviderSetting
import com.example.star.aiwork.domain.usecase.GenerateChatNameUseCase
import com.example.star.aiwork.domain.usecase.ImageGenerationUseCase
import com.example.star.aiwork.domain.usecase.PauseStreamingUseCase
import com.example.star.aiwork.domain.usecase.RollbackMessageUseCase
import com.example.star.aiwork.domain.usecase.SendMessageUseCase
import com.example.star.aiwork.domain.repository.SessionRepository
import com.example.star.aiwork.domain.usecase.embedding.ComputeEmbeddingUseCase
import com.example.star.aiwork.domain.usecase.embedding.FilterMemoryMessagesUseCase
import com.example.star.aiwork.domain.usecase.embedding.SaveEmbeddingUseCase
import com.example.star.aiwork.domain.usecase.embedding.SearchEmbeddingUseCase
import com.example.star.aiwork.domain.repository.MessageRepository
import com.example.star.aiwork.domain.model.MessageEntity
import com.example.star.aiwork.domain.model.MessageType
import com.example.star.aiwork.domain.model.MessageStatus
import com.example.star.aiwork.domain.model.MessageMetadata
import com.example.star.aiwork.ui.conversation.logic.BufferedMemoryItem
import com.example.star.aiwork.ui.conversation.logic.MemoryBuffer
import com.example.star.aiwork.ui.conversation.util.ConversationErrorHelper.getErrorMessage
import com.example.star.aiwork.data.model.LlmError
import com.example.star.aiwork.ui.conversation.util.ConversationLogHelper.logAllMessagesToSend
import com.example.star.aiwork.ui.conversation.logic.ImageGenerationHandler
import com.example.star.aiwork.ui.conversation.logic.MemoryTriggerFilter
import com.example.star.aiwork.ui.conversation.logic.MessageConstructionHelper
import com.example.star.aiwork.ui.conversation.logic.RollbackHandler
import com.example.star.aiwork.ui.conversation.logic.StreamingResponseHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Handles the business logic for processing messages in the conversation.
 * Includes sending messages to AI providers and handling fallbacks.
 * 
 * Refactored to delegate responsibilities to smaller handlers:
 * - ImageGenerationHandler
 * - StreamingResponseHandler
 * - RollbackHandler
 * - MessageConstructionHelper
 */
class ConversationLogic(
    private val uiState: ConversationUiState,
    private val context: Context,
    private val authorMe: String,
    private val timeNow: String,
    private val sendMessageUseCase: SendMessageUseCase,
    private val pauseStreamingUseCase: PauseStreamingUseCase,
    private val rollbackMessageUseCase: RollbackMessageUseCase,
    private val imageGenerationUseCase: ImageGenerationUseCase,
    private val generateChatNameUseCase: GenerateChatNameUseCase? = null,
    private val sessionId: String,
    private val getProviderSettings: () -> List<ProviderSetting>,
    private val messageRepository: MessageRepository? = null,
    private val sessionRepository: SessionRepository? = null,
    private val onRenameSession: (sessionId: String, newName: String) -> Unit,
    private val onPersistNewChatSession: suspend (sessionId: String) -> Unit = { },
    private val isNewChat: (sessionId: String) -> Boolean = { false },
    private val onSessionUpdated: suspend (sessionId: String) -> Unit = { },
    private val taskManager: StreamingTaskManager? = null,
    private val computeEmbeddingUseCase: ComputeEmbeddingUseCase? = null,
    private val searchEmbeddingUseCase: SearchEmbeddingUseCase? = null,
    private val saveEmbeddingUseCase: SaveEmbeddingUseCase? = null,
    private val filterMemoryMessagesUseCase: FilterMemoryMessagesUseCase? = null,
    private val embeddingTopK: Int = 3,
    private val getProviderSetting: () -> ProviderSetting? = { null },
    private val getModel: () -> Model? = { null }
) {

    // 用于保存流式收集协程的 Job，以便可以立即取消
    private var streamingJob: Job? = null
    // 用于保存提示消息流式显示的 Job，以便可以立即取消
    private var hintTypingJob: Job? = null
    // 使用 uiState 的协程作用域，这样每个会话可以管理自己的协程
    private val streamingScope: CoroutineScope = uiState.coroutineScope
    // 标记是否已被取消，用于非流式模式下避免显示已收集的内容
    @Volatile private var isCancelled = false
    
    // 当前正在流式生成的消息 ID（用于更新消息内容）
    private var currentStreamingMessageId: String? = null

    /**
     * 将 UI 层的 Message 转换为 MessageEntity 并保存到 Repository
     * @param message 要保存的消息
     * @param createdAt 可选的时间戳，如果不提供则使用当前时间
     */
    private suspend fun saveMessageToRepository(message: Message, createdAt: Long? = null): String {
        val messageId = UUID.randomUUID().toString()
        val role = when (message.author) {
            authorMe -> MessageRole.USER
            "AI", "assistant", "model" -> MessageRole.ASSISTANT
            "System", "system" -> MessageRole.SYSTEM
            else -> MessageRole.USER
        }
        val type = when {
            message.imageUrl != null -> MessageType.IMAGE
            role == MessageRole.SYSTEM -> MessageType.SYSTEM
            else -> MessageType.TEXT
        }
        val status = when {
            message.isLoading -> MessageStatus.STREAMING
            else -> MessageStatus.DONE
        }
        
        val entity = MessageEntity(
            id = messageId,
            sessionId = sessionId,
            role = role,
            type = type,
            content = message.content,
            metadata = MessageMetadata(
                remoteUrl = message.imageUrl,
                localFilePath = message.imageUrl
            ),
            createdAt = createdAt ?: System.currentTimeMillis(),
            status = status
        )
        
        messageRepository?.upsertMessage(entity)
        return messageId
    }

    /**
     * 更新 Repository 中的消息内容（用于流式输出）
     */
    private suspend fun updateMessageInRepository(messageId: String, content: String, isLoading: Boolean = false) {
        val existingMessage = messageRepository?.getMessage(messageId)
        if (existingMessage != null) {
            val updatedEntity = existingMessage.copy(
                content = content,
                status = if (isLoading) MessageStatus.STREAMING else MessageStatus.DONE
            )
            messageRepository.upsertMessage(updatedEntity)
        }
    }

    // Handlers
    private val imageGenerationHandler = ImageGenerationHandler(
        uiState = uiState,
        imageGenerationUseCase = imageGenerationUseCase,
        messageRepository = messageRepository,
        sessionRepository = sessionRepository,
        sessionId = sessionId,
        timeNow = timeNow,
        onSessionUpdated = onSessionUpdated
    )

    private val streamingResponseHandler = StreamingResponseHandler(
        uiState = uiState,
        messageRepository = messageRepository,
        sessionRepository = sessionRepository,
        sessionId = sessionId,
        timeNow = timeNow,
        onSessionUpdated = onSessionUpdated,
        onMessageIdCreated = { messageId -> currentStreamingMessageId = messageId },
        getCurrentMessageId = { currentStreamingMessageId }
    )

    private val rollbackHandler = RollbackHandler(
        uiState = uiState,
        rollbackMessageUseCase = rollbackMessageUseCase,
        messageRepository = messageRepository,
        streamingResponseHandler = streamingResponseHandler,
        sessionId = sessionId,
        authorMe = authorMe,
        timeNow = timeNow,
        onMessageIdCreated = { messageId -> currentStreamingMessageId = messageId }
    )

    // 创建 MemoryBuffer，当 buffer 满了时触发批量处理
    private val memoryBuffer = if (filterMemoryMessagesUseCase != null && saveEmbeddingUseCase != null) {
        MemoryBuffer(maxSize = 5) { items ->
            handleBufferFull(items)
        }
    } else {
        null
    }

    private val memoryTriggerFilter = MemoryTriggerFilter(
        computeEmbeddingUseCase = computeEmbeddingUseCase,
        saveEmbeddingUseCase = saveEmbeddingUseCase,
        memoryBuffer = memoryBuffer
    )

    /**
     * 处理 buffer 满了的情况
     * 调用 FilterMemoryMessagesUseCase 判断哪些消息需要保存，然后保存它们
     */
    private suspend fun handleBufferFull(items: List<BufferedMemoryItem>) {
        Log.d("ConversationLogic", "=".repeat(80))
        Log.d("ConversationLogic", "🔄 [批量处理] Buffer 已满，开始批量处理")
        
        if (items.isEmpty()) {
            Log.w("ConversationLogic", "⚠️ [批量处理] 消息列表为空，跳过处理")
            return
        }

        Log.d("ConversationLogic", "   └─ 待处理消息数量: ${items.size}")
        items.forEachIndexed { index, item ->
            Log.d("ConversationLogic", "   [$index] ${item.text.take(60)}${if (item.text.length > 60) "..." else ""} (embedding: ${item.embedding.size}维)")
        }

        val providerSetting = getProviderSetting()
        val model = getModel()
        
        if (filterMemoryMessagesUseCase == null || providerSetting == null || model == null) {
            Log.w("ConversationLogic", "⚠️ [批量处理] 依赖项缺失，跳过处理")
            Log.w("ConversationLogic", "   └─ FilterMemoryMessagesUseCase: ${filterMemoryMessagesUseCase != null}")
            Log.w("ConversationLogic", "   └─ ProviderSetting: ${providerSetting != null}")
            Log.w("ConversationLogic", "   └─ Model: ${model != null}")
            return
        }

        Log.d("ConversationLogic", "   └─ Provider: ${providerSetting.name}, Model: ${model.modelId}")

        try {
            // 提取文本列表
            val texts = items.map { it.text }
            Log.d("ConversationLogic", "📤 [批量处理] 调用 FilterMemoryMessagesUseCase 进行 AI 判断")
            Log.d("ConversationLogic", "   └─ 发送 ${texts.size} 条消息文本给 AI 模型")
            
            // 调用 FilterMemoryMessagesUseCase 判断哪些需要保存
            val indicesToSave = filterMemoryMessagesUseCase(
                messages = texts,
                providerSetting = providerSetting,
                model = model
            )
            
            Log.d("ConversationLogic", "📥 [批量处理] AI 模型返回结果")
            Log.d("ConversationLogic", "   └─ 需要保存的消息索引: $indicesToSave")
            Log.d("ConversationLogic", "   └─ 需要保存的消息数量: ${indicesToSave.size}/${items.size}")
            
            if (indicesToSave.isEmpty()) {
                Log.d("ConversationLogic", "⏭️ [批量处理] AI 模型判断没有消息需要写入长期记忆")
                Log.d("ConversationLogic", "=".repeat(80))
                return
            }
            
            // 记录被选中的消息详情
            indicesToSave.forEach { index ->
                if (index >= 0 && index < items.size) {
                    val item = items[index]
                    Log.d("ConversationLogic", "   ✅ 索引 $index 被选中: ${item.text.take(60)}${if (item.text.length > 60) "..." else ""}")
                } else {
                    Log.w("ConversationLogic", "   ⚠️ 无效索引: $index (总数: ${items.size})")
                }
            }
            
            // 在后台线程执行保存操作
            Log.d("ConversationLogic", "💾 [批量处理] 开始保存被选中的消息到数据库")
            withContext(Dispatchers.IO) {
                var successCount = 0
                var failCount = 0
                
                indicesToSave.forEach { index ->
                    if (index >= 0 && index < items.size) {
                        try {
                            val item = items[index]
                            Log.d("ConversationLogic", "   💾 正在保存索引 $index...")
                            memoryTriggerFilter.saveMemoryWithEmbedding(item.text, item.embedding)
                            successCount++
                            Log.d("ConversationLogic", "   ✅ 索引 $index 保存成功")
                        } catch (e: Exception) {
                            failCount++
                            Log.e("ConversationLogic", "   ❌ 索引 $index 保存失败: ${e.message}", e)
                        }
                    }
                }
                
                Log.d("ConversationLogic", "📊 [批量处理] 保存统计")
                Log.d("ConversationLogic", "   └─ 成功: $successCount, 失败: $failCount, 总计: ${indicesToSave.size}")
            }
            
            Log.d("ConversationLogic", "✅ [批量处理] 批量处理完成")
            Log.d("ConversationLogic", "=".repeat(80))
            
        } catch (e: Exception) {
            Log.e("ConversationLogic", "❌ [批量处理] 批量处理失败: ${e.message}", e)
            Log.e("ConversationLogic", "   └─ 异常类型: ${e.javaClass.simpleName}")
            e.printStackTrace()
            Log.d("ConversationLogic", "=".repeat(80))
            // 发生错误时静默处理，不影响正常流程
        }
    }

    /**
     * 取消当前的流式生成。
     */
    suspend fun cancelStreaming() {
        // 立即取消流式收集协程和提示消息的流式显示协程
        isCancelled = true
        streamingJob?.cancel()
        streamingJob = null
        hintTypingJob?.cancel() // 取消提示消息的流式显示
        hintTypingJob = null
        
        // 通过任务管理器取消任务（即使 ConversationLogic 重新创建也能取消）
        taskManager?.cancelTasks(sessionId)
        
        // 根据流式模式决定处理方式
        val currentContent: String
        val messageId = currentStreamingMessageId
        if (messageId != null) {
            if (uiState.streamResponse) {
                // 流式模式：在消息末尾追加取消提示
                val existingMessage = messageRepository?.getMessage(messageId)
                currentContent = (existingMessage?.content ?: "") + "\n（已取消生成）"
            } else {
                // 非流式模式：清空已收集的内容，只显示取消提示
                currentContent = "（已取消生成）"
            }
            
            // 更新 Repository 中的消息（包含取消提示）
            if (currentContent.isNotEmpty()) {
                updateMessageInRepository(messageId, currentContent, isLoading = false)
                // 消息已经通过 updateMessageInRepository() 保存到数据库
                // 更新会话的 updatedAt 时间戳
                sessionRepository?.updateSessionTimestamp(sessionId)
            }
        } else {
            currentContent = ""
        }
        
        // 使用 uiState 中保存的 activeTaskId（即使 ConversationLogic 重新创建也能恢复）
        val taskId = uiState.activeTaskId
        if (taskId != null) {
            // 无论成功还是失败，都要清除状态
            pauseStreamingUseCase(taskId).fold(
                onSuccess = {
                    withContext(Dispatchers.Main) {
                        uiState.activeTaskId = null
                        uiState.isGenerating = false
                    }
                },
                onFailure = { error ->
                    // 取消失败时也清除状态，但不显示错误（取消操作本身不应该报错）
                    withContext(Dispatchers.Main) {
                        uiState.activeTaskId = null
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
        
        // Session management (New Chat / Rename)
        if (isNewChat(sessionId)) {
            onPersistNewChatSession(sessionId)
            
            // ADDED: Auto-rename session logic using GenerateChatNameUseCase
            // 只有在新聊天且是第一条用户消息时才自动重命名
            // 注意：现在通过 Repository 检查消息，而不是 uiState.messages
            if (!isAutoTriggered && (uiState.channelName == "New Chat" || uiState.channelName == "新聊天" || uiState.channelName == "新会话" || uiState.channelName == "new chat")) {
                // 检查是否已有用户消息（通过 Repository）
                val hasUserMessage = withContext(Dispatchers.IO) {
                    messageRepository?.observeMessages(sessionId)?.first()?.any { it.role == MessageRole.USER } ?: false
                }
                if (!hasUserMessage) {
                    if (generateChatNameUseCase != null && providerSetting != null && model != null) {
                        // 使用GenerateChatNameUseCase生成标题
                        streamingScope.launch(Dispatchers.IO) {
                            try {
                                val titleFlow = generateChatNameUseCase(
                                    userMessage = inputContent,
                                    providerSetting = providerSetting,
                                    model = model
                                )
                                
                                var generatedTitle = StringBuilder()
                                titleFlow
                                    .onCompletion { 
                                        // 流完成后，持久化生成的标题
                                        val finalTitle = generatedTitle.toString().trim()
                                        if (finalTitle.isNotBlank()) {
                                            // 限制标题长度，避免过长
                                            val trimmedTitle = finalTitle.take(30).trim()
                                            withContext(Dispatchers.Main) {
                                                // 确保UI显示最终处理后的标题（可能和流过程中的显示略有不同）
                                                uiState.channelName = trimmedTitle
                                                // 持久化标题到数据库
                                                onRenameSession(sessionId, trimmedTitle)
                                                onSessionUpdated(sessionId)
                                                Log.d("ConversationLogic", "✅ [Auto-Rename] AI生成标题持久化完成: $trimmedTitle")
                                            }
                                        } else {
                                            // 如果AI生成失败，回退到简单截取
                                            val fallbackTitle = inputContent.take(20).trim()
                                            if (fallbackTitle.isNotBlank()) {
                                                withContext(Dispatchers.Main) {
                                                    // 更新UI显示
                                                    uiState.channelName = fallbackTitle
                                                    // 持久化标题到数据库
                                                    onRenameSession(sessionId, fallbackTitle)
                                                    onSessionUpdated(sessionId)
                                                    Log.d("ConversationLogic", "✅ [Auto-Rename] 回退标题完成: $fallbackTitle")
                                                }
                                            }
                                        }
                                    }
                                    .collect { chunk ->
                                        // 实时更新UI中的标题显示（不等待流结束）
                                        generatedTitle.append(chunk)
                                        val currentTitle = generatedTitle.toString().trim()
                                        if (currentTitle.isNotBlank()) {
                                            // 限制显示长度，避免过长
                                            val displayTitle = currentTitle.take(30).trim()
                                            withContext(Dispatchers.Main) {
                                                uiState.channelName = displayTitle
                                            }
                                        }
                                    }
                            } catch (e: Exception) {
                                // 如果生成标题失败，回退到简单截取
                                Log.e("ConversationLogic", "❌ [Auto-Rename] AI生成标题失败: ${e.message}", e)
                                val fallbackTitle = inputContent.take(20).trim()
                                if (fallbackTitle.isNotBlank()) {
                                    withContext(Dispatchers.Main) {
                                        // 更新UI显示
                                        uiState.channelName = fallbackTitle
                                        // 持久化标题到数据库
                                        onRenameSession(sessionId, fallbackTitle)
                                        onSessionUpdated(sessionId)
                                        Log.d("ConversationLogic", "✅ [Auto-Rename] 回退标题完成: $fallbackTitle")
                                    }
                                }
                            }
                        }
                    } else {
                        // 如果没有提供GenerateChatNameUseCase，使用简单的截取方式
                        val newTitle = inputContent.take(20).trim()
                        if (newTitle.isNotBlank()) {
                            onRenameSession(sessionId, newTitle)
                            onSessionUpdated(sessionId)
                            Log.d("ConversationLogic", "✅ [Auto-Rename] 简单标题完成，已调用 onSessionUpdated")
                        }
                    }
                }
            }
        }

        // 1. 先设置加载状态，确保 UI 立即显示加载动画
        withContext(Dispatchers.Main) {
            uiState.isGenerating = true
        }

        // 2. Save User Message to Repository
        // 注意：用户消息的保存现在由 SendMessageUseCase 负责，这里不再重复保存
        val userMessageTimestamp = System.currentTimeMillis()
        if (!isRetry && !isAutoTriggered) {
            // 清空选中的图片URI（保留UI状态，但不处理图片功能）
            uiState.selectedImageUri = null
        }

        // 3. Call LLM or Image Generation
        if (providerSetting != null && model != null) {
            try {
                
                if (model.type == ModelType.IMAGE) {
                    imageGenerationHandler.generateImage(providerSetting, model, inputContent)
                    return
                }

                // Construct Messages (先搜索 top-k，这会计算 embedding)
                val constructionResult = MessageConstructionHelper.constructMessages(
                    uiState = uiState,
                    authorMe = authorMe,
                    inputContent = inputContent,
                    isAutoTriggered = isAutoTriggered,
                    activeAgent = uiState.activeAgent,
                    retrieveKnowledge = retrieveKnowledge,
                    context = context,
                    messageRepository = messageRepository,
                    sessionId = sessionId,
                    computeEmbeddingUseCase = computeEmbeddingUseCase,
                    searchEmbeddingUseCase = searchEmbeddingUseCase,
                    topK = embeddingTopK
                )
                
                val messagesToSend = constructionResult.messages
                val computedEmbedding = constructionResult.computedEmbedding

                val params = TextGenerationParams(
                    model = model,
                    temperature = uiState.temperature,
                    maxTokens = uiState.maxTokens
                )

                val historyChat: List<ChatDataItem> = messagesToSend.dropLast(1).map { message ->
                    MessageConstructionHelper.toChatDataItem(message)
                }
                val userMessage: ChatDataItem = MessageConstructionHelper.toChatDataItem(messagesToSend.last())

                logAllMessagesToSend(
                    sessionId = sessionId,
                    model = model,
                    params = params,
                    messagesToSend = messagesToSend,
                    historyChat = historyChat,
                    userMessage = userMessage,
                    isAutoTriggered = isAutoTriggered,
                    loopCount = loopCount
                )

                // 打印最终发送给模型的完整消息
                Log.d("ConversationLogic", "=".repeat(100))
                Log.d("ConversationLogic", "📤 最终发送给模型的消息 (共 ${messagesToSend.size} 条):")
                Log.d("ConversationLogic", "模型: ${model.modelId}, 会话ID: $sessionId")
                messagesToSend.forEachIndexed { index, message ->
                    val roleName = message.role.name
                    val contentBuilder = StringBuilder()
                    
                    message.parts.forEach { part ->
                        when (part) {
                            is com.example.star.aiwork.ui.ai.UIMessagePart.Text -> {
                                val text = part.text
                                contentBuilder.append(text)
                            }
                            is com.example.star.aiwork.ui.ai.UIMessagePart.Image -> {
                                contentBuilder.append("\n[图片: ${part.url.take(100)}${if (part.url.length > 100) "..." else ""}]")
                            }
                            else -> {
                                contentBuilder.append("\n[其他类型: ${part::class.simpleName}]")
                            }
                        }
                    }
                    
                    val content = contentBuilder.toString().trim()
                    val displayContent = if (content.length > 500) {
                        content.take(500) + "... [已截断，总长度: ${content.length}]"
                    } else {
                        content
                    }
                    Log.d("ConversationLogic", "")
                    Log.d("ConversationLogic", "  [${index + 1}] $roleName:")
                    Log.d("ConversationLogic", "    $displayContent")
                }
                Log.d("ConversationLogic", "=".repeat(100))

                val sendResult = sendMessageUseCase(
                    sessionId = sessionId,
                    userMessage = userMessage,
                    history = historyChat,
                    providerSetting = providerSetting,
                    params = params
                )

                // 使用 SendMessageUseCase 返回的 ASSISTANT 消息ID
                // 业务逻辑已统一在 SendMessageUseCase 中处理
                currentStreamingMessageId = sendResult.assistantMessageId

                // 保存 taskId 到 uiState 中，这样即使 ConversationLogic 重新创建也能恢复
                withContext(Dispatchers.Main) {
                    uiState.activeTaskId = sendResult.taskId
                }
                isCancelled = false
                
                // 异步检查是否需要保存记忆（使用已计算的 embedding，避免重复计算）
                // 注意：processMemoryIfNeededWithEmbedding 内部已经使用 withContext(Dispatchers.IO)
                if (!isAutoTriggered && computedEmbedding != null) {
                    streamingScope.launch {
                        memoryTriggerFilter.processMemoryIfNeededWithEmbedding(inputContent, computedEmbedding)
                    }
                }

                // Streaming Response Handling
                val fullResponse = streamingResponseHandler.handleStreaming(
                    scope = streamingScope,
                    stream = sendResult.stream,
                    isCancelledCheck = { isCancelled },
                    onJobCreated = { job, hintJob ->
                        streamingJob = job
                        hintTypingJob = hintJob
                        // 注册任务到任务管理器，以便在 ConversationLogic 重新创建后仍能取消
                        taskManager?.registerTasks(sessionId, job, hintJob)
                    }
                )

                // Clear Jobs references after completion
                streamingJob = null
                hintTypingJob = null
                taskManager?.removeTasks(sessionId)
                // 清除活跃任务ID
                withContext(Dispatchers.Main) {
                    uiState.activeTaskId = null
                }

            } catch (e: Exception) {
                handleError(e, inputContent, providerSetting, model, isAutoTriggered, loopCount, retrieveKnowledge, isRetry)
            }
        } else {
            withContext(Dispatchers.IO) {
                saveMessageToRepository(Message("System", "No AI Provider configured.", timeNow))
            }
            uiState.isGenerating = false
        }
    }

    private suspend fun handleError(
        e: Exception,
        inputContent: String,
        providerSetting: ProviderSetting?,
        model: Model?,
        isAutoTriggered: Boolean,
        loopCount: Int,
        retrieveKnowledge: suspend (String) -> String,
        isRetry: Boolean
    ) {
        Log.e("ConversationLogic", "❌ handleError triggered: ${e.javaClass.simpleName} - ${e.message}", e)

        if (e is CancellationException || e is LlmError.CancelledError) {
            Log.d("ConversationLogic", "⚠️ Error is cancellation related, ignoring.")
            // 更新消息状态为完成（如果存在流式消息）
            val messageId = currentStreamingMessageId
            if (messageId != null) {
                withContext(Dispatchers.IO) {
                    updateMessageInRepository(messageId, messageRepository?.getMessage(messageId)?.content ?: "", isLoading = false)
                }
            }
            withContext(Dispatchers.Main) {
                uiState.activeTaskId = null
                uiState.isGenerating = false
            }
            // 清除任务管理器中的任务引用
            taskManager?.removeTasks(sessionId)
            return
        }

        Log.d("ConversationLogic", "🔍 Checking fallback eligibility: isRetry=$isRetry, enabled=${uiState.isFallbackEnabled}")

        // Fallback logic
        if (!isRetry && // 仅在尚未重试过的情况下尝试兜底
            uiState.isFallbackEnabled &&
            uiState.fallbackProviderId != null &&
            uiState.fallbackModelId != null
        ) {
            Log.d("ConversationLogic", "🔍 Fallback config found: providerId=${uiState.fallbackProviderId}, modelId=${uiState.fallbackModelId}")
            
            val providers = getProviderSettings()
            val fallbackProvider = providers.find { it.id == uiState.fallbackProviderId }
            val fallbackModel = fallbackProvider?.models?.find { it.id == uiState.fallbackModelId }
                ?: fallbackProvider?.models?.find { it.modelId == uiState.fallbackModelId }

            // 避免在当前已经是兜底配置的情况下陷入死循环（虽然!isRetry已经能大部分避免，但双重保险更好）
            val isSameAsCurrent = providerSetting?.id == uiState.fallbackProviderId && 
                (model?.id == fallbackModel?.id)

            Log.d("ConversationLogic", "🔍 Fallback candidates: provider=${fallbackProvider?.name}, model=${fallbackModel?.displayName}")
            Log.d("ConversationLogic", "🔍 isSameAsCurrent=$isSameAsCurrent (currentProvider=${providerSetting?.id}, currentModel=${model?.id})")

            if (fallbackProvider != null && fallbackModel != null && !isSameAsCurrent) {
                Log.i("ConversationLogic", "✅ Triggering configured fallback to ${fallbackProvider.name}...")
                withContext(Dispatchers.IO) {
                    val messageId = currentStreamingMessageId
                    if (messageId != null) {
                        updateMessageInRepository(messageId, messageRepository?.getMessage(messageId)?.content ?: "", isLoading = false)
                    }
                    saveMessageToRepository(
                        Message("System", "Request failed (${e.message}). Fallback to ${fallbackProvider.name} (${fallbackModel.displayName})...", timeNow)
                    )
                }
                processMessage(
                    inputContent = inputContent,
                    providerSetting = fallbackProvider,
                    model = fallbackModel,
                    isAutoTriggered = isAutoTriggered,
                    loopCount = loopCount,
                    retrieveKnowledge = retrieveKnowledge,
                    isRetry = true
                )
                return
            } else {
                Log.w("ConversationLogic", "⚠️ Fallback skipped: Provider/Model not found or same as current.")
            }
        } else {
            Log.d("ConversationLogic", "Skipping configured fallback (retry or disabled or missing config).")
        }

        Log.e("ConversationLogic", "❌ No fallback triggered. Displaying error message.")
        withContext(Dispatchers.IO) {
            // 如果是重试产生的空消息，删除它
            val messageId = currentStreamingMessageId
            if (messageId != null) {
                val existingMessage = messageRepository?.getMessage(messageId)
                if (existingMessage != null && existingMessage.content.isBlank()) {
                    messageRepository?.deleteMessage(messageId)
                } else {
                    // 更新消息状态为完成
                    updateMessageInRepository(messageId, existingMessage?.content ?: "", isLoading = false)
                }
            }
            
            val errorMessage = getErrorMessage(e)
            saveMessageToRepository(Message("System", errorMessage, timeNow))
        }
        withContext(Dispatchers.Main) {
            uiState.activeTaskId = null
            uiState.isGenerating = false
        }
        // 清除任务管理器中的任务引用
        taskManager?.removeTasks(sessionId)
        e.printStackTrace()
    }
    
    /**
     * 回滚最后一条助手消息并重新生成
     */
    suspend fun rollbackAndRegenerate(
        providerSetting: ProviderSetting?,
        model: Model?,
        retrieveKnowledge: suspend (String) -> String = { "" }
    ) {
        rollbackHandler.rollbackAndRegenerate(
            providerSetting = providerSetting,
            model = model,
            scope = streamingScope,
            isCancelledCheck = { isCancelled },
            onJobCreated = { job, hintJob ->
                streamingJob = job
                hintTypingJob = hintJob
                // 注册任务到任务管理器
                taskManager?.registerTasks(sessionId, job, hintJob)
            },
            onTaskIdUpdated = { taskId ->
                // 保存 taskId 到 uiState 中（在挂起函数回调中，可以直接使用 withContext）
                withContext(Dispatchers.Main) {
                    uiState.activeTaskId = taskId
                }
            }
        )
    }
}
