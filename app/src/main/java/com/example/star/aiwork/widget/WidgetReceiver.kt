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

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * Glance 应用小部件的接收器 (Receiver)。
 *
 * 这是一个 BroadcastReceiver，用于处理 App Widget 的生命周期事件。
 * 必须在 AndroidManifest.xml 中注册。
 *
 * [GlanceAppWidgetReceiver] 是 Glance 提供的辅助类，
 * 它简化了将广播事件连接到 [GlanceAppWidget] 的过程。
 */
class WidgetReceiver : GlanceAppWidgetReceiver() {

    /**
     * 指定此接收器关联的 GlanceAppWidget 实例。
     *
     * 当接收器接收到更新请求时，它会使用此小部件类来生成新的 UI。
     */
    override val glanceAppWidget: GlanceAppWidget
        get() = JetChatWidget()
}
