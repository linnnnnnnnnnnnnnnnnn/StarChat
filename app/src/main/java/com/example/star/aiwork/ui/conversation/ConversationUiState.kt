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

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.text.input.TextFieldValue
import com.example.star.aiwork.R
import com.example.star.aiwork.domain.model.Model
import com.example.star.aiwork.domain.model.ProviderSetting
import com.example.star.aiwork.domain.usecase.GenerateChatNameUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * 对话屏幕的 UI 状态容器。
 *
 * 管理对话界面的所有可变状态，包括消息列表、输入框状态、录音状态以及 AI 模型参数。
 * 每个会话持有自己的协程作用域，用于管理该会话的所有协程（如 processMessage、rollbackAndRegenerate）。
 *
 * @property channelName 频道名称。
 * @property channelMembers 频道成员数量。
 * @property initialMessages 初始消息列表。
 * @property coroutineScope 该会话的协程作用域，用于管理所有与该会话相关的协程。
 */
class ConversationUiState(
    channelName: String,
    val channelMembers: Int,
    val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    // 频道名称使用可变状态，以便根据当前会话动态更新
    var channelName: String by mutableStateOf(channelName)

    // 注意：消息不再由 UI 状态管理，而是通过 UseCase 从 Repository 订阅
    // UI 层只负责展示从 Domain 层订阅的消息数据

    // 分页加载状态
    var isLoadingMore: Boolean by mutableStateOf(false)
    var allMessagesLoaded: Boolean by mutableStateOf(false)

    // AI 模型参数状态
    var temperature: Float by mutableFloatStateOf(0.7f)
    var maxTokens: Int by mutableIntStateOf(2000)
    var streamResponse: Boolean by mutableStateOf(true)

    // 兜底机制配置
    var isFallbackEnabled: Boolean by mutableStateOf(true)
    var fallbackProviderId: String? by mutableStateOf(null)
    var fallbackModelId: String? by mutableStateOf(null)

    // ====== 语音输入模式状态 ======
    var isVoiceMode: Boolean by mutableStateOf(false) // 是否处于语音输入模式（替换文本输入框为"按住说话"按钮）

    // 录音状态
    var isRecording: Boolean by mutableStateOf(false)
    var isTranscribing: Boolean by mutableStateOf(false) // 是否正在转换文字
    var pendingTranscription: String by mutableStateOf("") // 暂存转写文本（录音时实时显示）

    // 语音面板状态
    var voiceInputStage: VoiceInputStage by mutableStateOf(VoiceInputStage.IDLE) // 语音输入阶段
    var isCancelGesture: Boolean by mutableStateOf(false) // 是否处于取消手势状态（上滑）
    var currentVolume: Float by mutableFloatStateOf(0f) // 当前音量（用于波形动画）

    // AI 生成状态
    var isGenerating: Boolean by mutableStateOf(false) // 是否正在生成回答

    // 流式生成任务状态
    var activeTaskId: String? by mutableStateOf(null) // 当前活跃的流式生成任务ID

    // 输入框文本状态
    var textFieldValue: TextFieldValue by mutableStateOf(TextFieldValue())

    // 暂存选中的图片 URI
    var selectedImageUri: Uri? by mutableStateOf(null)

    /**
     * 用于生成预览卡片标题的UseCase
     */
    var generateChatNameUseCase: GenerateChatNameUseCase? = null

    /**
     * 当前活跃的Provider设置
     */
    var activeProviderSetting: ProviderSetting? = null

    /**
     * 当前活跃的Model
     */
    var activeModel: Model? = null

    // 注意：所有消息管理方法已移除
    // 消息的增删改查应该通过 UseCase 操作 Repository，UI 只负责订阅和展示

    /**
     * 取消该会话的所有协程。
     * 当会话被移除或切换时，应该调用此方法清理协程资源。
     */
    fun cancelAllCoroutines() {
        coroutineScope.cancel()
    }
}

/**
 * 消息数据模型。
 *
 * 表示聊天中的单条消息。
 * 标记为 @Immutable 以优化 Compose 重组性能。
 *
 * @property author 消息作者名称。
 * @property content 消息文本内容。
 * @property timestamp 消息时间戳字符串。
 * @property image 可选的附件图片资源 ID。
 * @property imageUrl 可选的附件图片 URI 字符串（用户上传或网络图片）。
 * @property authorImage 作者头像资源 ID。
 */
@Immutable
data class Message(
    val author: String,
    val content: String,
    val timestamp: String,
    val image: Int? = null,
    val imageUrl: String? = null,
    val authorImage: Int = if (author == "me") R.drawable.ali else R.drawable.someone_else,
    val isLoading: Boolean = false  // ✅ 新增：标记是否正在加载
)
