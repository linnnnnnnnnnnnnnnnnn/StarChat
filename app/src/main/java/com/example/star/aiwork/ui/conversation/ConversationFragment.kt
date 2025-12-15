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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.core.os.bundleOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.text.input.TextFieldValue
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.findNavController
import com.example.star.aiwork.ui.MainViewModel
import com.example.star.aiwork.R
import com.example.star.aiwork.domain.model.MessageEntity
import com.example.star.aiwork.domain.model.MessageRole
import com.example.star.aiwork.ui.theme.JetchatTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.star.aiwork.data.remote.StreamingChatRemoteDataSource
import com.example.star.aiwork.data.repository.AiRepositoryImpl
import com.example.star.aiwork.data.local.datasource.message.MessageLocalDataSourceImpl
import com.example.star.aiwork.data.local.datasource.session.SessionCacheDataSource
import com.example.star.aiwork.data.local.datasource.session.SessionLocalDataSourceImpl
import com.example.star.aiwork.data.repository.MessageRepositoryImpl
import com.example.star.aiwork.data.repository.SessionRepositoryImpl
import com.example.star.aiwork.infra.cache.SessionCacheDataSourceImpl
import com.example.star.aiwork.domain.usecase.GenerateChatNameUseCase
import com.example.star.aiwork.domain.usecase.PauseStreamingUseCase
import com.example.star.aiwork.domain.usecase.RollbackMessageUseCase
import com.example.star.aiwork.domain.usecase.SendMessageUseCase
import com.example.star.aiwork.domain.usecase.UpdateMessageUseCase
import com.example.star.aiwork.domain.usecase.SaveMessageUseCase
import com.example.star.aiwork.domain.usecase.embedding.ComputeEmbeddingUseCase
import com.example.star.aiwork.domain.usecase.embedding.FilterMemoryMessagesUseCase
import com.example.star.aiwork.domain.usecase.embedding.ProcessBufferFullUseCase
import com.example.star.aiwork.domain.usecase.embedding.SaveEmbeddingUseCase
import com.example.star.aiwork.domain.usecase.embedding.SearchEmbeddingUseCase
import com.example.star.aiwork.domain.usecase.embedding.ShouldSaveAsMemoryUseCase
import com.example.star.aiwork.domain.usecase.message.ConstructMessagesUseCase
import com.example.star.aiwork.domain.usecase.HandleErrorUseCase
import com.example.star.aiwork.data.repository.EmbeddingRepositoryImpl
import com.example.star.aiwork.data.local.EmbeddingDatabaseProvider
import com.example.star.aiwork.infra.embedding.EmbeddingService
import com.example.star.aiwork.infra.network.SseClient
import com.example.star.aiwork.infra.network.defaultOkHttpClient
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class ConversationFragment : Fragment() {

    private val activityViewModel: MainViewModel by activityViewModels()
    private val chatViewModel: ChatViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(inflater.context).apply {
            layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)

            setContent {
                val providerSettings by activityViewModel.providerSettings.collectAsStateWithLifecycle()
                val temperature by activityViewModel.temperature.collectAsStateWithLifecycle()
                val maxTokens by activityViewModel.maxTokens.collectAsStateWithLifecycle()
                val streamResponse by activityViewModel.streamResponse.collectAsStateWithLifecycle()
                val activeProviderId by activityViewModel.activeProviderId.collectAsStateWithLifecycle()
                val activeModelId by activityViewModel.activeModelId.collectAsStateWithLifecycle()
                
                // 收集兜底设置
                val isFallbackEnabled by activityViewModel.isFallbackEnabled.collectAsStateWithLifecycle()
                val fallbackProviderId by activityViewModel.fallbackProviderId.collectAsStateWithLifecycle()
                val fallbackModelId by activityViewModel.fallbackModelId.collectAsStateWithLifecycle()

                val context = LocalContext.current
                val scope = rememberCoroutineScope()

                val currentSession by chatViewModel.currentSession.collectAsStateWithLifecycle()
                val sessions by chatViewModel.sessions.collectAsStateWithLifecycle()
                val searchQuery by chatViewModel.searchQuery.collectAsStateWithLifecycle()
                val searchResults by chatViewModel.searchResults.collectAsStateWithLifecycle()

                // 获取或创建当前会话的 UI 状态
                val uiState = remember(currentSession?.id) {
                    currentSession?.let {
                        chatViewModel.getOrCreateSessionUiState(it.id, it.name)
                    } ?: ConversationUiState(
                        channelName = "新对话",
                        channelMembers = 1
                    )
                }

                val okHttpClient = remember { defaultOkHttpClient() }
                val sseClient = remember { SseClient(okHttpClient) }
                val remoteChatDataSource = remember { StreamingChatRemoteDataSource(sseClient) }
                val aiRepository = remember { AiRepositoryImpl(remoteChatDataSource, okHttpClient) }

                // Create DataSources
                val messageLocalDataSource = remember(context) { MessageLocalDataSourceImpl(context) }
                val sessionLocalDataSource = remember(context) { SessionLocalDataSourceImpl(context) }
                val sessionCacheDataSource = remember { SessionCacheDataSourceImpl() }
                val messageCacheDataSource = remember { com.example.star.aiwork.infra.cache.MessageCacheDataSourceImpl() }
                
                // Create Repositories
                val messageRepository = remember(messageCacheDataSource, messageLocalDataSource) { 
                    MessageRepositoryImpl(messageCacheDataSource, messageLocalDataSource) 
                }
                val sessionRepository = remember(sessionCacheDataSource, sessionLocalDataSource) {
                    SessionRepositoryImpl(sessionCacheDataSource, sessionLocalDataSource)
                }
                
                // sendMessageUseCase 在下方使用 constructMessagesUseCase 一并创建
                val pauseStreamingUseCase = remember(aiRepository) {
                    PauseStreamingUseCase(aiRepository)
                }
                val rollbackMessageUseCase = remember(aiRepository, messageRepository) {
                    RollbackMessageUseCase(aiRepository, messageRepository)
                }
                val generateChatNameUseCase = remember(aiRepository) {
                    GenerateChatNameUseCase(aiRepository)
                }
                val updateMessageUseCase = remember(messageRepository, sessionRepository) {
                    if (messageRepository != null && sessionRepository != null) {
                        UpdateMessageUseCase(messageRepository, sessionRepository)
                    } else {
                        null
                    }
                }
                val saveMessageUseCase = remember(messageRepository, sessionRepository) {
                    if (messageRepository != null && sessionRepository != null) {
                        SaveMessageUseCase(messageRepository, sessionRepository)
                    } else {
                        null
                    }
                }
                val shouldSaveAsMemoryUseCase = remember { ShouldSaveAsMemoryUseCase() }

                // 创建 Embedding 相关的 UseCase
                val embeddingService = remember(context) { EmbeddingService(context) }
                val embeddingRepository = remember(context) {
                    val database = EmbeddingDatabaseProvider.getDatabase(context)
                    EmbeddingRepositoryImpl(database.embeddingDao())
                }
                val computeEmbeddingUseCase = remember(embeddingService) {
                    ComputeEmbeddingUseCase(
                        embeddingService = embeddingService,
                        useRemote = false
                    )
                }
                val searchEmbeddingUseCase = remember(embeddingRepository) {
                    SearchEmbeddingUseCase(embeddingRepository)
                }
                val saveEmbeddingUseCase = remember(embeddingRepository) {
                    SaveEmbeddingUseCase(embeddingRepository)
                }
                val filterMemoryMessagesUseCase = remember(aiRepository) {
                    FilterMemoryMessagesUseCase(aiRepository)
                }
                val processBufferFullUseCase = remember(filterMemoryMessagesUseCase, saveEmbeddingUseCase) {
                    if (filterMemoryMessagesUseCase != null && saveEmbeddingUseCase != null) {
                        ProcessBufferFullUseCase(filterMemoryMessagesUseCase, saveEmbeddingUseCase)
                    } else {
                        null
                    }
                }
                val handleErrorUseCase = remember(messageRepository, updateMessageUseCase) {
                    if (messageRepository != null && updateMessageUseCase != null) {
                        HandleErrorUseCase(messageRepository, updateMessageUseCase)
                    } else {
                        null
                    }
                }
                val constructMessagesUseCase = remember(messageRepository, computeEmbeddingUseCase, searchEmbeddingUseCase) {
                    if (messageRepository != null && computeEmbeddingUseCase != null && searchEmbeddingUseCase != null) {
                        ConstructMessagesUseCase(
                            messageRepository = messageRepository,
                            computeEmbeddingUseCase = computeEmbeddingUseCase,
                            searchEmbeddingUseCase = searchEmbeddingUseCase
                        )
                    } else if (messageRepository != null) {
                        // 仅在需要时使用 embedding 能力，缺失时只使用历史上下文
                        ConstructMessagesUseCase(
                            messageRepository = messageRepository,
                            computeEmbeddingUseCase = null,
                            searchEmbeddingUseCase = null
                        )
                    } else {
                        // 在没有 repository 的情况下不会被调用，这里返回一个占位实现以避免 NPE
                        ConstructMessagesUseCase(
                            messageRepository = MessageRepositoryImpl(
                                messageCacheDataSource,
                                messageLocalDataSource
                            ),
                            computeEmbeddingUseCase = null,
                            searchEmbeddingUseCase = null
                        )
                    }
                }

                val sendMessageUseCase = remember(aiRepository, messageRepository, sessionRepository, scope, constructMessagesUseCase) {
                    SendMessageUseCase(aiRepository, messageRepository, sessionRepository, scope, constructMessagesUseCase)
                }

                // Create ObserveMessagesUseCase
                val observeMessagesUseCase = remember(messageRepository) {
                    com.example.star.aiwork.domain.usecase.message.ObserveMessagesUseCase(messageRepository)
                }

                // 从 UseCase 订阅当前会话的消息
                val messagesFlow = remember(currentSession?.id, observeMessagesUseCase) {
                    currentSession?.id?.let { sessionId ->
                        observeMessagesUseCase(sessionId)
                    } ?: kotlinx.coroutines.flow.flowOf(emptyList())
                }
                val messagesFromUseCase by messagesFlow.collectAsStateWithLifecycle(initialValue = emptyList())

                // 转换消息实体为 UI 模型
                val convertedMessages = remember(messagesFromUseCase, uiState.temporaryErrorMessages) {
                    val dbMessages = messagesFromUseCase.map { entity ->
                        convertMessageEntityToMessage(entity)
                    }
                    // 合并数据库消息和临时错误消息（临时错误消息显示在最后）
                    dbMessages + uiState.temporaryErrorMessages
                }

                val conversationLogic = remember(
                    currentSession?.id,
                    uiState,
                    chatViewModel,
                    providerSettings, // Add providerSettings as a dependency
                    computeEmbeddingUseCase,
                    saveEmbeddingUseCase,
                    filterMemoryMessagesUseCase,
                    processBufferFullUseCase,
                    activeProviderId,
                    activeModelId,
                    messageRepository,
                    sessionRepository,
                    updateMessageUseCase,
                    handleErrorUseCase
                ) {
                    ConversationLogic(
                        uiState = uiState,
                        context = context,
                        authorMe = "me",
                        timeNow = "Now",
                        sendMessageUseCase = sendMessageUseCase,
                        pauseStreamingUseCase = pauseStreamingUseCase,
                        rollbackMessageUseCase = rollbackMessageUseCase,
                        generateChatNameUseCase = generateChatNameUseCase,
                        updateMessageUseCase = updateMessageUseCase,
                        saveMessageUseCase = saveMessageUseCase,
                        shouldSaveAsMemoryUseCase = shouldSaveAsMemoryUseCase,
                        sessionId = currentSession?.id ?: UUID.randomUUID().toString(),
                        getProviderSettings = { providerSettings },
                        messageRepository = messageRepository,
                        sessionRepository = sessionRepository,
                        onRenameSession = { sessionId, newName ->
                            chatViewModel.renameSession(sessionId, newName)
                        },
                        onPersistNewChatSession = { sessionId ->
                            // 如果当前没有会话，先创建临时会话
                            if (currentSession == null) {
                                // 创建临时会话，使用传入的 sessionId
                                // 注意：这里我们需要创建一个临时会话并标记为新会话
                                val sessionName = "新聊天"
                                chatViewModel.createTemporarySession(sessionName)
                            }
                            chatViewModel.persistNewChatSession(sessionId)
                        },
                        isNewChat = { sessionId ->
                            chatViewModel.isNewChat(sessionId)
                        },
                        onSessionUpdated = { sessionId ->
                            // 刷新会话列表，让 drawer 中的会话按 updatedAt 排序
                            scope.launch { chatViewModel.refreshSessions() }
                        },
                        taskManager = chatViewModel.streamingTaskManager,
                        computeEmbeddingUseCase = computeEmbeddingUseCase,
                        saveEmbeddingUseCase = saveEmbeddingUseCase,
                        filterMemoryMessagesUseCase = filterMemoryMessagesUseCase,
                        processBufferFullUseCase = processBufferFullUseCase,
                        getProviderSetting = {
                            providerSettings.find { it.id == activeProviderId } ?: providerSettings.firstOrNull()
                        },
                        getModel = {
                            val provider = providerSettings.find { it.id == activeProviderId } ?: providerSettings.firstOrNull()
                            provider?.models?.find { it.modelId == activeModelId } ?: provider?.models?.firstOrNull()
                        },
                        handleErrorUseCase = handleErrorUseCase
                    )
                }

                // 当会话切换时，更新 UI 状态的 channelName
                LaunchedEffect(currentSession?.id, currentSession?.name) {
                    currentSession?.let { session ->
                        uiState.channelName = session.name.ifBlank { "新对话" }
                    }
                }


                JetchatTheme {
                    ConversationContent(
                        uiState = uiState,
                        logic = conversationLogic,
                        messages = convertedMessages,  // 从 UseCase 订阅的消息
                        navigateToProfile = { user ->
                            val bundle = bundleOf("userId" to user)
                            findNavController().navigate(
                                R.id.nav_profile,
                                bundle,
                            )
                        },
                        onNavIconPressed = {
                            activityViewModel.openDrawer()
                        },
                        providerSettings = providerSettings,
                        activeProviderId = activeProviderId,
                        activeModelId = activeModelId,
                        temperature = temperature,
                        maxTokens = maxTokens,
                        streamResponse = streamResponse,
                        isFallbackEnabled = isFallbackEnabled,
                        fallbackProviderId = fallbackProviderId,
                        fallbackModelId = fallbackModelId,
                        onUpdateSettings = { temp, tokens, stream ->
                            activityViewModel.updateTemperature(temp)
                            activityViewModel.updateMaxTokens(tokens)
                            activityViewModel.updateStreamResponse(stream)
                        },
                        onUpdateFallbackSettings = { enabled, pId, mId ->
                            activityViewModel.updateFallbackEnabled(enabled)
                            activityViewModel.updateFallbackModel(pId, mId)
                        },
                        retrieveKnowledge = { query ->
                            activityViewModel.retrieveKnowledge(query)
                        },
                        currentSessionId = currentSession?.id,
                        searchQuery = searchQuery,
                        onSearchQueryChanged = { query -> chatViewModel.searchSessions(query) },
                        searchResults = searchResults,
                        onSessionSelected = { session -> chatViewModel.selectSession(session) },
                        generateChatNameUseCase = generateChatNameUseCase,
                        onLoadMoreMessages = { chatViewModel.loadMoreMessages() }
                    )
                }
            }
        }


    private fun convertMessageEntityToMessage(entity: MessageEntity): Message {
        val author = when (entity.role) {
            MessageRole.USER -> "me"
            MessageRole.ASSISTANT -> "assistant"
            MessageRole.SYSTEM -> "system"
            MessageRole.TOOL -> "tool"
        }

        val timestamp = if (entity.createdAt > 0) {
            val dateFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            dateFormat.format(Date(entity.createdAt))
        } else {
            "Now"
        }

        // 将 MessageStatus 转换为 isLoading 字段
        // SENDING 状态表示正在等待模型回复，显示加载动画
        // 收到第一个chunk后，状态会更新为 STREAMING，加载动画消失
        val isLoading = entity.status == com.example.star.aiwork.domain.model.MessageStatus.SENDING

        return Message(
            author = author,
            content = entity.content,
            timestamp = timestamp,
            imageUrl = entity.metadata.localFilePath,
            isLoading = isLoading
        )
    }
}
