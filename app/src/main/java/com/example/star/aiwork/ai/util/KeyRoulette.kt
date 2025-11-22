package com.example.star.aiwork.ai.util

/**
 * API Key 轮盘赌（负载均衡器）。
 *
 * 用于管理多个 API Key，并在请求时随机选择一个，以实现简单的负载均衡和规避速率限制。
 */
class KeyRoulette {
    /**
     * 获取下一个可用的 API Key。
     *
     * @param apiKey 输入的原始 Key 字符串，可以包含多个 Key，使用逗号或换行符分隔。
     * @return 随机选中的单个 API Key。如果输入为空，返回空字符串。
     */
    fun next(apiKey: String): String {
        // 如果 Key 包含多个由逗号或换行符分隔的 Key，则进行轮换。
        if (apiKey.isBlank()) return ""
        val keys = apiKey.split(Regex("[,\\n]")).map { it.trim() }.filter { it.isNotEmpty() }
        if (keys.isEmpty()) return ""
        // 随机选择一个进行负载均衡
        return keys.random()
    }

    companion object {
        /**
         * 获取默认的 KeyRoulette 实例。
         */
        fun default() = KeyRoulette()
    }
}
