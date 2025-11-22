package com.example.star.aiwork.ai.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * 表示一个 AI 可用的工具定义。
 *
 * 这通常用于 Function Calling 功能，告诉 AI 有哪些外部函数可以调用。
 *
 * @property type 工具类型，通常为 "function"。
 * @property function 函数的具体定义。
 */
@Serializable
data class Tool(
    @SerialName("type")
    val type: String = "function",
    @SerialName("function")
    val function: Function
)

/**
 * 函数定义。
 *
 * @property name 函数名称。
 * @property description 函数描述，帮助 AI 理解何时使用此函数。
 * @property parameters 函数参数的 JSON Schema 定义，描述了参数结构、类型和是否必需。
 */
@Serializable
data class Function(
    @SerialName("name")
    val name: String,
    @SerialName("description")
    val description: String,
    @SerialName("parameters")
    val parameters: JsonObject
)
