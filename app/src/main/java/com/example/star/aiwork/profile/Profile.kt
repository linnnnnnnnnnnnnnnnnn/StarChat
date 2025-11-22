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

package com.example.star.aiwork.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.star.aiwork.FunctionalityNotAvailablePopup
import com.example.star.aiwork.R
import com.example.star.aiwork.components.AnimatingFabContent
import com.example.star.aiwork.components.baselineHeight
import com.example.star.aiwork.data.colleagueProfile
import com.example.star.aiwork.data.meProfile
import com.example.star.aiwork.theme.JetchatTheme

/**
 * 个人资料屏幕。
 *
 * 显示用户的详细信息，如头像、姓名、职位、状态等。
 * 支持滚动，并包含一个根据滚动状态收缩/展开的 FAB。
 *
 * @param userData 用户个人资料数据。
 * @param nestedScrollInteropConnection 用于与 View 系统（如 CoordinatorLayout）进行嵌套滚动的连接器。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun ProfileScreen(
    userData: ProfileScreenState,
    nestedScrollInteropConnection: NestedScrollConnection = rememberNestedScrollInteropConnection(),
) {
    var functionalityNotAvailablePopupShown by remember { mutableStateOf(false) }
    if (functionalityNotAvailablePopupShown) {
        FunctionalityNotAvailablePopup { functionalityNotAvailablePopupShown = false }
    }

    val scrollState = rememberScrollState()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollInteropConnection)
            .systemBarsPadding(),
    ) {
        Surface {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
            ) {
                ProfileHeader(
                    scrollState,
                    userData,
                    this@BoxWithConstraints.maxHeight,
                )
                UserInfoFields(userData, this@BoxWithConstraints.maxHeight)
            }
        }

        // 当滚动条位于顶部（scrollY == 0）时，展开 FAB
        val fabExtended by remember { derivedStateOf { scrollState.value == 0 } }
        
        ProfileFab(
            extended = fabExtended,
            userIsMe = userData.isMe(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                // 偏移 FAB 以补偿 CoordinatorLayout 的折叠行为 (如果存在)
                // 在纯 Compose 环境中可能不需要这个负偏移
                .offset(y = ((-100).dp)),
            onFabClicked = { functionalityNotAvailablePopupShown = true },
        )
    }
}

/**
 * 用户信息字段列表。
 *
 * @param userData 用户数据。
 * @param containerHeight 容器总高度，用于计算底部留白。
 */
@Composable
private fun UserInfoFields(userData: ProfileScreenState, containerHeight: Dp) {
    Column {
        Spacer(modifier = Modifier.height(8.dp))

        NameAndPosition(userData)

        ProfileProperty(stringResource(R.string.display_name), userData.displayName)

        ProfileProperty(stringResource(R.string.status), userData.status)

        ProfileProperty(stringResource(R.string.twitter), userData.twitter, isLink = true)

        userData.timeZone?.let {
            ProfileProperty(stringResource(R.string.timezone), userData.timeZone)
        }

        // 添加一个 Spacer，确保列表内容在不同设备上都能占据一定高度，
        // 从而保证顶部内容（如头像）在滚动时有足够的空间被折叠。
        // 这里硬编码保留 320dp 的内容区域。
        Spacer(Modifier.height((containerHeight - 320.dp).coerceAtLeast(0.dp)))
    }
}

/**
 * 姓名和职位组合组件。
 */
@Composable
private fun NameAndPosition(userData: ProfileScreenState) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Name(
            userData,
            modifier = Modifier.baselineHeight(32.dp),
        )
        Position(
            userData,
            modifier = Modifier
                .padding(bottom = 20.dp)
                .baselineHeight(24.dp),
        )
    }
}

@Composable
private fun Name(userData: ProfileScreenState, modifier: Modifier = Modifier) {
    Text(
        text = userData.name,
        modifier = modifier,
        style = MaterialTheme.typography.headlineSmall,
    )
}

@Composable
private fun Position(userData: ProfileScreenState, modifier: Modifier = Modifier) {
    Text(
        text = userData.position,
        modifier = modifier,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * 个人资料头部（头像）。
 *
 * 头像会随着滚动产生视差效果（虽然这里的实现比较简单，主要是计算 padding）。
 */
@Composable
private fun ProfileHeader(scrollState: ScrollState, data: ProfileScreenState, containerHeight: Dp) {
    val offset = (scrollState.value / 2)
    val offsetDp = with(LocalDensity.current) { offset.toDp() }

    data.photo?.let {
        Image(
            modifier = Modifier
                .heightIn(max = containerHeight / 2)
                .fillMaxWidth()
                // TODO: 更新为使用 offset 修饰符以避免重组 (recomposition)
                .padding(
                    start = 16.dp,
                    top = offsetDp,
                    end = 16.dp,
                )
                .clip(CircleShape),
            painter = painterResource(id = it),
            contentScale = ContentScale.Crop,
            contentDescription = null,
        )
    }
}

/**
 * 单个属性行组件（标签 + 值）。
 */
@Composable
fun ProfileProperty(label: String, value: String, isLink: Boolean = false) {
    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
        Divider()
        Text(
            text = label,
            modifier = Modifier.baselineHeight(24.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val style = if (isLink) {
            MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.primary)
        } else {
            MaterialTheme.typography.bodyLarge
        }
        Text(
            text = value,
            modifier = Modifier.baselineHeight(24.dp),
            style = style,
        )
    }
}

@Composable
fun ProfileError() {
    Text(stringResource(R.string.profile_error))
}

/**
 * 个人资料页面的浮动操作按钮 (FAB)。
 *
 * 根据用户身份显示不同图标（编辑或发消息），并支持扩展动画。
 */
@Composable
fun ProfileFab(extended: Boolean, userIsMe: Boolean, modifier: Modifier = Modifier, onFabClicked: () -> Unit = { }) {
    key(userIsMe) {
        // 使用 key 防止在组合期间多次执行
        FloatingActionButton(
            onClick = onFabClicked,
            modifier = modifier
                .padding(16.dp)
                .navigationBarsPadding()
                .height(48.dp)
                .widthIn(min = 48.dp),
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ) {
            AnimatingFabContent(
                icon = {
                    Icon(
                        painter = painterResource(id = if (userIsMe) R.drawable.ic_create else R.drawable.ic_chat),
                        contentDescription = stringResource(
                            if (userIsMe) R.string.edit_profile else R.string.message,
                        ),
                    )
                },
                text = {
                    Text(
                        text = stringResource(
                            id = if (userIsMe) R.string.edit_profile else R.string.message,
                        ),
                    )
                },
                extended = extended,
            )
        }
    }
}

@Preview(widthDp = 640, heightDp = 360)
@Composable
fun ConvPreviewLandscapeMeDefault() {
    JetchatTheme {
        ProfileScreen(meProfile)
    }
}

@Preview(widthDp = 360, heightDp = 480)
@Composable
fun ConvPreviewPortraitMeDefault() {
    JetchatTheme {
        ProfileScreen(meProfile)
    }
}

@Preview(widthDp = 360, heightDp = 480)
@Composable
fun ConvPreviewPortraitOtherDefault() {
    JetchatTheme {
        ProfileScreen(colleagueProfile)
    }
}

@Preview
@Composable
fun ProfileFabPreview() {
    JetchatTheme {
        ProfileFab(extended = true, userIsMe = false)
    }
}
