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

package com.example.star.aiwork.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.star.aiwork.R
import com.example.star.aiwork.theme.JetchatTheme

/**
 * Jetchat 应用的通用顶部应用栏 (TopAppBar)。
 *
 * 它是一个居中对齐的顶部应用栏，包含：
 * - 一个导航图标 (Jetchat Logo)，点击可触发侧边栏或其他导航动作。
 * - 一个可组合的标题。
 * - 可选的操作按钮区域。
 * - 支持滚动行为 (ScrollBehavior)。
 *
 * @param modifier 应用于 TopAppBar 的修饰符。
 * @param scrollBehavior 顶部应用栏的滚动行为，例如随内容滚动而折叠。
 * @param onNavIconPressed 当导航图标被点击时的回调。
 * @param title 标题内容的 Composable lambda。
 * @param actions 操作区域 (通常是图标按钮) 的 Composable lambda。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JetchatAppBar(
    modifier: Modifier = Modifier,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    onNavIconPressed: () -> Unit = { },
    title: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    CenterAlignedTopAppBar(
        modifier = modifier,
        actions = actions,
        title = title,
        scrollBehavior = scrollBehavior,
        navigationIcon = {
            JetchatIcon(
                contentDescription = stringResource(id = R.string.navigation_drawer_open),
                modifier = Modifier
                    .size(64.dp)
                    .clickable(onClick = onNavIconPressed)
                    .padding(16.dp),
            )
        },
    )
}

/**
 * 亮色主题下的预览。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
fun JetchatAppBarPreview() {
    JetchatTheme {
        JetchatAppBar(title = { Text("Preview!") })
    }
}

/**
 * 暗色主题下的预览。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
fun JetchatAppBarPreviewDark() {
    JetchatTheme(isDarkTheme = true) {
        JetchatAppBar(title = { Text("Preview!") })
    }
}
