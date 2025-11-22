/*
 * Copyright 2023 The Android Open Source Project
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

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipState
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.star.aiwork.R
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * 录音按钮组件。
 *
 * 该组件处理录音的交互逻辑，包括长按录音、滑动取消等手势，并提供录音状态的视觉反馈动画。
 *
 * @param recording 当前是否正在录音。
 * @param swipeOffset 当前滑动的偏移量（用于检测滑动取消）。
 * @param onSwipeOffsetChange 当滑动偏移量改变时的回调。
 * @param onStartRecording 开始录音的回调。返回 true 表示成功开始。
 * @param onFinishRecording 完成录音的回调（正常松手）。
 * @param onCancelRecording 取消录音的回调（滑动取消或异常中断）。
 * @param modifier 修饰符。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordButton(
    recording: Boolean,
    swipeOffset: () -> Float,
    onSwipeOffsetChange: (Float) -> Unit,
    onStartRecording: () -> Boolean,
    onFinishRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 动画转换状态，根据 recording 状态变化触发动画
    val transition = updateTransition(targetState = recording, label = "record")
    
    // 缩放动画：录音时按钮背景放大
    val scale = transition.animateFloat(
        transitionSpec = { spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow) },
        label = "record-scale",
        targetValueByState = { rec -> if (rec) 2f else 1f },
    )
    
    // 透明度动画：录音时显示背景
    val containerAlpha = transition.animateFloat(
        transitionSpec = { tween(2000) },
        label = "record-scale",
        targetValueByState = { rec -> if (rec) 1f else 0f },
    )
    
    // 图标颜色动画：录音时改变图标颜色以适配背景
    val iconColor = transition.animateColor(
        transitionSpec = { tween(200) },
        label = "record-scale",
        targetValueByState = { rec ->
            if (rec) contentColorFor(LocalContentColor.current)
            else LocalContentColor.current
        },
    )

    Box {
        // 录音时的背景效果层
        Box(
            Modifier
                .matchParentSize()
                .aspectRatio(1f)
                .graphicsLayer {
                    alpha = containerAlpha.value
                    scaleX = scale.value
                    scaleY = scale.value
                }
                .clip(CircleShape)
                .background(LocalContentColor.current),
        )
        
        val scope = rememberCoroutineScope()
        val tooltipState = remember { TooltipState() }
        
        // 工具提示：显示“长按录音”提示
        TooltipBox(
            positionProvider = TooltipDefaults.rememberRichTooltipPositionProvider(),
            tooltip = {
                RichTooltip {
                    Text(stringResource(R.string.touch_and_hold_to_record))
                }
            },
            enableUserInput = false,
            state = tooltipState,
        ) {
            Icon(
                painterResource(id = R.drawable.ic_mic),
                contentDescription = stringResource(R.string.record_message),
                tint = iconColor.value,
                modifier = modifier
                    .sizeIn(minWidth = 56.dp, minHeight = 6.dp)
                    .padding(18.dp)
                    .clickable { } // 这里的点击事件为空，因为主要交互由 voiceRecordingGesture 处理
                    .voiceRecordingGesture(
                        horizontalSwipeProgress = swipeOffset,
                        onSwipeProgressChanged = onSwipeOffsetChange,
                        onClick = { scope.launch { tooltipState.show() } }, // 点击时显示提示
                        onStartRecording = onStartRecording,
                        onFinishRecording = onFinishRecording,
                        onCancelRecording = onCancelRecording,
                    ),
            )
        }
    }
}

/**
 * 自定义 Modifier，用于处理语音录制的手势逻辑。
 *
 * 处理长按开始、拖拽、松手结束以及左滑取消等逻辑。
 *
 * @param horizontalSwipeProgress 获取当前水平滑动进度的 lambda。
 * @param onSwipeProgressChanged 水平滑动进度改变时的回调。
 * @param onClick 点击时的回调（非长按）。
 * @param onStartRecording 开始录音的回调。
 * @param onFinishRecording 完成录音的回调。
 * @param onCancelRecording 取消录音的回调。
 * @param swipeToCancelThreshold 滑动取消的水平距离阈值。
 * @param verticalThreshold 滑动取消的垂直容差阈值。
 */
private fun Modifier.voiceRecordingGesture(
    horizontalSwipeProgress: () -> Float,
    onSwipeProgressChanged: (Float) -> Unit,
    onClick: () -> Unit = {},
    onStartRecording: () -> Boolean = { false },
    onFinishRecording: () -> Unit = {},
    onCancelRecording: () -> Unit = {},
    swipeToCancelThreshold: Dp = 200.dp,
    verticalThreshold: Dp = 80.dp,
): Modifier = this
    // 处理点击手势
    .pointerInput(Unit) { detectTapGestures { onClick() } }
    // 处理长按和拖拽手势
    .pointerInput(Unit) {
        var offsetY = 0f
        var dragging = false
        val swipeToCancelThresholdPx = swipeToCancelThreshold.toPx()
        val verticalThresholdPx = verticalThreshold.toPx()

        detectDragGesturesAfterLongPress(
            onDragStart = {
                // 长按开始，初始化状态并触发开始录音
                onSwipeProgressChanged(0f)
                offsetY = 0f
                dragging = true
                onStartRecording()
            },
            onDragCancel = {
                // 手势被系统取消（如来电等），触发取消录音
                onCancelRecording()
                dragging = false
            },
            onDragEnd = {
                // 用户松手，如果正在拖拽（录音中），则触发完成录音
                if (dragging) {
                    onFinishRecording()
                }
                dragging = false
            },
            onDrag = { change, dragAmount ->
                if (dragging) {
                    // 更新滑动进度
                    onSwipeProgressChanged(horizontalSwipeProgress() + dragAmount.x)
                    offsetY += dragAmount.y
                    
                    val offsetX = horizontalSwipeProgress()
                    
                    // 检测是否达到“滑动取消”的条件：
                    // 1. 向左滑动 (offsetX < 0)
                    // 2. 水平距离超过阈值 (abs(offsetX) >= swipeToCancelThresholdPx)
                    // 3. 垂直偏移在容差范围内 (abs(offsetY) <= verticalThresholdPx)
                    if (
                        offsetX < 0 &&
                        abs(offsetX) >= swipeToCancelThresholdPx &&
                        abs(offsetY) <= verticalThresholdPx
                    ) {
                        onCancelRecording()
                        dragging = false
                    }
                }
            },
        )
    }
