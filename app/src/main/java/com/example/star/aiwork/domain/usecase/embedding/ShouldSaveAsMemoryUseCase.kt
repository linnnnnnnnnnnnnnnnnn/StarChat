package com.example.star.aiwork.domain.usecase.embedding

/**
 * 判断文本是否应该保存为长期记忆的用例。
 * 
 * 包含记忆触发的业务规则：
 * - 显式触发词（如"记住"、"帮我记"等）
 * - 身份模式（如"我叫..."、"我是..."等）
 * - 偏好模式（如"我喜欢..."、"我希望你..."等）
 * - 长期目标模式（如"我想在未来..."、"我计划..."等）
 */
class ShouldSaveAsMemoryUseCase {
    
    companion object {
        /**
         * 显式触发词列表
         */
        private val EXPLICIT_TRIGGERS = listOf(
            "记住", "帮我记", "加入记忆", "牢记",
            "以后你都", "永远记", "保存到记忆"
        )

        /**
         * 身份模式（正则表达式）
         */
        private val IDENTITY_PATTERNS = listOf(
            Regex("我叫(.+?)"),
            Regex("我是(.+?)"),
            Regex("我住在(.+?)"),
            Regex("我来自(.+?)"),
            Regex("我的职业是(.+?)")
        )

        /**
         * 偏好模式（正则表达式）
         */
        private val PREFERENCE_PATTERNS = listOf(
            Regex("我喜欢(.+?)"),
            Regex("i like(.+?)"),
            Regex("我更喜欢(.+?)"),
            Regex("我希望你(.+?)"),
            Regex("以后请你(.+?)"),
            Regex("你以后回答我(.+?)")
        )

        /**
         * 长期目标模式（正则表达式）
         */
        private val LONG_TERM_GOALS = listOf(
            Regex("我想在未来(.+?)"),
            Regex("我接下来(.+?)"),
            Regex("我计划(.+?)"),
            Regex("我打算(.+?)"),
            Regex("我的目标是(.+?)")
        )
    }

    /**
     * 检查输入文本是否匹配任何记忆触发模式
     * 
     * @param text 用户输入的文本
     * @return 如果匹配则返回 true，否则返回 false
     */
    operator fun invoke(text: String): Boolean {
        if (text.isBlank()) {
            return false
        }
        
        val trimmedText = text.trim()
        
        // 检查显式触发词
        if (EXPLICIT_TRIGGERS.any { trigger -> trimmedText.contains(trigger) }) {
            return true
        }
        
        // 检查身份模式
        if (IDENTITY_PATTERNS.any { pattern -> pattern.containsMatchIn(trimmedText) }) {
            return true
        }
        
        // 检查偏好模式
        if (PREFERENCE_PATTERNS.any { pattern -> pattern.containsMatchIn(trimmedText) }) {
            return true
        }
        
        // 检查长期目标模式
        if (LONG_TERM_GOALS.any { pattern -> pattern.containsMatchIn(trimmedText) }) {
            return true
        }
        
        return false
    }
}



