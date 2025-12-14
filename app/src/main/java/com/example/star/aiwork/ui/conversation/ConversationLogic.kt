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
import com.example.star.aiwork.domain.usecase.UpdateMessageUseCase
import com.example.star.aiwork.domain.usecase.SaveMessageUseCase
import com.example.star.aiwork.domain.repository.SessionRepository
import com.example.star.aiwork.domain.usecase.embedding.ComputeEmbeddingUseCase
import com.example.star.aiwork.domain.usecase.embedding.FilterMemoryMessagesUseCase
import com.example.star.aiwork.domain.usecase.embedding.ProcessBufferFullUseCase
import com.example.star.aiwork.domain.usecase.embedding.SaveEmbeddingUseCase
import com.example.star.aiwork.domain.usecase.embedding.SearchEmbeddingUseCase
import com.example.star.aiwork.domain.usecase.embedding.ShouldSaveAsMemoryUseCase
import com.example.star.aiwork.domain.usecase.message.GetHistoryMessagesUseCase
import com.example.star.aiwork.domain.usecase.HandleErrorUseCase
import com.example.star.aiwork.domain.usecase.ErrorHandlingResult
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
 * Includes sending messages to AI providers.
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
    private val updateMessageUseCase: UpdateMessageUseCase? = null,
    private val saveMessageUseCase: SaveMessageUseCase? = null,
    private val getHistoryMessagesUseCase: GetHistoryMessagesUseCase? = null,
    private val shouldSaveAsMemoryUseCase: ShouldSaveAsMemoryUseCase? = null,
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
    private val processBufferFullUseCase: ProcessBufferFullUseCase? = null,
    private val embeddingTopK: Int = 3,
    private val getProviderSetting: () -> ProviderSetting? = { null },
    private val getModel: () -> Model? = { null },
    private val handleErrorUseCase: HandleErrorUseCase? = null
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


    // Handlers
    private val imageGenerationHandler = ImageGenerationHandler(
        uiState = uiState,
        imageGenerationUseCase = imageGenerationUseCase,
        saveMessageUseCase = saveMessageUseCase ?: throw IllegalStateException("SaveMessageUseCase is required"),
        updateMessageUseCase = updateMessageUseCase ?: throw IllegalStateException("UpdateMessageUseCase is required"),
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
        saveMessageUseCase = saveMessageUseCase ?: throw IllegalStateException("SaveMessageUseCase is required"),
        messageRepository = messageRepository,
        streamingResponseHandler = streamingResponseHandler,
        sessionId = sessionId,
        authorMe = authorMe,
        timeNow = timeNow,
        onMessageIdCreated = { messageId -> currentStreamingMessageId = messageId }
    )

    // 创建 MemoryBuffer，当 buffer 满了时触发批量处理
    private val memoryBuffer = if (processBufferFullUseCase != null) {
        MemoryBuffer(maxSize = 5) { items ->
            handleBufferFull(items)
        }
    } else {
        null
    }

    private val memoryTriggerFilter = MemoryTriggerFilter(
        shouldSaveAsMemoryUseCase = shouldSaveAsMemoryUseCase ?: ShouldSaveAsMemoryUseCase(),
        computeEmbeddingUseCase = computeEmbeddingUseCase,
        saveEmbeddingUseCase = saveEmbeddingUseCase,
        memoryBuffer = memoryBuffer
    )

    /**
     * 处理 buffer 满了的情况
     * 委托给 ProcessBufferFullUseCase 处理
     */
    private suspend fun handleBufferFull(items: List<BufferedMemoryItem>) {
        val providerSetting = getProviderSetting()
        val model = getModel()
        
        if (processBufferFullUseCase == null || providerSetting == null || model == null) {
            return
        }

        // 转换 UI 层的 BufferedMemoryItem 到 domain 层的类型
        val domainItems = items.map { item ->
            ProcessBufferFullUseCase.BufferedMemoryItem(
                text = item.text,
                embedding = item.embedding
            )
        }
        
        // 委托给 use case 处理
        processBufferFullUseCase(domainItems, providerSetting, model)
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
                updateMessageUseCase?.invoke(
                    messageId = messageId,
                    content = currentContent,
                    status = MessageStatus.DONE,
                    updateSessionTimestamp = true
                )
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
        // 清除临时错误消息（用户发送新消息时，错误消息应该消失）
        withContext(Dispatchers.Main) {
            uiState.temporaryErrorMessages = emptyList()
        }
        
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
                    retrieveKnowledge = retrieveKnowledge,
                    context = context,
                    getHistoryMessagesUseCase = getHistoryMessagesUseCase,
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
            // 错误消息不保存到数据库，只添加到临时错误消息列表
            withContext(Dispatchers.Main) {
                uiState.temporaryErrorMessages = listOf(
                    Message("System", "No AI Provider configured.", timeNow)
                )
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
        // 使用 HandleErrorUseCase 处理错误
        val result = if (handleErrorUseCase != null) {
            handleErrorUseCase(
                error = e,
                currentMessageId = currentStreamingMessageId
            )
        } else {
            // 如果没有提供 UseCase，使用简单的错误处理（向后兼容）
            val errorMessage = getErrorMessage(e)
            ErrorHandlingResult.ShouldDisplayError(errorMessage, shouldDeleteMessage = false)
        }

        // 根据处理结果执行相应操作
        when (result) {
            is ErrorHandlingResult.Cancelled -> {
                // 取消异常，清除状态
                withContext(Dispatchers.Main) {
                    uiState.activeTaskId = null
                    uiState.isGenerating = false
                }
                taskManager?.removeTasks(sessionId)
            }

            is ErrorHandlingResult.ShouldDisplayError -> {
                // 需要显示错误消息
                val messageId = currentStreamingMessageId
                if (result.shouldDeleteMessage && messageId != null) {
                    // 删除空消息
                    withContext(Dispatchers.IO) {
                        messageRepository?.deleteMessage(messageId)
                    }
                }
                // 错误消息不保存到数据库，只添加到临时错误消息列表
                withContext(Dispatchers.Main) {
                    uiState.temporaryErrorMessages = listOf(
                        Message("System", result.errorMessage, timeNow)
                    )
                }
                withContext(Dispatchers.Main) {
                    uiState.activeTaskId = null
                    uiState.isGenerating = false
                }
                // 清除任务管理器中的任务引用
                taskManager?.removeTasks(sessionId)
                e.printStackTrace()
            }
        }
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
