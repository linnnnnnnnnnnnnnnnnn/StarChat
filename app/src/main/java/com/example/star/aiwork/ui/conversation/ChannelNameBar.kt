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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.star.aiwork.R
import com.example.star.aiwork.domain.model.SessionEntity
import com.example.star.aiwork.ui.FunctionalityNotAvailablePopup
import com.example.star.aiwork.ui.components.JetchatAppBar
import com.example.star.aiwork.ui.theme.JetchatTheme
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelNameBar(
    channelName: String,
    channelMembers: Int,
    modifier: Modifier = Modifier,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    onNavIconPressed: () -> Unit = { },
    onSettingsClicked: () -> Unit = { },
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    searchResults: List<SessionEntity>,
    onSessionSelected: (SessionEntity) -> Unit
) {
    var isSearchActive by remember { mutableStateOf(false) }
    var functionalityNotAvailablePopupShown by remember { mutableStateOf(false) }
    if (functionalityNotAvailablePopupShown) {
        FunctionalityNotAvailablePopup { functionalityNotAvailablePopupShown = false }
    }

    if (isSearchActive) {
        SearchAppBar(
            query = searchQuery,
            onQueryChange = onSearchQueryChanged,
            searchResults = searchResults,
            onSessionSelected = {
                onSessionSelected(it)
                isSearchActive = false
                // 选择会话后清空搜索查询
                onSearchQueryChanged("")
            },
            onCloseSearch = { 
                isSearchActive = false
                // 关闭搜索时清空搜索查询
                onSearchQueryChanged("")
            }
        )
    } else {
        JetchatAppBar(
            modifier = modifier,
            scrollBehavior = scrollBehavior,
            onNavIconPressed = onNavIconPressed,
            title = {
                Text(
                    text = channelName,
                    style = MaterialTheme.typography.titleMedium
                )
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
                IconButton(onClick = { isSearchActive = true }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_search),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        contentDescription = stringResource(id = R.string.search)
                    )
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchAppBar(
    query: String,
    onQueryChange: (String) -> Unit,
    searchResults: List<SessionEntity>,
    onSessionSelected: (SessionEntity) -> Unit,
    onCloseSearch: () -> Unit
) {
    // 用于获取输入框的尺寸，以便让下拉菜单宽度与输入框一致
    var textFieldSize by remember { mutableStateOf(IntSize.Zero) }

    // 只有当有搜索词且有结果时才显示弹窗
    val showResults = query.isNotEmpty() && searchResults.isNotEmpty()

    TopAppBar(
        title = {
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text("搜索会话...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { textFieldSize = it }, // [关键] 获取输入框尺寸
                    singleLine = true
                )

                if (showResults) {
                    // 使用原生 Popup 替代 ExposedDropdownMenu
                    Popup(
                        alignment = Alignment.TopStart,
                        // 让弹窗显示在输入框正下方
                        offset = IntOffset(0, textFieldSize.height),
                        properties = PopupProperties(
                            // focusable = false 确保弹窗永远不会抢走输入框的光标
                            focusable = false,
                            dismissOnBackPress = true,
                            dismissOnClickOutside = true
                        )
                    ) {
                        // 使用 Surface 模拟菜单的卡片样式
                        Surface(
                            modifier = Modifier
                                .width(with(LocalDensity.current) { textFieldSize.width.toDp() }), // 宽度对齐输入框
                            shape = MaterialTheme.shapes.extraSmall, // 保持与 ExposedDropdownMenu 类似的圆角
                            tonalElevation = 3.dp,
                            shadowElevation = 3.dp
                        ) {
                            // 添加滚动支持，防止结果太多占满屏幕
                            Column(
                                modifier = Modifier
                                    .heightIn(max = 300.dp) // 限制最大高度
                                    .verticalScroll(rememberScrollState())
                            ) {
                                searchResults.forEach { session ->
                                    DropdownMenuItem(
                                        text = { Text(session.name) },
                                        onClick = {
                                            onSessionSelected(session)
                                            // 点击后通常会清空搜索或关闭，由上层逻辑控制
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onCloseSearch) {
                Icon(Icons.Default.ArrowBack, contentDescription = "关闭搜索")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
fun ChannelBarPrev() {
    JetchatTheme {
        ChannelNameBar(
            channelName = "composers",
            channelMembers = 52,
            searchQuery = "",
            onSearchQueryChanged = {},
            searchResults = emptyList(),
            onSessionSelected = {}
        )
    }
}