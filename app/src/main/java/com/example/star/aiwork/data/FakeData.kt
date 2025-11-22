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

package com.example.star.aiwork.data

import com.example.star.aiwork.R
import com.example.star.aiwork.ai.provider.Model
import com.example.star.aiwork.ai.provider.ProviderSetting
import com.example.star.aiwork.conversation.ConversationUiState
import com.example.star.aiwork.conversation.Message
import com.example.star.aiwork.data.EMOJIS.EMOJI_CLOUDS
import com.example.star.aiwork.data.EMOJIS.EMOJI_FLAMINGO
import com.example.star.aiwork.data.EMOJIS.EMOJI_MELTING
import com.example.star.aiwork.data.EMOJIS.EMOJI_PINK_HEART
import com.example.star.aiwork.data.EMOJIS.EMOJI_POINTS
import com.example.star.aiwork.profile.ProfileScreenState

/**
 * 初始消息列表。
 * 包含用于演示的假对话数据。
 */
val initialMessages = listOf(
    Message(
        "me",
        "Check it out!",
        "8:07 PM",
    ),
    Message(
        "me",
        "Thank you!$EMOJI_PINK_HEART",
        "8:06 PM",
        R.drawable.sticker,
    ),
    Message(
        "Taylor Brooks",
        "You can use all the same stuff",
        "8:05 PM",
    ),
    Message(
        "Taylor Brooks",
        "@aliconors Take a look at the `Flow.collectAsStateWithLifecycle()` APIs",
        "8:05 PM",
    ),
    Message(
        "John Glenn",
        "Compose newbie as well $EMOJI_FLAMINGO, have you looked at the JetNews sample? " +
            "Most blog posts end up out of date pretty fast but this sample is always up to " +
            "date and deals with async data loading (it's faked but the same idea " +
            "applies) $EMOJI_POINTS https://goo.gle/jetnews",
        "8:04 PM",
    ),
    Message(
        "me",
        "Compose newbie: I’ve scourged the internet for tutorials about async data " +
            "loading but haven’t found any good ones $EMOJI_MELTING $EMOJI_CLOUDS. " +
            "What’s the recommended way to load async data and emit composable widgets?",
        "8:03 PM",
    ),
    Message(
        "Shangeeth Sivan",
        "Does anyone know about Glance Widgets its the new way to build widgets in Android!",
        "8:08 PM",
    ),
    Message(
        "Taylor Brooks",
        "Wow! I never knew about Glance Widgets when was this added to the android ecosystem",
        "8:10 PM",
    ),
    Message(
        "John Glenn",
        "Yeah its seems to be pretty new!",
        "8:12 PM",
    ),
)

/**
 * 未读消息列表（用于演示）。
 */
val unreadMessages = initialMessages.filter { it.author != "me" }

/**
 * 示例 UI 状态。
 */
val exampleUiState = ConversationUiState(
    initialMessages = initialMessages,
    channelName = "#composers",
    channelMembers = 42,
)

/**
 * 同事个人资料示例。
 */
val colleagueProfile = ProfileScreenState(
    userId = "12345",
    photo = R.drawable.someone_else,
    name = "Taylor Brooks",
    status = "Away",
    displayName = "taylor",
    position = "Senior Android Dev at Openlane",
    twitter = "twitter.com/taylorbrookscodes",
    timeZone = "12:25 AM local time (Eastern Daylight Time)",
    commonChannels = "2",
)

/**
 * "我" 的个人资料示例。
 */
val meProfile = ProfileScreenState(
    userId = "me",
    photo = R.drawable.ali,
    name = "Ali Conors",
    status = "Online",
    displayName = "aliconors",
    position = "Senior Android Dev at Yearin\nGoogle Developer Expert",
    twitter = "twitter.com/aliconors",
    timeZone = "In your timezone",
    commonChannels = null,
)

/**
 * 表情符号常量对象。
 * 包含各种 Android 版本和 Emoji 版本中引入的特殊字符。
 */
object EMOJIS {
    // EMOJI 15
    const val EMOJI_PINK_HEART = "\uD83E\uDE77"

    // EMOJI 14 🫠
    const val EMOJI_MELTING = "\uD83E\uDEE0"

    // ANDROID 13.1 😶‍🌫️
    const val EMOJI_CLOUDS = "\uD83D\uDE36\u200D\uD83C\uDF2B️"

    // ANDROID 12.0 🦩
    const val EMOJI_FLAMINGO = "\uD83E\uDDA9"

    // ANDROID 12.0  👉
    const val EMOJI_POINTS = " \uD83D\uDC49"
}

/**
 * 免费提供商配置列表。
 *
 * 包含默认配置的 AI 服务提供商，如 SiliconFlow 和 DeepSeek。
 * 这些配置用于演示目的，并在用户首次启动应用时作为默认设置加载。
 */
val freeProviders = listOf(
    ProviderSetting.OpenAI(
        id = "silicon_cloud",
        name = "SiliconFlow",
        baseUrl = "https://api.siliconflow.cn/v1",
        // 请在这里填入您的 SiliconFlow API Key
        apiKey = "sk-sjsubcwdyqrqwzuvaepkgciiwxupgjjulpwuynwrpjkpohgx",
        models = listOf(
            Model(
                modelId = "Qwen/Qwen2.5-7B-Instruct",
                displayName = "Qwen 2.5 7B"
            ),
            Model(
                modelId = "THUDM/glm-4-9b-chat",
                displayName = "GLM-4 9B"
            )
        )
    ),
    ProviderSetting.OpenAI(
        id = "deepseek",
        name = "DeepSeek",
        baseUrl = "https://api.deepseek.com",
        // 请在这里填入您的 DeepSeek API Key
        apiKey = "sk-a13bd2345be44a4b89d12a3fb81327cd",
        models = listOf(
            Model(
                modelId = "deepseek-chat",
                displayName = "DeepSeek V3"
            ),
            Model(
                modelId = "deepseek-reasoner",
                displayName = "DeepSeek R1"
            )
        )
    )
)
