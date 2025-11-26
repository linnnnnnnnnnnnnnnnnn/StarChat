package com.example.star.aiwork.data.provider

import com.example.star.aiwork.domain.Provider
import com.example.star.aiwork.domain.model.ProviderSetting
import okhttp3.OkHttpClient

object ProviderFactory {
    fun getProvider(setting: ProviderSetting, client: OkHttpClient): Provider<out ProviderSetting> {
        return when (setting) {
            is ProviderSetting.OpenAI -> OpenAIProvider(client)
            is ProviderSetting.Ollama -> OllamaProvider(client)
            else -> OpenAIProvider(client)
        }
    }
}
