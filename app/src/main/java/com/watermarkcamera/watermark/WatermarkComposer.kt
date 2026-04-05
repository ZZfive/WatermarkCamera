package com.watermarkcamera.watermark

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 水印合成核心类
 * 使用 Canvas 将水印绘制到图片上
 * 每个水印块独立绘制，支持不同的位置和字体大小
 */
class WatermarkComposer(private val context: Context) {

    private val density = context.resources.displayMetrics.density

    /**
     * 合成水印到原始图片
     */
    fun composeWatermark(
        bitmap: Bitmap,
        config: WatermarkConfig,
        layout: WatermarkLayoutConfig
    ): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        // 收集所有启用的块及其内容
        val enabledBlocks = mutableListOf<Pair<WatermarkBlockConfig, String>>()

        if (layout.timestamp.enabled) {
            enabledBlocks.add(layout.timestamp to formatTimestamp(config.timestamp))
        }
        if (layout.address.enabled && config.locationAddress.isNotEmpty()) {
            enabledBlocks.add(layout.address to config.locationAddress)
        }
        if (layout.coords.enabled && config.latitude != null && config.longitude != null) {
            val coordsText = when (layout.coordsMode) {
                CoordsDisplayMode.HORIZONTAL ->
                    "纬度: ${String.format(Locale.US, "%.6f", config.latitude)} / 经度: ${String.format(Locale.US, "%.6f", config.longitude)}"
                CoordsDisplayMode.VERTICAL ->
                    "纬度: ${String.format(Locale.US, "%.6f", config.latitude)}\n经度: ${String.format(Locale.US, "%.6f", config.longitude)}"
            }
            enabledBlocks.add(layout.coords to coordsText)
        }
        if (layout.custom.enabled && config.customText.isNotEmpty()) {
            enabledBlocks.add(layout.custom to config.customText)
        }

        // 按对齐位置分组，同一位置的块垂直堆叠
        val alignedBlocks = enabledBlocks.groupBy { it.first.alignment }

        alignedBlocks.forEach { (_, blocks) ->
            blocks.forEachIndexed { index, (blockConfig, content) ->
                // 计算堆叠偏移：同一位置的块依次向下排列
                val stackOffset = if (index > 0) {
                    var offset = 0f
                    for (i in 0 until index) {
                        offset += calculateBlockContentHeight(blocks[i].first, blocks[i].second)
                        offset += WatermarkStyle.STACK_SPACING * density
                    }
                    offset
                } else {
                    0f
                }
                drawBlock(canvas, result, blockConfig, content, stackOffset)
            }
        }

        return result
    }

    /**
     * 计算单个块的内容高度（不含padding，用于堆叠计算）
     */
    private fun calculateBlockContentHeight(blockConfig: WatermarkBlockConfig, content: String): Float {
        val paint = createTextPaint(blockConfig.fontSizeSp)
        val lines = content.split("\n")
        return lines.size * paint.fontSpacing
    }

    /**
     * 计算单个块的完整高度（含padding）
     */
    private fun calculateBlockHeight(blockConfig: WatermarkBlockConfig, content: String): Float {
        val paint = createTextPaint(blockConfig.fontSizeSp)
        val lines = content.split("\n")
        val padding = WatermarkStyle.PADDING * density
        val lineHeight = paint.fontSpacing
        return lines.size * lineHeight + padding * 2
    }

    private fun drawBlock(
        canvas: Canvas,
        image: Bitmap,
        blockConfig: WatermarkBlockConfig,
        content: String,
        stackOffset: Float = 0f
    ) {
        if (content.isEmpty()) return

        val paint = createTextPaint(blockConfig.fontSizeSp)
        val lines = content.split("\n")
        val padding = WatermarkStyle.PADDING * density

        // 计算当前块的尺寸
        val lineHeight = paint.fontSpacing
        val blockHeight = lines.size * lineHeight + padding * 2
        val maxLineWidth = lines.maxOfOrNull { paint.measureText(it) } ?: 0f
        val blockWidth = maxLineWidth + padding * 2

        // 根据对齐方式计算绘制坐标
        val (drawX, drawY) = calculatePosition(
            blockConfig.alignment,
            blockWidth,
            blockHeight,
            image.width.toFloat(),
            image.height.toFloat(),
            stackOffset
        )

        // 绘制半透明背景（如果启用）
        if (blockConfig.showBackground) {
            val bgPaint = createBackgroundPaint()
            val bgRect = RectF(drawX, drawY, drawX + blockWidth, drawY + blockHeight)
            canvas.drawRoundRect(
                bgRect,
                WatermarkStyle.CORNER_RADIUS * density,
                WatermarkStyle.CORNER_RADIUS * density,
                bgPaint
            )
        }

        // 绘制文字 - 使用 baseline 精确定位
        // baselineY = drawY + padding - paint.ascent()
        // paint.ascent() 是负值，所以 - paint.ascent() 相当于 + |paint.ascent()|
        val baselineY = drawY + padding - paint.ascent()
        var currentY = baselineY
        lines.forEach { line ->
            canvas.drawText(line, drawX + padding, currentY, paint)
            currentY += lineHeight
        }
    }

    private fun calculatePosition(
        alignment: WatermarkAlignment,
        blockWidth: Float,
        blockHeight: Float,
        imageWidth: Float,
        imageHeight: Float,
        stackOffset: Float = 0f
    ): Pair<Float, Float> {
        val margin = WatermarkStyle.MARGIN_FROM_EDGE * density

        val (baseX, baseY) = when (alignment) {
            WatermarkAlignment.TOP_LEFT -> Pair(margin, margin)
            WatermarkAlignment.TOP -> Pair(imageWidth / 2 - blockWidth / 2, margin)
            WatermarkAlignment.TOP_RIGHT -> Pair(imageWidth - blockWidth - margin, margin)
            WatermarkAlignment.LEFT -> Pair(margin, imageHeight / 2 - blockHeight / 2)
            WatermarkAlignment.CENTER -> Pair(imageWidth / 2 - blockWidth / 2, imageHeight / 2 - blockHeight / 2)
            WatermarkAlignment.RIGHT -> Pair(imageWidth - blockWidth - margin, imageHeight / 2 - blockHeight / 2)
            WatermarkAlignment.BOTTOM_LEFT -> Pair(margin, imageHeight - blockHeight - margin)
            WatermarkAlignment.BOTTOM -> Pair(imageWidth / 2 - blockWidth / 2, imageHeight - blockHeight - margin)
            WatermarkAlignment.BOTTOM_RIGHT -> Pair(imageWidth - blockWidth - margin, imageHeight - blockHeight - margin)
        }

        // 应用堆叠偏移：向下移动
        return Pair(baseX, baseY + stackOffset)
    }

    private fun createTextPaint(fontSizeSp: Int): Paint {
        return Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = fontSizeSp * density
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

    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy年MM月dd日 HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}
