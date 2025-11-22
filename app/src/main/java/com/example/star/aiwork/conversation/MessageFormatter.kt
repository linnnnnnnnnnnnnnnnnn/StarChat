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

package com.example.star.aiwork.conversation

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp

// 包含语法标记的正则表达式
// 匹配：URL链接, `代码块`, @用户, *加粗*, _斜体_, ~删除线~
val symbolPattern by lazy {
    Regex("""(https?://[^\s\t\n]+)|(`[^`]+`)|(@\w+)|(\*[\w]+\*)|(_[\w]+_)|(~[\w]+~)""")
}

// ClickableTextWrapper 接受的注解类型枚举
enum class SymbolAnnotationType {
    PERSON, // 用户提及
    LINK,   // URL 链接
}

// 字符串注解类型的别名，表示 AnnotatedString 中的一段范围
typealias StringAnnotation = AnnotatedString.Range<String>

// 匹配语法标记时返回的对，包含带样式的文本内容和可选的注解
typealias SymbolAnnotation = Pair<AnnotatedString, StringAnnotation?>

/**
 * 按照 Markdown-lite 语法格式化消息
 *
 * 支持的格式：
 * | @username -> 加粗，使用主色调，且为可点击元素
 * | http(s)://... -> 可点击链接，在浏览器中打开
 * | *bold* -> 加粗
 * | _italic_ -> 斜体
 * | ~strikethrough~ -> 删除线
 * | `MyClass.myMethod` -> 内联代码样式
 *
 * @param text 要解析的消息文本
 * @param primary 是否为主要消息（通常指发送者的消息），影响颜色选择
 * @return 带有注解的 AnnotatedString，用于 ClickableText wrapper
 */
@Composable
fun messageFormatter(text: String, primary: Boolean): AnnotatedString {
    // 查找所有匹配的语法标记
    val tokens = symbolPattern.findAll(text)

    return buildAnnotatedString {

        var cursorPosition = 0

        // 根据消息类型（主要/次要）确定代码块的背景颜色
        val codeSnippetBackground =
            if (primary) {
                MaterialTheme.colorScheme.secondary
            } else {
                MaterialTheme.colorScheme.surface
            }

        for (token in tokens) {
            // 追加当前标记之前的普通文本
            append(text.slice(cursorPosition until token.range.first))

            // 获取标记对应的带样式文本和注解
            val (annotatedString, stringAnnotation) = getSymbolAnnotation(
                matchResult = token,
                colorScheme = MaterialTheme.colorScheme,
                primary = primary,
                codeSnippetBackground = codeSnippetBackground,
            )
            append(annotatedString)

            // 如果有注解（如链接或用户提及），则添加到 AnnotatedString 中
            if (stringAnnotation != null) {
                val (item, start, end, tag) = stringAnnotation
                addStringAnnotation(tag = tag, start = start, end = end, annotation = item)
            }

            cursorPosition = token.range.last + 1
        }

        // 追加剩余的文本
        if (!tokens.none()) {
            append(text.slice(cursorPosition..text.lastIndex))
        } else {
            append(text)
        }
    }
}

/**
 * 将正则表达式匹配结果映射为支持的语法符号样式
 *
 * @param matchResult 正则表达式匹配结果
 * @param colorScheme 当前主题颜色方案
 * @param primary 是否为主要消息
 * @param codeSnippetBackground 代码块背景颜色
 * @return 一个 Pair，包含带样式的 AnnotatedString 和可选的注解（用于 ClickableText wrapper）
 */
private fun getSymbolAnnotation(
    matchResult: MatchResult,
    colorScheme: ColorScheme,
    primary: Boolean,
    codeSnippetBackground: Color,
): SymbolAnnotation {
    return when (matchResult.value.first()) {
        '@' -> SymbolAnnotation(
            AnnotatedString(
                text = matchResult.value,
                spanStyle = SpanStyle(
                    color = if (primary) colorScheme.inversePrimary else colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                ),
            ),
            StringAnnotation(
                item = matchResult.value.substring(1), // 去掉 @ 符号
                start = matchResult.range.first,
                end = matchResult.range.last,
                tag = SymbolAnnotationType.PERSON.name,
            ),
        )
        '*' -> SymbolAnnotation(
            AnnotatedString(
                text = matchResult.value.trim('*'),
                spanStyle = SpanStyle(fontWeight = FontWeight.Bold),
            ),
            null,
        )
        '_' -> SymbolAnnotation(
            AnnotatedString(
                text = matchResult.value.trim('_'),
                spanStyle = SpanStyle(fontStyle = FontStyle.Italic),
            ),
            null,
        )
        '~' -> SymbolAnnotation(
            AnnotatedString(
                text = matchResult.value.trim('~'),
                spanStyle = SpanStyle(textDecoration = TextDecoration.LineThrough),
            ),
            null,
        )
        '`' -> SymbolAnnotation(
            AnnotatedString(
                text = matchResult.value.trim('`'),
                spanStyle = SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    background = codeSnippetBackground,
                    baselineShift = BaselineShift(0.2f),
                ),
            ),
            null,
        )
        'h' -> SymbolAnnotation(
            AnnotatedString(
                text = matchResult.value,
                spanStyle = SpanStyle(
                    color = if (primary) colorScheme.inversePrimary else colorScheme.primary,
                ),
            ),
            StringAnnotation(
                item = matchResult.value,
                start = matchResult.range.first,
                end = matchResult.range.last,
                tag = SymbolAnnotationType.LINK.name,
            ),
        )
        else -> SymbolAnnotation(AnnotatedString(matchResult.value), null)
    }
}
