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

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.LayoutModifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp

/**
 * 自定义 LayoutModifier，用于控制文本的基线位置。
 *
 * 应用于 Text 组件时，它设置顶部与第一条基线之间的距离。
 * 它还使元素的底部与文本的最后一条基线重合。
 *
 *     _______________
 *     |             |   ↑
 *     |             |   |  heightFromBaseline
 *     |Hello, World!|   ↓
 *     ‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾
 *
 * 此修饰符可用于通过固定基线之间的距离来排版多个文本元素，
 * 从而实现更精确的垂直对齐效果。
 *
 * @property heightFromBaseline 从组件顶部到文本第一条基线的距离。
 */
data class BaselineHeightModifier(val heightFromBaseline: Dp) : LayoutModifier {

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {

        // 测量子元素 (通常是 Text)
        val textPlaceable = measurable.measure(constraints)
        // 获取第一条基线的位置 (距离顶部的距离)
        val firstBaseline = textPlaceable[FirstBaseline]
        // 获取最后一条基线的位置 (距离顶部的距离)
        val lastBaseline = textPlaceable[LastBaseline]

        // 计算布局的总高度：
        // heightFromBaseline (期望的顶部到第一基线距离) + (最后基线 - 第一基线)
        // 这样做的结果是：Layout 顶部到第一基线是 heightFromBaseline，Layout 底部就在最后一条基线处。
        val height = heightFromBaseline.roundToPx() + lastBaseline - firstBaseline
        
        return layout(constraints.maxWidth, height) {
            // 计算子元素放置的 Y 坐标。
            // 我们希望子元素的第一基线位于 heightFromBaseline 处。
            // 子元素自身的第一基线位于 textPlaceable[FirstBaseline]。
            // 所以 Y 偏移量 = heightFromBaseline - firstBaseline
            val topY = heightFromBaseline.roundToPx() - firstBaseline
            textPlaceable.place(0, topY)
        }
    }
}

/**
 * 便捷扩展函数，用于应用 BaselineHeightModifier。
 *
 * @param heightFromBaseline 期望的从顶部到第一条基线的距离。
 */
fun Modifier.baselineHeight(heightFromBaseline: Dp): Modifier = this.then(BaselineHeightModifier(heightFromBaseline))
