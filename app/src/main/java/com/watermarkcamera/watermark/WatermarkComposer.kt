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
import kotlin.math.roundToInt

class WatermarkComposer(private val context: Context) {

    private val density = context.resources.displayMetrics.density

    private val paintCache = object : LinkedHashMap<Int, Paint>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Paint>?): Boolean {
            return size > 16
        }
    }

    private val stackSpacing get() = WatermarkStyle.STACK_SPACING * density
    private val marginFromEdge get() = WatermarkStyle.MARGIN_FROM_EDGE * density
    private val cornerRadius get() = WatermarkStyle.CORNER_RADIUS * density
    private val padding get() = WatermarkStyle.PADDING * density

    fun composeWatermark(
        bitmap: Bitmap,
        config: WatermarkConfig,
        layout: WatermarkLayoutConfig
    ): Bitmap {
        val result = if (bitmap.config == Bitmap.Config.ARGB_8888 && bitmap.isMutable) {
            bitmap
        } else {
            bitmap.copy(Bitmap.Config.ARGB_8888, true)
        }
        val canvas = Canvas(result)

        val referenceDimension = 4000f
        val imageScale = minOf(result.width, result.height) / referenceDimension
        val maxContentWidth = result.width - marginFromEdge * imageScale * 2 - padding * imageScale * 2

        val enabledBlocks = mutableListOf<Triple<Int, WatermarkBlockConfig, List<String>>>()

        if (layout.timestamp.enabled) {
            enabledBlocks.add(
                Triple(
                    0,
                    layout.timestamp,
                    wrapLines(
                        formatTimestamp(config.timestamp).split("\n"),
                        layout.timestamp.fontSizeSp,
                        imageScale,
                        maxContentWidth
                    )
                )
            )
        }
        if (layout.address.enabled && config.locationAddress.isNotEmpty()) {
            enabledBlocks.add(
                Triple(
                    1,
                    layout.address,
                    wrapLines(
                        config.locationAddress.split("\n"),
                        layout.address.fontSizeSp,
                        imageScale,
                        maxContentWidth
                    )
                )
            )
        }
        if (layout.coords.enabled && config.latitude != null && config.longitude != null) {
            val coordsText = when (layout.coordsMode) {
                CoordsDisplayMode.HORIZONTAL ->
                    "纬度: ${String.format(Locale.US, "%.6f", config.latitude)} / 经度: ${String.format(Locale.US, "%.6f", config.longitude)}"
                CoordsDisplayMode.VERTICAL ->
                    "纬度: ${String.format(Locale.US, "%.6f", config.latitude)}\n经度: ${String.format(Locale.US, "%.6f", config.longitude)}"
            }
            enabledBlocks.add(
                Triple(
                    2,
                    layout.coords,
                    wrapLines(
                        coordsText.split("\n"),
                        layout.coords.fontSizeSp,
                        imageScale,
                        maxContentWidth
                    )
                )
            )
        }
        if (layout.custom.enabled && config.customText.isNotEmpty()) {
            enabledBlocks.add(
                Triple(
                    3,
                    layout.custom,
                    wrapLines(
                        config.customText.split("\n"),
                        layout.custom.fontSizeSp,
                        imageScale,
                        maxContentWidth
                    )
                )
            )
        }

        val alignedBlocks = enabledBlocks.groupBy { it.second.alignment }

        alignedBlocks.forEach { (alignment, blocks) ->
            val orderedBlocks = blocks.sortedBy { it.first }
                .let { if (alignment.isBottomAligned()) it.reversed() else it }

            val heights = orderedBlocks.map { (_, blockConfig, lines) ->
                calculateBlockHeight(blockConfig, lines, imageScale)
            }

            var cumulativeOffset = 0f
            orderedBlocks.forEachIndexed { index, (_, blockConfig, lines) ->
                drawBlock(canvas, result, blockConfig, lines, cumulativeOffset, imageScale)
                if (index < orderedBlocks.lastIndex) {
                    cumulativeOffset += heights[index] + stackSpacing * imageScale
                }
            }
        }

        return result
    }

    private fun wrapLines(
        lines: List<String>,
        fontSizeSp: Int,
        imageScale: Float,
        maxContentWidth: Float
    ): List<String> {
        val paint = getTextPaint(fontSizeSp, imageScale)
        val wrapped = mutableListOf<String>()

        lines.forEach { line ->
            if (line.isEmpty()) {
                wrapped.add("")
                return@forEach
            }

            var start = 0
            while (start < line.length) {
                val count = paint.breakText(
                    line,
                    start,
                    line.length,
                    true,
                    maxContentWidth.coerceAtLeast(1f),
                    null
                )
                if (count <= 0) break
                wrapped.add(line.substring(start, start + count))
                start += count
            }
        }

        return wrapped.ifEmpty { lines }
    }

    private fun calculateBlockHeight(
        blockConfig: WatermarkBlockConfig,
        lines: List<String>,
        imageScale: Float
    ): Float {
        val paint = getTextPaint(blockConfig.fontSizeSp, imageScale)
        val lineHeight = paint.fontSpacing
        return lines.size * lineHeight + padding * imageScale * 2
    }

    private fun drawBlock(
        canvas: Canvas,
        image: Bitmap,
        blockConfig: WatermarkBlockConfig,
        lines: List<String>,
        stackOffset: Float = 0f,
        imageScale: Float = 1f
    ) {
        if (lines.isEmpty()) return

        val paint = getTextPaint(blockConfig.fontSizeSp, imageScale)
        val lineHeight = paint.fontSpacing
        val scaledPadding = padding * imageScale
        val blockHeight = lines.size * lineHeight + scaledPadding * 2
        val maxLineWidth = lines.maxOfOrNull { paint.measureText(it) } ?: 0f
        val blockWidth = maxLineWidth + scaledPadding * 2

        val (drawX, drawY) = calculatePosition(
            blockConfig.alignment,
            blockWidth,
            blockHeight,
            image.width.toFloat(),
            image.height.toFloat(),
            stackOffset,
            imageScale
        )

        if (blockConfig.showBackground) {
            val bgPaint = getBackgroundPaint()
            val scaledCornerRadius = cornerRadius * imageScale
            val bgRect = RectF(drawX, drawY, drawX + blockWidth, drawY + blockHeight)
            canvas.drawRoundRect(bgRect, scaledCornerRadius, scaledCornerRadius, bgPaint)
        }

        val baselineY = drawY + scaledPadding - paint.ascent()
        var currentY = baselineY
        lines.forEach { line ->
            canvas.drawText(line, drawX + scaledPadding, currentY, paint)
            currentY += lineHeight
        }
    }

    private fun calculatePosition(
        alignment: WatermarkAlignment,
        blockWidth: Float,
        blockHeight: Float,
        imageWidth: Float,
        imageHeight: Float,
        stackOffset: Float = 0f,
        imageScale: Float = 1f
    ): Pair<Float, Float> {
        val scaledMargin = marginFromEdge * imageScale
        val (baseX, baseY) = when (alignment) {
            WatermarkAlignment.TOP_LEFT -> Pair(scaledMargin, scaledMargin + stackOffset)
            WatermarkAlignment.TOP -> Pair(imageWidth / 2 - blockWidth / 2, scaledMargin + stackOffset)
            WatermarkAlignment.TOP_RIGHT -> Pair(imageWidth - blockWidth - scaledMargin, scaledMargin + stackOffset)
            WatermarkAlignment.LEFT -> Pair(scaledMargin, imageHeight / 2 - blockHeight / 2 + stackOffset)
            WatermarkAlignment.CENTER -> Pair(imageWidth / 2 - blockWidth / 2, imageHeight / 2 - blockHeight / 2 + stackOffset)
            WatermarkAlignment.RIGHT -> Pair(imageWidth - blockWidth - scaledMargin, imageHeight / 2 - blockHeight / 2 + stackOffset)
            WatermarkAlignment.BOTTOM_LEFT -> Pair(scaledMargin, imageHeight - blockHeight - scaledMargin - stackOffset)
            WatermarkAlignment.BOTTOM -> Pair(imageWidth / 2 - blockWidth / 2, imageHeight - blockHeight - scaledMargin - stackOffset)
            WatermarkAlignment.BOTTOM_RIGHT -> Pair(imageWidth - blockWidth - scaledMargin, imageHeight - blockHeight - scaledMargin - stackOffset)
        }
        return Pair(baseX, baseY)
    }

    private fun getTextPaint(fontSizeSp: Int, imageScale: Float = 1f): Paint {
        val scaledTextSize = (fontSizeSp * density * imageScale).roundToInt().coerceAtLeast(1)
        return paintCache.getOrPut(scaledTextSize) {
            Paint().apply {
                color = Color.WHITE
                textSize = scaledTextSize.toFloat()
                isAntiAlias = true
                typeface = Typeface.DEFAULT_BOLD
                setShadowLayer(
                    WatermarkStyle.TEXT_SHADOW_RADIUS * density * imageScale,
                    2f * density * imageScale,
                    2f * density * imageScale,
                    Color.BLACK
                )
            }
        }
    }

    private fun getBackgroundPaint(): Paint {
        return Paint().apply {
            color = Color.argb(128, 0, 0, 0)
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy年MM月dd日 HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    private fun WatermarkAlignment.isBottomAligned(): Boolean {
        return this == WatermarkAlignment.BOTTOM_LEFT ||
            this == WatermarkAlignment.BOTTOM ||
            this == WatermarkAlignment.BOTTOM_RIGHT
    }
}
