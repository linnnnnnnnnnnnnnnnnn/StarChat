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

package com.example.star.aiwork.widget.composables

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.components.Scaffold
import androidx.glance.appwidget.components.TitleBar
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.text.Text
import com.example.star.aiwork.NavActivity
import com.example.star.aiwork.R
import com.example.star.aiwork.conversation.Message
import com.example.star.aiwork.widget.theme.JetChatGlanceTextStyles
import com.example.star.aiwork.widget.theme.JetchatGlanceColorScheme

/**
 * 显示消息列表的 Glance 小部件 Composable。
 *
 * 使用 [Scaffold] 提供标准的小部件布局结构，包含标题栏和内容区域。
 * 内容区域使用 [LazyColumn] 来高效显示消息列表。
 *
 * @param messages 要显示的消息列表。
 */
@Composable
fun MessagesWidget(messages: List<Message>) {
    Scaffold(titleBar = {
        // 小部件标题栏
        TitleBar(
            startIcon = ImageProvider(R.drawable.ic_jetchat),
            iconColor = null, // null 表示使用图标的原始颜色
            title = LocalContext.current.getString(R.string.messages_widget_title),
        )
    }, backgroundColor = JetchatGlanceColorScheme.colors.background) {
        // 可滚动的消息列表
        LazyColumn(modifier = GlanceModifier.fillMaxWidth()) {
            messages.forEach {
                item {
                    Column(modifier = GlanceModifier.fillMaxWidth()) {
                        MessageItem(it)
                        Spacer(modifier = GlanceModifier.height(10.dp))
                    }
                }
            }
        }
    }
}

/**
 * 单条消息的显示项。
 *
 * 显示消息作者和内容。点击该项会启动应用的主 Activity [NavActivity]。
 *
 * @param message 要显示的消息对象。
 */
@Composable
fun MessageItem(message: Message) {
    // 设置点击事件：点击启动 NavActivity
    Column(modifier = GlanceModifier.clickable(actionStartActivity<NavActivity>()).fillMaxWidth()) {
        Text(
            text = message.author,
            style = JetChatGlanceTextStyles.titleMedium,
        )
        Text(
            text = message.content,
            style = JetChatGlanceTextStyles.bodyMedium,
        )
    }
}

@Preview
@Composable
fun MessageItemPreview() {
    MessageItem(Message("John", "This is a preview of the message Item", "8:02PM"))
}

@Preview
@Composable
fun WidgetPreview() {
    MessagesWidget(listOf(Message("John", "This is a preview of the message Item", "8:02PM")))
}
