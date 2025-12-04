package com.example.star.aiwork.domain.model

import androidx.compose.runtime.Composable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.util.UUID

/**
 * 提供商设置 (Provider Setting)。
 * 定义 AI 提供商的配置信息，包括认证、API 地址、模型列表等。
 * 这是一个密封类，具体的提供商实现（如 OpenAI, Google）继承自此类。
 *
 * @property id 提供商设置的唯一标识。
 * @property enabled 是否启用该提供商。
 * @property name 提供商显示名称。
 * @property models 该提供商下配置的模型列表。
 * @property proxy 网络代理设置。
 * @property balanceOption 余额查询设置。
 * @property builtIn 是否为内置提供商（不可删除）。
 * @property description 详细描述（Composable UI）。
 * @property shortDescription 简短描述（Composable UI）。
 */
@Serializable
sealed class ProviderSetting {
    abstract val id: String
    abstract val enabled: Boolean
    abstract val name: String
    abstract val models: List<Model>
    abstract val proxy: ProviderProxy
    abstract val balanceOption: BalanceOption

    abstract val builtIn: Boolean
    @Transient open val description: @Composable () -> Unit = {}
    @Transient open val shortDescription: @Composable () -> Unit = {}

    abstract fun addModel(model: Model): ProviderSetting
    abstract fun editModel(model: Model): ProviderSetting
    abstract fun delModel(model: Model): ProviderSetting
    abstract fun moveMove(from: Int, to: Int): ProviderSetting
    abstract fun copyProvider(
        id: String = this.id,
        enabled: Boolean = this.enabled,
        name: String = this.name,
        models: List<Model> = this.models,
        proxy: ProviderProxy = this.proxy,
        balanceOption: BalanceOption = this.balanceOption,
        builtIn: Boolean = this.builtIn,
        description: @Composable (() -> Unit) = this.description,
        shortDescription: @Composable (() -> Unit) = this.shortDescription,
    ): ProviderSetting

    interface OpenAICompatible {
        val id: String
        val apiKey: String
        val baseUrl: String
        val chatCompletionsPath: String
        val proxy: ProviderProxy
    }

    /**
     * OpenAI 兼容提供商设置。
     * 适用于官方 OpenAI API 以及所有兼容 OpenAI 接口格式的第三方服务（如 DeepSeek, Moonshot 等）。
     */
    @Serializable
    @SerialName("openai")
    data class OpenAI(
        override var id: String = UUID.randomUUID().toString(),
        override var enabled: Boolean = true,
        override var name: String = "OpenAI",
        override var models: List<Model> = emptyList(),
        override var proxy: ProviderProxy = ProviderProxy.None,
        override val balanceOption: BalanceOption = BalanceOption(),
        @Transient override val builtIn: Boolean = false,
        @Transient override val description: @Composable (() -> Unit) = {},
        @Transient override val shortDescription: @Composable (() -> Unit) = {},
        override var apiKey: String = "",
        override var baseUrl: String = "https://api.openai.com/v1",
        override var chatCompletionsPath: String = "/chat/completions",
        var useResponseApi: Boolean = false,
    ) : ProviderSetting(), OpenAICompatible {
        override fun addModel(model: Model): ProviderSetting {
            return copy(models = models + model)
        }

        override fun editModel(model: Model): ProviderSetting {
            return copy(models = models.map { if (it.id == model.id) model.copy() else it })
        }

        override fun delModel(model: Model): ProviderSetting {
            return copy(models = models.filter { it.id != model.id })
        }

        override fun moveMove(
            from: Int,
            to: Int
        ): ProviderSetting {
            return copy(models = models.toMutableList().apply {
                val model = removeAt(from)
                add(to, model)
            })
        }

        override fun copyProvider(
            id: String,
            enabled: Boolean,
            name: String,
            models: List<Model>,
            proxy: ProviderProxy,
            balanceOption: BalanceOption,
            builtIn: Boolean,
            description: @Composable (() -> Unit),
            shortDescription: @Composable (() -> Unit),
        ): ProviderSetting {
            return this.copy(
                id = id,
                enabled = enabled,
                name = name,
                models = models,
                builtIn = builtIn,
                description = description,
                proxy = proxy,
                balanceOption = balanceOption,
                shortDescription = shortDescription
            )
        }
    }

    /**
     * Ollama 提供商设置。
     * 专用于接入本地 Ollama 服务。
     */
    @Serializable
    @SerialName("ollama")
    data class Ollama(
        override var id: String = UUID.randomUUID().toString(),
        override var enabled: Boolean = true,
        override var name: String = "Ollama",
        override var models: List<Model> = emptyList(),
        override var proxy: ProviderProxy = ProviderProxy.None,
        override val balanceOption: BalanceOption = BalanceOption(),
        @Transient override val builtIn: Boolean = false,
        @Transient override val description: @Composable (() -> Unit) = {},
        @Transient override val shortDescription: @Composable (() -> Unit) = {},
        override var apiKey: String = "ollama",
        override var baseUrl: String = "http://172.16.48.147:8080",
        override var chatCompletionsPath: String = "/api/chat",
    ) : ProviderSetting(), OpenAICompatible {
        override fun addModel(model: Model): ProviderSetting {
            return copy(models = models + model)
        }

        override fun editModel(model: Model): ProviderSetting {
            return copy(models = models.map { if (it.id == model.id) model.copy() else it })
        }

        override fun delModel(model: Model): ProviderSetting {
            return copy(models = models.filter { it.id != model.id })
        }

        override fun moveMove(
            from: Int,
            to: Int
        ): ProviderSetting {
            return copy(models = models.toMutableList().apply {
                val model = removeAt(from)
                add(to, model)
            })
        }

        override fun copyProvider(
            id: String,
            enabled: Boolean,
            name: String,
            models: List<Model>,
            proxy: ProviderProxy,
            balanceOption: BalanceOption,
            builtIn: Boolean,
            description: @Composable (() -> Unit),
            shortDescription: @Composable (() -> Unit),
        ): ProviderSetting {
            return this.copy(
                id = id,
                enabled = enabled,
                name = name,
                models = models,
                builtIn = builtIn,
                description = description,
                shortDescription = shortDescription,
                proxy = proxy,
                balanceOption = balanceOption
            )
        }
    }

    /**
     * Google (Gemini/Vertex AI) 提供商设置。
     */
    @Serializable
    @SerialName("google")
    data class Google(
        override var id: String = UUID.randomUUID().toString(),
        override var enabled: Boolean = true,
        override var name: String = "Google",
        override var models: List<Model> = emptyList(),
        override var proxy: ProviderProxy = ProviderProxy.None,
        override val balanceOption: BalanceOption = BalanceOption(),
        @Transient override val builtIn: Boolean = false,
        @Transient override val description: @Composable (() -> Unit) = {},
        @Transient override val shortDescription: @Composable (() -> Unit) = {},
        var apiKey: String = "",
        var baseUrl: String = "https://generativelanguage.googleapis.com/v1beta",
        var vertexAI: Boolean = false,
        var privateKey: String = "",
        var serviceAccountEmail: String = "",
        var location: String = "us-central1",
        var projectId: String = "",
    ) : ProviderSetting() {
        override fun addModel(model: Model): ProviderSetting {
            return copy(models = models + model)
        }

        override fun editModel(model: Model): ProviderSetting {
            return copy(models = models.map { if (it.id == model.id) model.copy() else it })
        }

        override fun delModel(model: Model): ProviderSetting {
            return copy(models = models.filter { it.id != model.id })
        }

        override fun moveMove(
            from: Int,
            to: Int
        ): ProviderSetting {
            return copy(models = models.toMutableList().apply {
                val model = removeAt(from)
                add(to, model)
            })
        }

        override fun copyProvider(
            id: String,
            enabled: Boolean,
            name: String,
            models: List<Model>,
            proxy: ProviderProxy,
            balanceOption: BalanceOption,
            builtIn: Boolean,
            description: @Composable (() -> Unit),
            shortDescription: @Composable (() -> Unit),
        ): ProviderSetting {
            return this.copy(
                id = id,
                enabled = enabled,
                name = name,
                models = models,
                builtIn = builtIn,
                description = description,
                shortDescription = shortDescription,
                proxy = proxy,
                balanceOption = balanceOption
            )
        }
    }

    /**
     * Anthropic (Claude) 提供商设置。
     */
    @Serializable
    @SerialName("claude")
    data class Claude(
        override var id: String = UUID.randomUUID().toString(),
        override var enabled: Boolean = true,
        override var name: String = "Claude",
        override var models: List<Model> = emptyList(),
        override var proxy: ProviderProxy = ProviderProxy.None,
        override val balanceOption: BalanceOption = BalanceOption(),
        @Transient override val builtIn: Boolean = false,
        @Transient override val description: @Composable (() -> Unit) = {},
        @Transient override val shortDescription: @Composable (() -> Unit) = {},
        var apiKey: String = "",
        var baseUrl: String = "https://api.anthropic.com/v1",
    ) : ProviderSetting() {
        override fun addModel(model: Model): ProviderSetting {
            return copy(models = models + model)
        }

        override fun editModel(model: Model): ProviderSetting {
            return copy(models = models.map { if (it.id == model.id) model.copy() else it })
        }

        override fun delModel(model: Model): ProviderSetting {
            return copy(models = models.filter { it.id != model.id })
        }

        override fun moveMove(
            from: Int,
            to: Int
        ): ProviderSetting {
            return copy(models = models.toMutableList().apply {
                val model = removeAt(from)
                add(to, model)
            })
        }

        override fun copyProvider(
            id: String,
            enabled: Boolean,
            name: String,
            models: List<Model>,
            proxy: ProviderProxy,
            balanceOption: BalanceOption,
            builtIn: Boolean,
            description: @Composable (() -> Unit),
            shortDescription: @Composable (() -> Unit),
        ): ProviderSetting {
            return this.copy(
                id = id,
                enabled = enabled,
                name = name,
                models = models,
                proxy = proxy,
                balanceOption = balanceOption,
                builtIn = builtIn,
                description = description,
                shortDescription = shortDescription,
            )
        }
    }

    /**
     * Dify 提供商设置。
     */
    @Serializable
    @SerialName("dify")
    data class Dify(
        override var id: String = UUID.randomUUID().toString(),
        override var enabled: Boolean = true,
        override var name: String = "Dify",
        override var models: List<Model> = emptyList(),
        override var proxy: ProviderProxy = ProviderProxy.None,
        override val balanceOption: BalanceOption = BalanceOption(),
        @Transient override val builtIn: Boolean = false,
        @Transient override val description: @Composable (() -> Unit) = {},
        @Transient override val shortDescription: @Composable (() -> Unit) = {},
        var apiKey: String = "",
        var baseUrl: String = "https://api.dify.ai/v1",
        var botType: DifyBotType = DifyBotType.Chat,
        var inputVariable: String = "",
        var outputVariable: String = ""
    ) : ProviderSetting() {
        override fun addModel(model: Model): ProviderSetting {
            return copy(models = models + model)
        }

        override fun editModel(model: Model): ProviderSetting {
            return copy(models = models.map { if (it.id == model.id) model.copy() else it })
        }

        override fun delModel(model: Model): ProviderSetting {
            return copy(models = models.filter { it.id != model.id })
        }

        override fun moveMove(
            from: Int,
            to: Int
        ): ProviderSetting {
            return copy(models = models.toMutableList().apply {
                val model = removeAt(from)
                add(to, model)
            })
        }

        override fun copyProvider(
            id: String,
            enabled: Boolean,
            name: String,
            models: List<Model>,
            proxy: ProviderProxy,
            balanceOption: BalanceOption,
            builtIn: Boolean,
            description: @Composable (() -> Unit),
            shortDescription: @Composable (() -> Unit),
        ): ProviderSetting {
            return this.copy(
                id = id,
                enabled = enabled,
                name = name,
                models = models,
                proxy = proxy,
                balanceOption = balanceOption,
                builtIn = builtIn,
                description = description,
                shortDescription = shortDescription,
            )
        }
    }

    @Serializable
    enum class DifyBotType {
        Chat, Completion, Workflow
    }

    companion object {
        val Types by lazy {
            listOf(
                OpenAI::class,
                Ollama::class,
                Google::class,
                Claude::class,
                Dify::class,
            )
        }
    }
}
