package com.example.star.aiwork.ui.ai

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import com.example.star.aiwork.domain.model.MessageRole
import com.example.star.aiwork.domain.model.TokenUsage
import com.example.star.aiwork.domain.model.Model
import com.example.star.aiwork.infra.util.json
import java.util.UUID

/**
 * 表示用户界面中显示的一条消息。
 *
 * 这是一个通用的消息数据模型，可以包含多种类型的内容部分（如文本、图片、工具调用等）。
 * 它被设计为可序列化，以便于存储和传输。具体的 AI 提供商实现会将此模型转换为其 API 所需的数据传输对象 (DTO)。
 *
 * @property id 消息的唯一标识符，默认为生成的 UUID。
 * @property role 消息的角色（如用户、助手、系统）。
 * @property parts 消息的内容部分列表，支持多模态内容。
 * @property annotations 消息的注释列表（如引用来源）。
 * @property createdAt 消息创建时间。
 * @property modelId 生成该消息的 AI 模型 ID（如果是助手消息）。
 * @property usage 该消息消耗的 Token 使用情况（如果是助手消息）。
 * @property translation 消息的翻译文本（可选）。
 */
@Serializable
data class UIMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: MessageRole,
    val parts: List<UIMessagePart>,
    val annotations: List<UIMessageAnnotation> = emptyList(),
    val createdAt: LocalDateTime = Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault()),
    val modelId: String? = null,
    val usage: TokenUsage? = null,
    val translation: String? = null
) {
    /**
     * 将接收到的消息块 (MessageChunk) 追加到当前消息中。
     *
     * 这用于流式响应处理，将增量内容合并到当前消息中。
     *
     * @param chunk 要追加的消息块。
     * @return 更新后的新 UIMessage 对象。
     */
    private fun appendChunk(chunk: MessageChunk): UIMessage {
        val choice = chunk.choices.getOrNull(0)
        return choice?.delta?.let { delta ->
            // 处理 Parts (内容部分)
            var newParts = delta.parts.fold(parts) { acc, deltaPart ->
                when (deltaPart) {
                    is UIMessagePart.Text -> {
                        // 如果存在文本部分，则追加文本；否则添加新的文本部分
                        val existingTextPart =
                            acc.find { it is UIMessagePart.Text } as? UIMessagePart.Text
                        if (existingTextPart != null) {
                            acc.map { part ->
                                if (part is UIMessagePart.Text) {
                                    UIMessagePart.Text(existingTextPart.text + deltaPart.text)
                                } else part
                            }
                        } else {
                            acc + deltaPart
                        }
                    }

                    is UIMessagePart.Image -> {
                        // 如果存在图像部分，则追加 URL (通常用于 Base64 编码的流)；否则添加新的图像部分
                        val existingImagePart =
                            acc.find { it is UIMessagePart.Image } as? UIMessagePart.Image
                        if (existingImagePart != null) {
                            acc.map { part ->
                                if (part is UIMessagePart.Image) {
                                    UIMessagePart.Image(
                                        url = existingImagePart.url + deltaPart.url,
                                        originalUri = existingImagePart.originalUri
                                    )
                                } else part
                            }
                        } else {
                            acc + UIMessagePart.Image(
                                url = "data:image/png;base64,${deltaPart.url}",
                                originalUri = deltaPart.originalUri
                            )
                        }
                    }

                    is UIMessagePart.Reasoning -> {
                        // 处理推理过程 (Reasoning)
                        val existingReasoningPart =
                            acc.find { it is UIMessagePart.Reasoning } as? UIMessagePart.Reasoning
                        if (existingReasoningPart != null) {
                            acc.map { part ->
                                if (part is UIMessagePart.Reasoning) {
                                    UIMessagePart.Reasoning(
                                        reasoning = existingReasoningPart.reasoning + deltaPart.reasoning,
                                        createdAt = existingReasoningPart.createdAt,
                                        finishedAt = null,
                                    ).also {
                                        if (deltaPart.metadata != null) {
                                            it.metadata = deltaPart.metadata // 更新元数据
                                            println("更新metadata: ${json.encodeToString(deltaPart)}")
                                        }
                                    }
                                } else part
                            }
                        } else {
                            acc + deltaPart
                        }
                    }

                    is UIMessagePart.ToolCall -> {
                        // 处理工具调用
                        if (deltaPart.toolCallId.isBlank()) {
                            // 如果没有 ID，尝试合并到最后一个工具调用
                            val lastToolCall =
                                acc.lastOrNull { it is UIMessagePart.ToolCall } as? UIMessagePart.ToolCall
                            if (lastToolCall == null || lastToolCall.toolCallId.isBlank()) {
                                acc + deltaPart.copy()
                            } else {
                                acc.map { part ->
                                    if (part == lastToolCall && part is UIMessagePart.ToolCall) {
                                        part.merge(deltaPart)
                                    } else part
                                }
                            }
                        } else {
                            // 有 ID，插入或更新
                            val existsPart = acc.find {
                                it is UIMessagePart.ToolCall && it.toolCallId == deltaPart.toolCallId
                            } as? UIMessagePart.ToolCall
                            if (existsPart == null) {
                                // 插入
                                acc + deltaPart.copy()
                            } else {
                                // 更新
                                acc.map { part ->
                                    if (part is UIMessagePart.ToolCall && part.toolCallId == deltaPart.toolCallId) {
                                        part.merge(deltaPart)
                                    } else part
                                }
                            }
                        }
                    }

                    else -> {
                        println("delta part append not supported: $deltaPart")
                        acc
                    }
                }
            }
            // 处理推理结束 (Reasoning End)
            if (parts.filterIsInstance<UIMessagePart.Reasoning>()
                    .isNotEmpty() && delta.parts.filterIsInstance<UIMessagePart.Reasoning>()
                    .isEmpty()
            ) {
                newParts = newParts.map { part ->
                    if (part is UIMessagePart.Reasoning && part.finishedAt == null) {
                        part.copy(finishedAt = Clock.System.now())
                    } else part
                }
            }
            // 处理注释 (annotations)
            val newAnnotations = delta.annotations.ifEmpty {
                annotations
            }
            copy(
                parts = newParts,
                annotations = newAnnotations,
            )
        } ?: this
    }

    /**
     * 将消息摘要为文本格式，包含角色前缀。
     */
    fun summaryAsText(): String {
        return "[${role.name}]: " + parts.joinToString(separator = "\n") { part ->
            when (part) {
                is UIMessagePart.Text -> part.text
                else -> ""
            }
        }
    }

    /**
     * 将消息内容转换为纯文本。
     */
    fun toText() = parts.joinToString(separator = "\n") { part ->
        when (part) {
            is UIMessagePart.Text -> part.text
            else -> ""
        }
    }

    /**
     * 获取消息中的所有工具调用部分。
     */
    fun getToolCalls() = parts.filterIsInstance<UIMessagePart.ToolCall>()

    /**
     * 获取消息中的所有工具执行结果部分。
     */
    fun getToolResults() = parts.filterIsInstance<UIMessagePart.ToolResult>()

    /**
     * 检查消息是否有效以上传（不包含未完成的推理部分等）。
     */
    fun isValidToUpload() = parts.any {
        it !is UIMessagePart.Reasoning
    }

    /**
     * 检查消息是否包含指定类型的 Part。
     */
    inline fun <reified P : UIMessagePart> hasPart(): Boolean {
        return parts.any {
            it is P
        }
    }

    /**
     * 运算符重载，允许使用 `+` 号将 MessageChunk 追加到消息。
     */
    operator fun plus(chunk: MessageChunk): UIMessage {
        return this.appendChunk(chunk)
    }

    companion object {
        fun system(prompt: String) = UIMessage(
            role = MessageRole.SYSTEM,
            parts = listOf(UIMessagePart.Text(prompt))
        )

        fun user(prompt: String) = UIMessage(
            role = MessageRole.USER,
            parts = listOf(UIMessagePart.Text(prompt))
        )

        fun assistant(prompt: String) = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(UIMessagePart.Text(prompt))
        )
    }
}

/**
 * 处理 MessageChunk 合并到消息列表中。
 *
 * 如果列表最后一条消息的角色与 Chunk 的角色相同，则合并到最后一条消息；
 * 否则，添加一条新消息。
 *
 * @receiver 已有消息列表
 * @param chunk 消息数据块
 * @param model 模型信息, 可以不传。如果传了，会把模型 ID 写入到消息，标记是哪个模型输出的消息
 * @return 更新后的新消息列表
 */
fun List<UIMessage>.handleMessageChunk(chunk: MessageChunk, model: Model? = null): List<UIMessage> {
    require(this.isNotEmpty()) {
        "messages must not be empty"
    }
    val choice = chunk.choices.getOrNull(0) ?: return this
    val message = choice.delta ?: choice.message ?: throw Exception("delta/message is null")
    if (this.last().role != message.role) {
        return this + message.copy(modelId = model?.id)
    } else {
        val last = this.last() + chunk
        return this.dropLast(1) + last
    }
}

/**
 * 判断这个消息部分列表是否包含任何用户**可输入内容**。
 *
 * 检查是否包含非空的文本、图片、文档、视频或音频。
 * 例如: 文本，图片, 文档
 */
fun List<UIMessagePart>.isEmptyInputMessage(): Boolean {
    if (this.isEmpty()) return true
    return this.all { message ->
        when (message) {
            is UIMessagePart.Text -> message.text.isBlank()
            is UIMessagePart.Image -> message.url.isBlank()
            is UIMessagePart.Document -> message.url.isBlank()
            is UIMessagePart.Video -> message.url.isBlank()
            is UIMessagePart.Audio -> message.url.isBlank()
            else -> true
        }
    }
}

/**
 * 判断这个消息部分列表在 UI 上是否显示任何内容。
 *
 * 除了常规内容外，还检查推理部分是否为空。
 */
fun List<UIMessagePart>.isEmptyUIMessage(): Boolean {
    if (this.isEmpty()) return true
    return this.all { message ->
        when (message) {
            is UIMessagePart.Text -> message.text.isBlank()
            is UIMessagePart.Image -> message.url.isBlank()
            is UIMessagePart.Document -> message.url.isBlank()
            is UIMessagePart.Reasoning -> message.reasoning.isBlank()
            is UIMessagePart.Video -> message.url.isBlank()
            is UIMessagePart.Audio -> message.url.isBlank()
            else -> true
        }
    }
}

/**
 * 截断消息列表，保留从指定索引开始的部分。
 */
fun List<UIMessage>.truncate(index: Int): List<UIMessage> {
    if (index < 0 || index > this.lastIndex) return this
    return this.subList(index, this.size)
}

/**
 * 限制上下文大小，确保保留一定数量的消息。
 *
 * 此函数会尝试智能地截断消息列表，以保留最近的 [size] 条消息。
 * 它会处理工具调用 (Tool Call) 和工具结果 (Tool Result) 的依赖关系，
 * 确保不会切断一个完整的工具交互流程。
 *
 * @param size 期望保留的消息数量。
 */
fun List<UIMessage>.limitContext(size: Int): List<UIMessage> {
    if (size <= 0 || this.size <= size) return this

    val startIndex = this.size - size
    var adjustedStartIndex = startIndex

    // 循环往前查找，直到满足所有依赖条件
    var needsAdjustment = true
    val visitedIndices = mutableSetOf<Int>()

    while (needsAdjustment && adjustedStartIndex > 0) {
        needsAdjustment = false

        // 防止无限循环
        if (adjustedStartIndex in visitedIndices) break
        visitedIndices.add(adjustedStartIndex)

        val currentMessage = this[adjustedStartIndex]

        // 如果当前消息包含 tool result，往前查找对应的 tool call
        if (currentMessage.getToolResults().isNotEmpty()) {
            for (i in adjustedStartIndex - 1 downTo 0) {
                if (this[i].getToolCalls().isNotEmpty()) {
                    adjustedStartIndex = i
                    needsAdjustment = true
                    break
                }
            }
        }

        // 如果当前消息包含 tool call，往前查找对应的用户消息
        if (currentMessage.getToolCalls().isNotEmpty()) {
            for (i in adjustedStartIndex - 1 downTo 0) {
                if (this[i].role == MessageRole.USER) {
                    adjustedStartIndex = i
                    needsAdjustment = true
                    break
                }
            }
        }
    }

    return this.subList(adjustedStartIndex, this.size)
}

/**
 * 密封类，表示消息的不同组成部分。
 */
@Serializable
sealed class UIMessagePart {
    abstract val priority: Int
    abstract val metadata: JsonObject?

    @Serializable
    data class Text(
        val text: String,
        override var metadata: JsonObject? = null
    ) : UIMessagePart() {
        override val priority: Int = 0
    }

    @Serializable
    data class Image(
        val url: String,
        val originalUri: String? = null,
        override var metadata: JsonObject? = null
    ) : UIMessagePart() {
        override val priority: Int = 1
    }

    @Serializable
    data class Video(
        val url: String,
        override var metadata: JsonObject? = null
    ) : UIMessagePart() {
        override val priority: Int = 1
    }

    @Serializable
    data class Audio(
        val url: String,
        override var metadata: JsonObject? = null
    ) : UIMessagePart() {
        override val priority: Int = 1
    }

    @Serializable
    data class Document(
        val url: String,
        val fileName: String,
        val mime: String = "text/*",
        override var metadata: JsonObject? = null
    ) : UIMessagePart() {
        override val priority: Int = 1
    }

    @Serializable
    data class Reasoning(
        val reasoning: String,
        val createdAt: Instant = Clock.System.now(),
        val finishedAt: Instant? = Clock.System.now(),
        override var metadata: JsonObject? = null
    ) : UIMessagePart() {
        override val priority: Int = -1
    }

    @Deprecated("Deprecated")
    @Serializable
    data object Search : UIMessagePart() {
        override val priority: Int = 0
        override var metadata: JsonObject? = null
    }

    @Serializable
    data class ToolCall(
        val toolCallId: String,
        val toolName: String,
        val arguments: String,
        override var metadata: JsonObject? = null
    ) : UIMessagePart() {
        fun merge(other: ToolCall): ToolCall {
            return ToolCall(
                toolCallId = toolCallId,
                toolName = toolName + other.toolName,
                arguments = arguments + other.arguments,
                metadata = if(other.metadata != null) other.metadata else metadata,
            )
        }

        override val priority: Int = 0
    }

    @Serializable
    data class ToolResult(
        val toolCallId: String,
        val toolName: String,
        val content: JsonElement,
        val arguments: JsonElement,
        override var metadata: JsonObject? = null
    ) : UIMessagePart() {
        override val priority: Int = 0
    }
}

/**
 * 根据优先级对消息部分进行排序。
 */
fun List<UIMessagePart>.toSortedMessageParts(): List<UIMessagePart> {
    return sortedBy { it.priority }
}

/**
 * 标记消息中的推理过程为完成状态。
 */
fun UIMessage.finishReasoning(): UIMessage {
    return copy(
        parts = parts.map { part ->
            when (part) {
                is UIMessagePart.Reasoning -> {
                    if (part.finishedAt == null) {
                        part.copy(
                            finishedAt = Clock.System.now()
                        )
                    } else {
                        part
                    }
                }

                else -> part
            }
        }
    )
}

/**
 * 消息注释的密封类。
 */
@Serializable
sealed class UIMessageAnnotation {
    @Serializable
    @SerialName("url_citation")
    data class UrlCitation(
        val title: String,
        val url: String
    ) : UIMessageAnnotation()
}

/**
 * 表示从 API 接收到的消息数据块 (Chunk)。
 *
 * 通常用于流式响应。
 */
@Serializable
data class MessageChunk(
    val id: String,
    val model: String,
    val choices: List<UIMessageChoice>,
    val usage: TokenUsage? = null,
)

/**
 * 消息数据块中的选项。
 */
@Serializable
data class UIMessageChoice(
    val index: Int,
    val delta: UIMessage?,
    val message: UIMessage?,
    val finishReason: String?
)
