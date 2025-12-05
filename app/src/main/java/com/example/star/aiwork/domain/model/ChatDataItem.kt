package com.example.star.aiwork.domain.model

/**
 * 表示聊天会话中的单个数据项的数据类。
 *
 * @property role 消息发送者的角色（例如，"user" 表示用户，"assistant" 表示 AI）。
 * @property content 消息的文本内容。
 * @property type 消息类型标识符（默认为 0，可用于区分文本、图像或其他类型的消息）。
 * @property localFilePath 本地文件路径（用于图片、音频等）。
 */
data class ChatDataItem(
    var role: String = "",
    var content: String = "",
    var type: Int = 0,
    var localFilePath: String? = null
)
