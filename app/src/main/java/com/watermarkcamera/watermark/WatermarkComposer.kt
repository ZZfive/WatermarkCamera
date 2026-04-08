package com.watermarkcamera.watermark

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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
 *
 * 优化点：
 * - 避免冗余的 bitmap.copy()
 * - Paint 对象 LRU 缓存
 * - 预计算 block 高度，避免 O(n²)
 * - 预分词避免重复 split("\n")
 */
class WatermarkComposer(private val context: Context) {

    private val density = context.resources.displayMetrics.density

    // Paint 对象 LRU 缓存，避免重复创建
    private val paintCache = object : LinkedHashMap<Int, Paint>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Paint>?): Boolean {
            return size > 16
        }
    }

    private val stackSpacing get() = WatermarkStyle.STACK_SPACING * density
    private val marginFromEdge get() = WatermarkStyle.MARGIN_FROM_EDGE * density
    private val cornerRadius get() = WatermarkStyle.CORNER_RADIUS * density
    private val padding get() = WatermarkStyle.PADDING * density

    /**
     * 合成水印到原始图片
     */
    fun composeWatermark(
        bitmap: Bitmap,
        config: WatermarkConfig,
        layout: WatermarkLayoutConfig
    ): Bitmap {
        // 避免冗余拷贝：如果原图已是 ARGB_8888 且可变，直接使用
        val result = if (bitmap.config == Bitmap.Config.ARGB_8888 && bitmap.isMutable) {
            bitmap
        } else {
            bitmap.copy(Bitmap.Config.ARGB_8888, true)
        }
        val canvas = Canvas(result)

        // 收集所有启用的块及其内容
        val enabledBlocks = mutableListOf<Pair<WatermarkBlockConfig, List<String>>>()

        if (layout.timestamp.enabled) {
            enabledBlocks.add(layout.timestamp to formatTimestamp(config.timestamp).split("\n"))
        }
        if (layout.address.enabled && config.locationAddress.isNotEmpty()) {
            enabledBlocks.add(layout.address to config.locationAddress.split("\n"))
        }
        if (layout.coords.enabled && config.latitude != null && config.longitude != null) {
            val coordsText = when (layout.coordsMode) {
                CoordsDisplayMode.HORIZONTAL ->
                    "纬度: ${String.format(Locale.US, "%.6f", config.latitude)} / 经度: ${String.format(Locale.US, "%.6f", config.longitude)}"
                CoordsDisplayMode.VERTICAL ->
                    "纬度: ${String.format(Locale.US, "%.6f", config.latitude)}\n经度: ${String.format(Locale.US, "%.6f", config.longitude)}"
            }
            enabledBlocks.add(layout.coords to coordsText.split("\n"))
        }
        if (layout.custom.enabled && config.customText.isNotEmpty()) {
            enabledBlocks.add(layout.custom to config.customText.split("\n"))
        }

        // 按对齐位置分组，同一位置的块垂直堆叠
        val alignedBlocks = enabledBlocks.groupBy { it.first.alignment }

        alignedBlocks.forEach { (_, blocks) ->
            // 预计算所有 block 高度，避免 O(n²) 重复计算
            val heights = blocks.map { (blockConfig, lines) ->
                calculateBlockHeight(blockConfig, lines)
            }

            // 累积偏移量 O(n) 而非 O(n²)
            var cumulativeOffset = 0f
            blocks.forEachIndexed { index, (blockConfig, lines) ->
                drawBlock(canvas, result, blockConfig, lines, cumulativeOffset)
                if (index < blocks.lastIndex) {
                    cumulativeOffset += heights[index] + stackSpacing
                }
            }
        }

        return result
    }

    /**
     * 计算单个块的完整高度（含padding）
     */
    private fun calculateBlockHeight(blockConfig: WatermarkBlockConfig, lines: List<String>): Float {
        val paint = getTextPaint(blockConfig.fontSizeSp)
        val lineHeight = paint.fontSpacing
        return lines.size * lineHeight + padding * 2
    }

    private fun drawBlock(
        canvas: Canvas,
        image: Bitmap,
        blockConfig: WatermarkBlockConfig,
        lines: List<String>,
        stackOffset: Float = 0f
    ) {
        if (lines.isEmpty()) return

        val paint = getTextPaint(blockConfig.fontSizeSp)
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
            val bgPaint = getBackgroundPaint()
            val bgRect = RectF(drawX, drawY, drawX + blockWidth, drawY + blockHeight)
            canvas.drawRoundRect(bgRect, cornerRadius, cornerRadius, bgPaint)
        }

        // 绘制文字 - 使用 baseline 精确定位
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
        val (baseX, baseY) = when (alignment) {
            WatermarkAlignment.TOP_LEFT -> Pair(marginFromEdge, marginFromEdge)
            WatermarkAlignment.TOP -> Pair(imageWidth / 2 - blockWidth / 2, marginFromEdge)
            WatermarkAlignment.TOP_RIGHT -> Pair(imageWidth - blockWidth - marginFromEdge, marginFromEdge)
            WatermarkAlignment.LEFT -> Pair(marginFromEdge, imageHeight / 2 - blockHeight / 2)
            WatermarkAlignment.CENTER -> Pair(imageWidth / 2 - blockWidth / 2, imageHeight / 2 - blockHeight / 2)
            WatermarkAlignment.RIGHT -> Pair(imageWidth - blockWidth - marginFromEdge, imageHeight / 2 - blockHeight / 2)
            WatermarkAlignment.BOTTOM_LEFT -> Pair(marginFromEdge, imageHeight - blockHeight - marginFromEdge)
            WatermarkAlignment.BOTTOM -> Pair(imageWidth / 2 - blockWidth / 2, imageHeight - blockHeight - marginFromEdge)
            WatermarkAlignment.BOTTOM_RIGHT -> Pair(imageWidth - blockWidth - marginFromEdge, imageHeight - blockHeight - marginFromEdge)
        }

        // 应用堆叠偏移：向下移动
        return Pair(baseX, baseY + stackOffset)
    }

    /**
     * 获取缓存的 Text Paint，避免重复创建
     */
    private fun getTextPaint(fontSizeSp: Int): Paint {
        return paintCache.getOrPut(fontSizeSp) {
            Paint().apply {
                color = Color.WHITE
                textSize = fontSizeSp * density
                isAntiAlias = true
                typeface = Typeface.DEFAULT_BOLD
                setShadowLayer(
                    WatermarkStyle.TEXT_SHADOW_RADIUS * density,
                    2f * density,
                    2f * density,
                    Color.BLACK
                )
            }
        }
    }

    private fun getBackgroundPaint(): Paint {
        // 背景 Paint 很简单，不需要缓存，每次创建也很快
        return Paint().apply {
            color = Color.argb(128, 0, 0, 0) // 50% alpha black
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy年MM月dd日 HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}
