package com.example.star.aiwork.ai.ui

import kotlinx.serialization.Serializable

/**
 * 图像生成结果的数据类。
 *
 * @property items 生成的图像项列表，每个项代表一张图片。
 */
@Serializable
data class ImageGenerationResult(
    val items: List<ImageGenerationItem>, // 一个item代表一个图片
)

/**
 * 单个生成的图像项。
 *
 * @property data 图像数据，通常是 URL 或 Base64 编码的字符串。
 * @property mimeType 图像的 MIME 类型 (例如 "image/png")。
 */
@Serializable
data class ImageGenerationItem(
    val data: String,
    val mimeType: String,
)

/**
 * 图像生成的纵横比选项。
 */
@Serializable
enum class ImageAspectRatio {
    SQUARE,    // 正方形 (1:1)
    LANDSCAPE, // 横向
    PORTRAIT   // 纵向
}
