package com.example.star.aiwork.ai.util

import kotlinx.serialization.json.Json

/**
 * 全局共享的 JSON 序列化/反序列化配置实例。
 *
 * 配置如下：
 * - ignoreUnknownKeys = true: 反序列化时忽略 JSON 中存在但数据类中不存在的字段，增强兼容性。
 * - encodeDefaults = true: 序列化时即使字段值为默认值也将其包含在 JSON 中。
 * - prettyPrint = true: 输出格式化后的 JSON 字符串，便于调试阅读。
 * - isLenient = true: 允许更宽松的 JSON 解析规则（例如允许键名不带引号等）。
 */
val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
    isLenient = true
}
