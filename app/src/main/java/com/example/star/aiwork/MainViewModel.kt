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

package com.example.star.aiwork

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.star.aiwork.ai.provider.ProviderSetting
import com.example.star.aiwork.data.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 主 ViewModel，负责在屏幕之间共享数据和通信。
 *
 * 管理全局应用状态，包括：
 * - 侧边栏 (Drawer) 的开闭状态。
 * - 用户配置的 AI 提供商设置 [ProviderSetting]。
 * - 全局 AI 模型参数 (Temperature, Max Tokens, Stream Response)。
 *
 * @property userPreferencesRepository 用户偏好仓库，用于持久化数据的读写。
 */
class MainViewModel(
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    // 侧边栏状态流
    private val _drawerShouldBeOpened = MutableStateFlow(false)
    val drawerShouldBeOpened = _drawerShouldBeOpened.asStateFlow()

    /**
     * AI 提供商设置列表流。
     * 从 Repository 加载，并在 ViewModel 作用域内共享。
     */
    val providerSettings: StateFlow<List<ProviderSetting>> = userPreferencesRepository.providerSettings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * AI 温度参数流。
     */
    val temperature: StateFlow<Float> = userPreferencesRepository.temperature
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0.7f
        )

    /**
     * AI 最大 Token 数流。
     */
    val maxTokens: StateFlow<Int> = userPreferencesRepository.maxTokens
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 2000
        )

    /**
     * AI 流式响应开关流。
     */
    val streamResponse: StateFlow<Boolean> = userPreferencesRepository.streamResponse
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    /**
     * 请求打开侧边栏。
     */
    fun openDrawer() {
        _drawerShouldBeOpened.value = true
    }

    /**
     * 重置侧边栏打开请求状态。
     * 应在 UI 响应打开动作后调用。
     */
    fun resetOpenDrawerAction() {
        _drawerShouldBeOpened.value = false
    }
    
    /**
     * 更新并持久化提供商设置列表。
     */
    fun updateProviderSettings(newSettings: List<ProviderSetting>) {
        viewModelScope.launch {
            userPreferencesRepository.updateProviderSettings(newSettings)
        }
    }

    /**
     * 更新并持久化温度参数。
     */
    fun updateTemperature(newTemperature: Float) {
        viewModelScope.launch {
            userPreferencesRepository.updateTemperature(newTemperature)
        }
    }

    /**
     * 更新并持久化最大 Token 数。
     */
    fun updateMaxTokens(newMaxTokens: Int) {
        viewModelScope.launch {
            userPreferencesRepository.updateMaxTokens(newMaxTokens)
        }
    }

    /**
     * 更新并持久化流式响应开关。
     */
    fun updateStreamResponse(newStreamResponse: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.updateStreamResponse(newStreamResponse)
        }
    }

    /**
     * ViewModel 工厂，用于手动注入依赖项 (UserPreferencesRepository)。
     *
     * 在未使用 Hilt 等依赖注入框架时，通过此工厂从 CreationExtras 中获取 Application 实例，
     * 并创建 Repository 和 ViewModel。
     */
    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras
            ): T {
                // 从 extras 中获取 Application 对象
                val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                
                return MainViewModel(
                    UserPreferencesRepository(application)
                ) as T
            }
        }
    }
}
