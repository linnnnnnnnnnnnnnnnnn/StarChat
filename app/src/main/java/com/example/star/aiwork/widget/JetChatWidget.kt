/*
 * Copyright 2024 The Android Open Source Project
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

package com.example.star.aiwork.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import com.example.star.aiwork.data.unreadMessages
import com.example.star.aiwork.widget.composables.MessagesWidget

/**
 * 定义 JetChat 的应用小部件 (App Widget)。
 *
 * 使用 Jetpack Glance 库构建，这允许使用类似于 Compose 的 API 来创建小部件 UI。
 *
 * 此类继承自 [GlanceAppWidget]，它是所有 Glance 小部件的基类。
 * 主要职责是提供小部件的内容。
 */
class JetChatWidget : GlanceAppWidget() {

    /**
     * 提供小部件的内容。
     *
     * 当小部件需要更新或首次创建时调用。
     *
     * @param context Android 上下文。
     * @param id 此 Glance 小部件实例的唯一标识符。
     */
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // provideContent 是入口点，用于定义 Glance 小部件的 UI。
        provideContent {
            // 应用 Glance 主题，确保小部件遵循系统的 Material You 样式 (如果支持)。
            GlanceTheme {
                // 显示消息列表小部件内容。
                // 这里使用硬编码的 unreadMessages 列表作为示例数据。
                // 在真实应用中，应从 Repository 或数据库加载数据。
                MessagesWidget(unreadMessages.toList())
            }
        }
    }
}
