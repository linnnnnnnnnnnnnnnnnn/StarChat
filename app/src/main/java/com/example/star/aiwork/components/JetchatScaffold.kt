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

package com.example.star.aiwork.components

import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue.Closed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import com.example.star.aiwork.theme.JetchatTheme

/**
 * 封装了 ModalNavigationDrawer 的 Jetchat 脚手架。
 *
 * 该组件为应用提供了一个侧边导航抽屉结构。它将具体的抽屉内容 (JetchatDrawerContent)
 * 和主屏幕内容组合在一起。
 *
 * @param drawerState 抽屉的状态 (打开或关闭)。
 * @param selectedMenu 当前选中的菜单项，用于高亮显示。
 * @param onProfileClicked 当用户在抽屉中点击个人资料时触发的回调。
 * @param onChatClicked 当用户在抽屉中点击聊天会话时触发的回调。
 * @param content 抽屉关闭时显示的主屏幕内容。
 */
@Composable
fun JetchatDrawer(
    drawerState: DrawerState = rememberDrawerState(initialValue = Closed),
    selectedMenu: String,
    onProfileClicked: (String) -> Unit,
    onChatClicked: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    JetchatTheme {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    drawerState = drawerState,
                    drawerContainerColor = MaterialTheme.colorScheme.background,
                    drawerContentColor = MaterialTheme.colorScheme.onBackground,
                ) {
                    JetchatDrawerContent(
                        onProfileClicked = onProfileClicked,
                        onChatClicked = onChatClicked,
                        selectedMenu = selectedMenu,
                    )
                }
            },
            content = content,
        )
    }
}
