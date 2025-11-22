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

package com.example.star.aiwork.conversation

import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.star.aiwork.R

/**
 * 按钮可见性状态枚举。
 */
private enum class Visibility {
    VISIBLE, // 可见
    GONE,    // 隐藏
}

/**
 * 显示一个允许用户滚动到底部的按钮。
 *
 * 当用户向上滚动查看历史消息时，此按钮出现，点击后可快速回到最新消息位置。
 * 按钮的显示和隐藏伴随位移动画。
 *
 * @param enabled 按钮是否应该启用/显示。如果为 true，按钮将滑入视野；如果为 false，按钮将滑出。
 * @param onClicked 点击按钮时的回调，通常执行滚动到底部的操作。
 * @param modifier 修饰符。
 */
@Composable
fun JumpToBottom(enabled: Boolean, onClicked: () -> Unit, modifier: Modifier = Modifier) {
    // 管理“跳转到底部”按钮的可见性动画
    // 根据 enabled 状态切换 Visibility 枚举
    val transition = updateTransition(
        if (enabled) Visibility.VISIBLE else Visibility.GONE,
        label = "JumpToBottom visibility animation",
    )
    
    // 定义垂直偏移量的动画
    // 当不可见时，按钮位于底部下方 32dp 处（隐藏）
    // 当可见时，按钮位于底部上方 32dp 处（显示）
    val bottomOffset by transition.animateDp(label = "JumpToBottom offset animation") {
        if (it == Visibility.GONE) {
            (-32).dp
        } else {
            32.dp
        }
    }
    
    // 只有当按钮部分或完全滑入视野时才进行渲染
    if (bottomOffset > 0.dp) {
        ExtendedFloatingActionButton(
            icon = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_arrow_downward),
                    modifier = Modifier.height(18.dp),
                    contentDescription = null,
                )
            },
            text = {
                Text(text = stringResource(id = R.string.jumpBottom))
            },
            onClick = onClicked,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = modifier
                // 应用动态计算的垂直偏移量
                .offset(x = 0.dp, y = -bottomOffset)
                .height(36.dp),
        )
    }
}

@Preview
@Composable
fun JumpToBottomPreview() {
    JumpToBottom(enabled = true, onClicked = {})
}
