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

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import com.example.star.aiwork.FunctionalityNotAvailablePopup
import com.example.star.aiwork.MainViewModel
import com.example.star.aiwork.R
import com.example.star.aiwork.components.JetchatAppBar
import com.example.star.aiwork.theme.JetchatTheme

/**
 * 显示用户个人资料的 Fragment。
 *
 * 该 Fragment 将 Compose UI 嵌入到传统的 View 系统中。
 * 它使用 ViewBinding (或 findViewById) 找到 ComposeView，并设置其内容。
 *
 * 主要包含两个 ComposeView:
 * 1. Toolbar (顶部应用栏)
 * 2. Profile Content (个人资料详情)
 */
class ProfileFragment : Fragment() {

    private val viewModel: ProfileViewModel by viewModels()
    private val activityViewModel: MainViewModel by activityViewModels()

    override fun onAttach(context: Context) {
        super.onAttach(context)
        // 获取传递给 Fragment 的参数 (userId)
        // 建议在实际项目中使用 Safe Args 插件
        val userId = arguments?.getString("userId")
        viewModel.setUserId(userId)
    }

    @OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val rootView: View = inflater.inflate(R.layout.fragment_profile, container, false)

        // 设置顶部工具栏的 Compose 内容
        rootView.findViewById<ComposeView>(R.id.toolbar_compose_view).apply {
            setContent {
                var functionalityNotAvailablePopupShown by remember { mutableStateOf(false) }
                if (functionalityNotAvailablePopupShown) {
                    FunctionalityNotAvailablePopup { functionalityNotAvailablePopupShown = false }
                }

                JetchatTheme {
                    JetchatAppBar(
                        // 重置传递给 Compose 树根的最小边界，以适应 WrapContent
                        modifier = Modifier.wrapContentSize(),
                        onNavIconPressed = { activityViewModel.openDrawer() },
                        title = { },
                        actions = {
                            // "更多" 选项图标
                            Icon(
                                painter = painterResource(id = R.drawable.ic_more_vert),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .clickable(onClick = {
                                        functionalityNotAvailablePopupShown = true
                                    })
                                    .padding(horizontal = 12.dp, vertical = 16.dp)
                                    .height(24.dp),
                                contentDescription = stringResource(id = R.string.more_options),
                            )
                        },
                    )
                }
            }
        }

        // 设置个人资料主要内容的 Compose 内容
        rootView.findViewById<ComposeView>(R.id.profile_compose_view).apply {
            setContent {
                // 观察 ViewModel 中的用户数据
                val userData by viewModel.userData.observeAsState()
                // 记住嵌套滚动互操作连接，以便与 CoordinatorLayout 等 View 组件协同工作
                val nestedScrollInteropConnection = rememberNestedScrollInteropConnection()

                JetchatTheme {
                    if (userData == null) {
                        ProfileError()
                    } else {
                        ProfileScreen(
                            userData = userData!!,
                            nestedScrollInteropConnection = nestedScrollInteropConnection,
                        )
                    }
                }
            }
        }
        return rootView
    }
}
