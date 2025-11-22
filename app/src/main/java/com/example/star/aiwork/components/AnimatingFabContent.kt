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

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.util.lerp
import kotlin.math.roundToInt

/**
 * 一个显示图标和文本的布局，用作 FAB（浮动操作按钮）的内容，并支持扩展动画。
 * 
 * 当 FAB 扩展时，文本会显示出来；当收缩时，只显示图标。
 * 此 Composable 处理图标和文本之间的布局过渡以及不透明度动画。
 *
 * @param icon 图标内容的 Composable lambda。
 * @param text 文本内容的 Composable lambda。
 * @param modifier 应用于此布局的修饰符。
 * @param extended 是否处于扩展状态。True 表示显示文本，False 表示仅显示图标。
 */
@Composable
fun AnimatingFabContent(
    icon: @Composable () -> Unit,
    text: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    extended: Boolean = true,
) {
    // 根据 extended 参数确定当前的目标状态（扩展或折叠）
    val currentState = if (extended) ExpandableFabStates.Extended else ExpandableFabStates.Collapsed
    // 创建一个转换对象，用于管理状态更改时的动画
    val transition = updateTransition(currentState, "fab_transition")

    // 文本不透明度动画
    val textOpacity by transition.animateFloat(
        transitionSpec = {
            if (targetState == ExpandableFabStates.Collapsed) {
                // 收缩时：线性缓动，持续时间约为总时长的 5/12
                tween(
                    easing = LinearEasing,
                    durationMillis = (transitionDuration / 12f * 5).roundToInt(), // 5 / 12 帧
                )
            } else {
                // 扩展时：线性缓动，延迟 1/3 时间，持续 5/12 时间
                tween(
                    easing = LinearEasing,
                    delayMillis = (transitionDuration / 3f).roundToInt(), // 4 / 12 帧
                    durationMillis = (transitionDuration / 12f * 5).roundToInt(), // 5 / 12 帧
                )
            }
        },
        label = "fab_text_opacity",
    ) { state ->
        // 定义不同状态下的目标不透明度
        if (state == ExpandableFabStates.Collapsed) {
            0f
        } else {
            1f
        }
    }
    
    // FAB 宽度因子动画 (用于计算布局宽度)
    val fabWidthFactor by transition.animateFloat(
        transitionSpec = {
            if (targetState == ExpandableFabStates.Collapsed) {
                // 收缩时：快速移出慢速移入，持续整个过渡时间
                tween(
                    easing = FastOutSlowInEasing,
                    durationMillis = transitionDuration,
                )
            } else {
                // 扩展时：同上
                tween(
                    easing = FastOutSlowInEasing,
                    durationMillis = transitionDuration,
                )
            }
        },
        label = "fab_width_factor",
    ) { state ->
         // 0f 表示折叠（最小宽度），1f 表示展开（全宽）
        if (state == ExpandableFabStates.Collapsed) {
            0f
        } else {
            1f
        }
    }
    
    // 使用 lambda 而不是直接传递 Float 值，以推迟读取状态，
    // 这可以提高性能，防止不必要的重组 (Recomposition)。
    IconAndTextRow(
        icon,
        text,
        { textOpacity },
        { fabWidthFactor },
        modifier = modifier,
    )
}

/**
 * 负责实际布局图标和文本的 Composable。
 * 使用自定义 Layout 来根据动画进度动态调整宽度。
 */
@Composable
private fun IconAndTextRow(
    icon: @Composable () -> Unit,
    text: @Composable () -> Unit,
    opacityProgress: () -> Float, // 使用 lambda 延迟读取
    widthProgress: () -> Float,
    modifier: Modifier,
) {
    Layout(
        modifier = modifier,
        content = {
            icon()
            Box(modifier = Modifier.graphicsLayer { alpha = opacityProgress() }) {
                text()
            }
        },
    ) { measurables, constraints ->

        // 测量子元素
        val iconPlaceable = measurables[0].measure(constraints)
        val textPlaceable = measurables[1].measure(constraints)

        val height = constraints.maxHeight

        // FAB 的初始纵横比为 1，所以初始宽度等于高度
        val initialWidth = height.toFloat()

        // 计算内边距：假设图标居中，内边距为 (总宽 - 图标宽) / 2
        val iconPadding = (initialWidth - iconPlaceable.width) / 2f

        // 展开后的总宽度 = 图标宽 + 文本宽 + 3个内边距 (左、中、右)
        val expandedWidth = iconPlaceable.width + textPlaceable.width + iconPadding * 3

        // 应用动画因子，在初始宽度和展开宽度之间进行线性插值
        val width = lerp(initialWidth, expandedWidth, widthProgress())

        // 布局子元素
        layout(width.roundToInt(), height) {
            // 放置图标：左侧有 padding，垂直居中
            iconPlaceable.place(
                iconPadding.roundToInt(),
                constraints.maxHeight / 2 - iconPlaceable.height / 2,
            )
            // 放置文本：在图标右侧 (图标宽 + 2个 padding)，垂直居中
            textPlaceable.place(
                (iconPlaceable.width + iconPadding * 2).roundToInt(),
                constraints.maxHeight / 2 - textPlaceable.height / 2,
            )
        }
    }
}

// FAB 的两种状态：折叠和展开
private enum class ExpandableFabStates { Collapsed, Extended }

// 过渡动画持续时间 (毫秒)
private const val transitionDuration = 200
