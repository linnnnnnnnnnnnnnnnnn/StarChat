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

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.star.aiwork.data.colleagueProfile
import com.example.star.aiwork.data.meProfile

/**
 * 个人资料屏幕的 ViewModel。
 *
 * 负责管理和提供用户个人资料数据。
 */
class ProfileViewModel : ViewModel() {

    private var userId: String = ""

    /**
     * 设置要显示的用户的 ID。
     *
     * 根据 ID 加载相应的用户数据（目前仅使用模拟数据）。
     *
     * @param newUserId 用户 ID。如果为 null，则默认为当前登录用户。
     */
    fun setUserId(newUserId: String?) {
        if (newUserId != userId) {
            userId = newUserId ?: meProfile.userId
        }
        // 简化的逻辑：如果是自己或自己的显示名称，则显示 meProfile，否则显示 colleagueProfile (模拟数据)
        _userData.value = if (userId == meProfile.userId || userId == meProfile.displayName) {
            meProfile
        } else {
            colleagueProfile
        }
    }

    private val _userData = MutableLiveData<ProfileScreenState>()
    /**
     * 可观察的用户资料数据。
     */
    val userData: LiveData<ProfileScreenState> = _userData
}

/**
 * 个人资料屏幕的 UI 状态。
 *
 * 包含显示用户详细信息所需的所有字段。
 * 标记为 @Immutable 以告知 Compose 编译器此类的实例在创建后不会更改，从而优化重组性能。
 *
 * @property userId 用户唯一标识符。
 * @property photo 头像资源 ID。
 * @property name 姓名。
 * @property status 在线状态或个性签名。
 * @property displayName 显示名称 (handle)。
 * @property position 职位描述。
 * @property twitter Twitter 链接。
 * @property timeZone 时区信息 (如果是查看自己则为 null)。
 * @property commonChannels 共同所在的频道数 (如果是查看自己则为 null)。
 */
@Immutable
data class ProfileScreenState(
    val userId: String,
    @DrawableRes val photo: Int?,
    val name: String,
    val status: String,
    val displayName: String,
    val position: String,
    val twitter: String = "",
    val timeZone: String?, // Null if me
    val commonChannels: String?, // Null if me
) {
    /**
     * 检查该资料是否属于当前登录用户。
     */
    fun isMe() = userId == meProfile.userId
}
