package com.watermarkcamera.watermark

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 水印样式定义 - 中老年友好设计
 */
object WatermarkStyle {
    // 字体大小 - 中老年友好：大字体
    val FONT_SIZE = 28f
    val LINE_SPACING = 12f

    // 颜色 - 高对比度
    val TEXT_COLOR: Color = Color.White
    val BACKGROUND_COLOR: Color = Color.Black.copy(alpha = 0.5f)

    // 尺寸
    val CORNER_RADIUS = 12f
    val PADDING = 24f
    val MARGIN_FROM_EDGE = 32f
    val STACK_SPACING = 8f  // 同一位置堆叠时的间距

    // 文字样式
    val TEXT_SHADOW_RADIUS = 2f
    val TEXT_SHADOW_COLOR: Color = Color.Black.copy(alpha = 0.5f)
}