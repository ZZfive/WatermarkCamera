package com.watermarkcamera.watermark

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 水印合成核心类
 * 使用 Canvas 将水印绘制到图片上
 */
class WatermarkComposer(private val context: Context) {

    private val density = context.resources.displayMetrics.density

    /**
     * 合成水印到原始图片
     */
    fun composeWatermark(bitmap: Bitmap, config: WatermarkConfig): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        // 构建水印文本行
        val lines = buildWatermarkLines(config)

        if (lines.isEmpty()) {
            return result
        }

        // 创建画笔
        val textPaint = createTextPaint()
        val bgPaint = createBackgroundPaint()

        // 计算水印区域尺寸
        val lineHeight = textPaint.fontSpacing
        val padding = WatermarkStyle.PADDING * density
        val backgroundHeight = lines.size * lineHeight + padding * 2
        val backgroundWidth = lines.maxOf { textPaint.measureText(it) } + padding * 2

        // 计算水印位置
        val (bgX, bgY) = calculateBackgroundPosition(
            backgroundWidth,
            backgroundHeight,
            result.width.toFloat(),
            result.height.toFloat(),
            config.position
        )

        // 绘制半透明背景
        val bgRect = RectF(
            bgX,
            bgY,
            bgX + backgroundWidth,
            bgY + backgroundHeight
        )
        canvas.drawRoundRect(bgRect, WatermarkStyle.CORNER_RADIUS * density, WatermarkStyle.CORNER_RADIUS * density, bgPaint)

        // 绘制文字
        var currentY = bgY + padding + textPaint.textSize
        lines.forEach { line ->
            canvas.drawText(line, bgX + padding, currentY, textPaint)
            currentY += lineHeight
        }

        return result
    }

    private fun buildWatermarkLines(config: WatermarkConfig): List<String> {
        val lines = mutableListOf<String>()

        // 时间戳
        if (config.showTimestamp) {
            lines.add(formatTimestamp(config.timestamp))
        }

        // 位置地址
        if (config.showLocation && config.locationAddress.isNotEmpty()) {
            lines.add(config.locationAddress)
        }

        // 经纬度
        if (config.showLocation && config.latitude != null && config.longitude != null) {
            lines.add("纬度: ${String.format(Locale.US, "%.6f", config.latitude)}")
            lines.add("经度: ${String.format(Locale.US, "%.6f", config.longitude)}")
        }

        // 自定义文本
        if (config.showText && config.customText.isNotEmpty()) {
            lines.add(config.customText)
        }

        return lines
    }

    private fun createTextPaint(): Paint {
        return Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = WatermarkStyle.FONT_SIZE * density
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(
                WatermarkStyle.TEXT_SHADOW_RADIUS * density,
                2f * density,
                2f * density,
                android.graphics.Color.BLACK
            )
        }
    }

    private fun createBackgroundPaint(): Paint {
        return Paint().apply {
            color = android.graphics.Color.argb(128, 0, 0, 0) // 50% alpha black
        }
    }

    private fun calculateBackgroundPosition(
        bgWidth: Float,
        bgHeight: Float,
        imageWidth: Float,
        imageHeight: Float,
        position: WatermarkPosition
    ): Pair<Float, Float> {
        val margin = WatermarkStyle.MARGIN_FROM_EDGE * density

        return when (position) {
            WatermarkPosition.TOP_LEFT -> Pair(margin, margin)
            WatermarkPosition.TOP_RIGHT -> Pair(imageWidth - bgWidth - margin, margin)
            WatermarkPosition.BOTTOM_LEFT -> Pair(margin, imageHeight - bgHeight - margin)
            WatermarkPosition.BOTTOM_RIGHT -> Pair(imageWidth - bgWidth - margin, imageHeight - bgHeight - margin)
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy年MM月dd日 HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}