/*
 * Copyright 2021 The Android Open Source Project
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

package com.example.star.aiwork.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.example.star.aiwork.R

/**
 * Jetchat 应用程序的品牌图标组件。
 *
 * 该图标由两部分组成：背景和前景，叠加在一起形成完整的 Logo。
 * 支持通过 Semantics 设置内容描述，用于无障碍功能。
 *
 * @param contentDescription 用于无障碍服务的图标描述。如果为 null，则不设置语义信息。
 * @param modifier 应用于图标容器的修饰符。
 */
@Composable
fun JetchatIcon(contentDescription: String?, modifier: Modifier = Modifier) {
    // 配置语义属性（如果提供了 contentDescription）
    val semantics = if (contentDescription != null) {
        Modifier.semantics {
            this.contentDescription = contentDescription
            this.role = Role.Image
        }
    } else {
        Modifier
    }
    // 使用 Box 将两个图标层叠在一起
    Box(modifier = modifier.then(semantics)) {
        // 背景层图标
        Icon(
            painter = painterResource(id = R.drawable.ic_jetchat_back),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primaryContainer,
        )
        // 前景层图标
        Icon(
            painter = painterResource(id = R.drawable.ic_jetchat_front),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}
