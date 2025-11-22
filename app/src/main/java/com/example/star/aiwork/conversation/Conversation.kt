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

@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.star.aiwork.conversation

import android.Manifest
import android.content.ClipDescription
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.paddingFrom
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.star.aiwork.FunctionalityNotAvailablePopup
import com.example.star.aiwork.R
import com.example.star.aiwork.ai.core.MessageRole
import com.example.star.aiwork.ai.provider.ProviderSetting
import com.example.star.aiwork.ai.provider.TextGenerationParams
import com.example.star.aiwork.ai.provider.providers.OpenAIProvider
import com.example.star.aiwork.ai.ui.UIMessage
import com.example.star.aiwork.ai.ui.UIMessagePart
import com.example.star.aiwork.ai.util.AIRequestInterceptor
import com.example.star.aiwork.components.JetchatAppBar
import com.example.star.aiwork.data.exampleUiState
import com.example.star.aiwork.theme.JetchatTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import kotlin.math.roundToInt

/**
 * 对话屏幕的入口点。
 *
 * 这个可组合函数协调主要的对话 UI，包括：
 * - 显示消息历史记录。
 * - 处理用户输入（文本和语音）。
 * - 管理 AI 模型交互（文本生成）。
 * - 处理设置对话框和导航。
 *
 * @param uiState [ConversationUiState] 包含要显示的消息和 UI 状态。
 * @param navigateToProfile 请求导航到用户个人资料时的回调。
 * @param modifier 应用于此布局节点的 [Modifier]。
 * @param onNavIconPressed 当按下导航图标（汉堡菜单）时的回调。
 * @param providerSettings 可用的 AI 提供商设置列表。
 * @param temperature 当前的 AI 文本生成温度设置 (0.0 - 2.0)。
 * @param maxTokens 生成的最大 Token 数。
 * @param streamResponse 是否流式传输 AI 响应或等待完整响应。
 * @param onUpdateSettings 更新模型设置（温度、最大 Token 数、流式响应）的回调。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ConversationContent(
    uiState: ConversationUiState,
    navigateToProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
    onNavIconPressed: () -> Unit = { },
    providerSettings: List<ProviderSetting> = emptyList(),
    temperature: Float = 0.7f,
    maxTokens: Int = 2000,
    streamResponse: Boolean = true,
    onUpdateSettings: (Float, Int, Boolean) -> Unit = { _, _, _ -> }
) {
    val authorMe = stringResource(R.string.author_me)
    val timeNow = stringResource(id = R.string.now)
    val context = LocalContext.current

    // 列表滚动和顶部应用栏行为的状态
    val scrollState = rememberLazyListState()
    val topBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(topBarState)
    val scope = rememberCoroutineScope()
    
    // 显示模型设置对话框的状态
    var showSettingsDialog by remember { mutableStateOf(false) }
    
    // 将从 ViewModel 传递的参数与 UiState 同步
    // 这确保了 UI 反映持久化的设置
    LaunchedEffect(temperature, maxTokens, streamResponse) {
        uiState.temperature = temperature
        uiState.maxTokens = maxTokens
        uiState.streamResponse = streamResponse
    }

    // 拖放视觉状态
    var background by remember {
        mutableStateOf(Color.Transparent)
    }

    var borderStroke by remember {
        mutableStateOf(Color.Transparent)
    }
    
    // 如果请求，显示模型设置对话框
    if (showSettingsDialog) {
        ModelSettingsDialog(
            uiState = uiState,
            onDismissRequest = { 
                // 当对话框关闭时保存设置
                onUpdateSettings(uiState.temperature, uiState.maxTokens, uiState.streamResponse)
                showSettingsDialog = false 
            }
        )
    }

    // 拖放回调处理
    val dragAndDropCallback = remember {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val clipData = event.toAndroidDragEvent().clipData

                if (clipData.itemCount < 1) {
                    return false
                }

                // 将拖放的文本添加为新消息
                uiState.addMessage(
                    Message(authorMe, clipData.getItemAt(0).text.toString(), timeNow),
                )

                return true
            }

            override fun onStarted(event: DragAndDropEvent) {
                super.onStarted(event)
                borderStroke = Color.Red
            }

            override fun onEntered(event: DragAndDropEvent) {
                super.onEntered(event)
                background = Color.Red.copy(alpha = .3f)
            }

            override fun onExited(event: DragAndDropEvent) {
                super.onExited(event)
                background = Color.Transparent
            }

            override fun onEnded(event: DragAndDropEvent) {
                super.onEnded(event)
                background = Color.Transparent
                borderStroke = Color.Transparent
            }
        }
    }

    // 初始化带有 OkHttp 客户端的 OpenAI 提供商
    val client = remember {
        OkHttpClient.Builder()
            .addInterceptor(AIRequestInterceptor())
            .build()
    }
    val provider = remember { OpenAIProvider(client) }
    
    // 选择第一个可用的提供商和模型进行演示
    // 在实际应用中，这应该由用户选择
    val providerSetting = remember(providerSettings) { providerSettings.firstOrNull() }
    val model = remember(providerSetting) { providerSetting?.models?.firstOrNull() }

    // 初始化用于语音转文本的音频录制器和 WebSocket
    val audioRecorder = remember { AudioRecorder(context) }
    
    // 跟踪挂起的部分文本长度，以便在实时转录期间正确替换它
    var lastPartialLength by remember { mutableIntStateOf(0) }
    
    // 处理 ASR 结果的转录监听器
    val transcriptionListener = remember(scope, uiState) {
        object : YoudaoWebSocket.TranscriptionListener {
            override fun onResult(text: String, isFinal: Boolean) {
                scope.launch(Dispatchers.Main) {
                    val currentText = uiState.textFieldValue.text
                    
                    // 删除以前的部分文本（如果有），以便使用新的部分或最终结果进行更新
                    val safeCurrentText = if (currentText.length >= lastPartialLength) {
                        currentText.substring(0, currentText.length - lastPartialLength)
                    } else {
                        currentText // 通常不应该发生
                    }
                    
                    val newText = safeCurrentText + text
                    
                    uiState.textFieldValue = uiState.textFieldValue.copy(
                        text = newText,
                        selection = TextRange(newText.length)
                    )
                    
                    // 更新 lastPartialLength：如果是最终结果，重置为 0，否则存储当前长度
                    lastPartialLength = if (isFinal) 0 else text.length
                }
            }

            override fun onError(t: Throwable) {
                android.util.Log.e("Conversation", "ASR Error", t)
                scope.launch(Dispatchers.Main) {
                    Toast.makeText(context, "Error: ${t.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    val youdaoWebSocket = remember { YoudaoWebSocket(transcriptionListener) }

    // 音频录制的权限启动器
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            Toast.makeText(context, "需要录音权限才能使用语音功能", Toast.LENGTH_SHORT).show()
        }
    }

    // 在 dispose 时清理资源
    DisposableEffect(Unit) {
        onDispose {
            youdaoWebSocket.close()
            audioRecorder.stopRecording()
        }
    }

    Scaffold(
        topBar = {
            ChannelNameBar(
                channelName = uiState.channelName,
                channelMembers = uiState.channelMembers,
                onNavIconPressed = onNavIconPressed,
                scrollBehavior = scrollBehavior,
                onSettingsClicked = { showSettingsDialog = true }
            )
        },
        // 排除 ime 和导航栏内边距，以便由 UserInput composable 添加
        contentWindowInsets = ScaffoldDefaults
            .contentWindowInsets
            .exclude(WindowInsets.navigationBars)
            .exclude(WindowInsets.ime),
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { paddingValues ->
        Column(
            Modifier.fillMaxSize().padding(paddingValues)
                .background(color = background)
                .border(width = 2.dp, color = borderStroke)
                .dragAndDropTarget(shouldStartDragAndDrop = { event ->
                    event
                        .mimeTypes()
                        .contains(
                            ClipDescription.MIMETYPE_TEXT_PLAIN,
                        )
                }, target = dragAndDropCallback),
        ) {
            // 消息列表
            Messages(
                messages = uiState.messages,
                navigateToProfile = navigateToProfile,
                modifier = Modifier.weight(1f),
                scrollState = scrollState,
            )
            
            // 用户输入区域
            UserInput(
                onMessageSent = { content ->
                    // 1. 立即将用户消息添加到 UI
                    uiState.addMessage(
                        Message(authorMe, content, timeNow),
                    )
                    
                    // 2. 调用 LLM 获取响应
                    if (providerSetting != null && model != null) {
                        // 检查提供商是否兼容
                        if (providerSetting !is ProviderSetting.OpenAI) {
                             uiState.addMessage(
                                Message("System", "Currently only OpenAI compatible providers are supported.", timeNow)
                            )
                            return@UserInput
                        }
                        
                        scope.launch {
                            try {
                                // 为 AI 准备对话上下文
                                val history = uiState.messages.asReversed().map { msg ->
                                    UIMessage(
                                        role = if (msg.author == authorMe) MessageRole.USER else MessageRole.ASSISTANT,
                                        parts = listOf(UIMessagePart.Text(msg.content))
                                    )
                                }.takeLast(10) // 保持最近 10 条消息的简单上下文窗口

                                // 添加初始空 AI 消息占位符
                                uiState.addMessage(
                                    Message("AI", "", timeNow)
                                )

                                if (uiState.streamResponse) {
                                    // 调用 streamText 进行流式响应
                                    provider.streamText(
                                        providerSetting = providerSetting,
                                        messages = history,
                                        params = TextGenerationParams(
                                            model = model,
                                            temperature = uiState.temperature,
                                            maxTokens = uiState.maxTokens
                                        )
                                    ).collect { chunk ->
                                        withContext(Dispatchers.Main) {
                                            // 使用每个块更新 UI
                                            val deltaContent = chunk.choices.firstOrNull()?.delta?.toText() ?: ""
                                            if (deltaContent.isNotEmpty()) {
                                                uiState.appendToLastMessage(deltaContent)
                                            }
                                        }
                                    }
                                } else {
                                    // 调用 generateText 进行非流式响应
                                    val response = provider.generateText(
                                        providerSetting = providerSetting,
                                        messages = history,
                                        params = TextGenerationParams(
                                            model = model,
                                            temperature = uiState.temperature,
                                            maxTokens = uiState.maxTokens
                                        )
                                    )
                                    val content = response.choices.firstOrNull()?.message?.toText() ?: ""
                                    withContext(Dispatchers.Main) {
                                        if (content.isNotEmpty()) {
                                             uiState.appendToLastMessage(content)
                                        }
                                    }
                                }

                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    uiState.addMessage(
                                        Message("System", "Error: ${e.message}", timeNow)
                                    )
                                }
                                e.printStackTrace()
                            }
                        }
                    } else {
                         uiState.addMessage(
                            Message("System", "No AI Provider configured.", timeNow)
                        )
                    }
                },
                resetScroll = {
                    scope.launch {
                        scrollState.scrollToItem(0)
                    }
                },
                // 让此元素处理填充，以便将 elevation 显示在导航栏后面
                modifier = Modifier.navigationBarsPadding().imePadding(),
                onStartRecording = {
                    // 检查权限并开始录音
                    if (ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        uiState.isRecording = true
                        lastPartialLength = 0 // 重置部分长度跟踪器
                        scope.launch(Dispatchers.IO) {
                            youdaoWebSocket.connect()
                            audioRecorder.startRecording(
                                onAudioData = { data, len ->
                                    youdaoWebSocket.sendAudio(data, len)
                                },
                                onError = { e ->
                                    android.util.Log.e("Conversation", "AudioRecorder Error", e)
                                    scope.launch(Dispatchers.Main) {
                                        uiState.isRecording = false
                                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onStopRecording = {
                    // 停止录音并关闭 socket
                    if (uiState.isRecording) {
                        uiState.isRecording = false
                        audioRecorder.stopRecording()
                        youdaoWebSocket.close()
                    }
                },
                isRecording = uiState.isRecording,
                textFieldValue = uiState.textFieldValue,
                onTextChanged = { uiState.textFieldValue = it }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelNameBar(
    channelName: String,
    channelMembers: Int,
    modifier: Modifier = Modifier,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    onNavIconPressed: () -> Unit = { },
    onSettingsClicked: () -> Unit = { }
) {
    var functionalityNotAvailablePopupShown by remember { mutableStateOf(false) }
    if (functionalityNotAvailablePopupShown) {
        FunctionalityNotAvailablePopup { functionalityNotAvailablePopupShown = false }
    }
    JetchatAppBar(
        modifier = modifier,
        scrollBehavior = scrollBehavior,
        onNavIconPressed = onNavIconPressed,
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // 频道名称
                Text(
                    text = channelName,
                    style = MaterialTheme.typography.titleMedium,
                )
                // 成员数量
                Text(
                    text = stringResource(R.string.members, channelMembers),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        actions = {
            // 设置图标
            IconButton(onClick = onSettingsClicked) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    contentDescription = "Settings"
                )
            }
            // 搜索图标
            Icon(
                painterResource(id = R.drawable.ic_search),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clickable(onClick = { functionalityNotAvailablePopupShown = true })
                    .padding(horizontal = 12.dp, vertical = 16.dp)
                    .height(24.dp),
                contentDescription = stringResource(id = R.string.search),
            )
            // 信息图标
            Icon(
                painterResource(id = R.drawable.ic_info),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clickable(onClick = { functionalityNotAvailablePopupShown = true })
                    .padding(horizontal = 12.dp, vertical = 16.dp)
                    .height(24.dp),
                contentDescription = stringResource(id = R.string.info),
            )
        },
    )
}

/**
 * 配置 AI 模型设置的对话框。
 *
 * 允许用户调整：
 * - Temperature (温度，创造性 vs 精确性)
 * - Max Tokens (最大 Token 数，响应长度)
 * - Stream Response (流式响应，启用/禁用流式传输)
 */
@Composable
fun ModelSettingsDialog(
    uiState: ConversationUiState,
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = "Model Settings",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 温度设置滑块
                Text(
                    text = "Temperature",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Precise",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = String.format("%.1f", uiState.temperature),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Creative",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Slider(
                    value = uiState.temperature,
                    onValueChange = { uiState.temperature = it },
                    valueRange = 0f..2f,
                    steps = 19,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 最大 Token 数设置滑块
                Text(
                    text = "Max Tokens",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                 Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Short",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${uiState.maxTokens}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Long",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Slider(
                    value = uiState.maxTokens.toFloat(),
                    onValueChange = { uiState.maxTokens = it.roundToInt() },
                    valueRange = 100f..4096f,
                    steps = 39, // (4096-100)/100 约等于 40 步
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 流式响应开关
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Stream Response",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Switch(
                        checked = uiState.streamResponse,
                        onCheckedChange = { uiState.streamResponse = it }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismissRequest
            ) {
                Text("Done")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    )
}

const val ConversationTestTag = "ConversationTestTag"

@Composable
fun Messages(messages: List<Message>, navigateToProfile: (String) -> Unit, scrollState: LazyListState, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    Box(modifier = modifier) {

        val authorMe = stringResource(id = R.string.author_me)
        LazyColumn(
            reverseLayout = true,
            state = scrollState,
            modifier = Modifier
                .testTag(ConversationTestTag)
                .fillMaxSize(),
        ) {
            for (index in messages.indices) {
                val prevAuthor = messages.getOrNull(index - 1)?.author
                val nextAuthor = messages.getOrNull(index + 1)?.author
                val content = messages[index]
                val isFirstMessageByAuthor = prevAuthor != content.author
                val isLastMessageByAuthor = nextAuthor != content.author

                // 为了简单起见，硬编码日期分隔线
                if (index == messages.size - 1) {
                    item {
                        DayHeader("20 Aug")
                    }
                } else if (index == 2) {
                    item {
                        DayHeader("Today")
                    }
                }

                item {
                    Message(
                        onAuthorClick = { name -> navigateToProfile(name) },
                        msg = content,
                        isUserMe = content.author == authorMe,
                        isFirstMessageByAuthor = isFirstMessageByAuthor,
                        isLastMessageByAuthor = isLastMessageByAuthor,
                    )
                }
            }
        }
        // 当用户滚动超过阈值时显示跳转到底部按钮。
        // 转换为像素：
        val jumpThreshold = with(LocalDensity.current) {
            JumpToBottomThreshold.toPx()
        }

        // 如果第一个可见项不是第一个，或者偏移量大于阈值，则显示该按钮。
        val jumpToBottomButtonEnabled by remember {
            derivedStateOf {
                scrollState.firstVisibleItemIndex != 0 ||
                    scrollState.firstVisibleItemScrollOffset > jumpThreshold
            }
        }

        JumpToBottom(
            // 仅当滚动条不在底部时显示
            enabled = jumpToBottomButtonEnabled,
            onClicked = {
                scope.launch {
                    scrollState.animateScrollToItem(0)
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
fun Message(
    onAuthorClick: (String) -> Unit,
    msg: Message,
    isUserMe: Boolean,
    isFirstMessageByAuthor: Boolean,
    isLastMessageByAuthor: Boolean,
) {
    val borderColor = if (isUserMe) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.tertiary
    }

    val spaceBetweenAuthors = if (isLastMessageByAuthor) Modifier.padding(top = 8.dp) else Modifier
    Row(modifier = spaceBetweenAuthors) {
        if (isLastMessageByAuthor) {
            // 头像
            Image(
                modifier = Modifier
                    .clickable(onClick = { onAuthorClick(msg.author) })
                    .padding(horizontal = 16.dp)
                    .size(42.dp)
                    .border(1.5.dp, borderColor, CircleShape)
                    .border(3.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    .clip(CircleShape)
                    .align(Alignment.Top),
                painter = painterResource(id = msg.authorImage),
                contentScale = ContentScale.Crop,
                contentDescription = null,
            )
        } else {
            // 头像下方的空间
            Spacer(modifier = Modifier.width(74.dp))
        }
        AuthorAndTextMessage(
            msg = msg,
            isUserMe = isUserMe,
            isFirstMessageByAuthor = isFirstMessageByAuthor,
            isLastMessageByAuthor = isLastMessageByAuthor,
            authorClicked = onAuthorClick,
            modifier = Modifier
                .padding(end = 16.dp)
                .weight(1f),
        )
    }
}

@Composable
fun AuthorAndTextMessage(
    msg: Message,
    isUserMe: Boolean,
    isFirstMessageByAuthor: Boolean,
    isLastMessageByAuthor: Boolean,
    authorClicked: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        if (isLastMessageByAuthor) {
            AuthorNameTimestamp(msg)
        }
        ChatItemBubble(msg, isUserMe, authorClicked = authorClicked)
        if (isFirstMessageByAuthor) {
            // 下一个作者之前的最后一个气泡
            Spacer(modifier = Modifier.height(8.dp))
        } else {
            // 气泡之间
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun AuthorNameTimestamp(msg: Message) {
    // 为辅助功能合并作者和时间戳。
    Row(modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Text(
            text = msg.author,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .alignBy(LastBaseline)
                .paddingFrom(LastBaseline, after = 8.dp), // 距离第一个气泡的空间
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = msg.timestamp,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.alignBy(LastBaseline),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val ChatBubbleShape = RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp)

@Composable
fun DayHeader(dayString: String) {
    Row(
        modifier = Modifier
            .padding(vertical = 8.dp, horizontal = 16.dp)
            .height(16.dp),
    ) {
        DayHeaderLine()
        Text(
            text = dayString,
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DayHeaderLine()
    }
}

@Composable
private fun RowScope.DayHeaderLine() {
    Divider(
        modifier = Modifier
            .weight(1f)
            .align(Alignment.CenterVertically),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
    )
}

@Composable
fun ChatItemBubble(message: Message, isUserMe: Boolean, authorClicked: (String) -> Unit) {

    val backgroundBubbleColor = if (isUserMe) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    Column {
        Surface(
            color = backgroundBubbleColor,
            shape = ChatBubbleShape,
        ) {
            ClickableMessage(
                message = message,
                isUserMe = isUserMe,
                authorClicked = authorClicked,
            )
        }

        message.image?.let {
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                color = backgroundBubbleColor,
                shape = ChatBubbleShape,
            ) {
                Image(
                    painter = painterResource(it),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(160.dp),
                    contentDescription = stringResource(id = R.string.attached_image),
                )
            }
        }
    }
}

@Composable
fun ClickableMessage(message: Message, isUserMe: Boolean, authorClicked: (String) -> Unit) {
    val uriHandler = LocalUriHandler.current

    val styledMessage = messageFormatter(
        text = message.content,
        primary = isUserMe,
    )

    ClickableText(
        text = styledMessage,
        style = MaterialTheme.typography.bodyLarge.copy(color = LocalContentColor.current),
        modifier = Modifier.padding(16.dp),
        onClick = {
            styledMessage
                .getStringAnnotations(start = it, end = it)
                .firstOrNull()
                ?.let { annotation ->
                    when (annotation.tag) {
                        SymbolAnnotationType.LINK.name -> uriHandler.openUri(annotation.item)
                        SymbolAnnotationType.PERSON.name -> authorClicked(annotation.item)
                        else -> Unit
                    }
                }
        },
    )
}

@Preview
@Composable
fun ConversationPreview() {
    JetchatTheme {
        ConversationContent(
            uiState = exampleUiState,
            navigateToProfile = { },
        )
    }
}

@Preview
@Composable
fun ChannelBarPrev() {
    JetchatTheme {
        ChannelNameBar(channelName = "composers", channelMembers = 52)
    }
}

@Preview
@Composable
fun DayHeaderPrev() {
    DayHeader("Aug 6")
}

private val JumpToBottomThreshold = 56.dp
