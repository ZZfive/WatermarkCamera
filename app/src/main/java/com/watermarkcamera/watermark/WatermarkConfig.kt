package com.watermarkcamera.watermark

/**
 * 水印配置数据类
 */
data class WatermarkConfig(
    val showText: Boolean = true,
    val customText: String = "",
    val showLocation: Boolean = true,
    val locationAddress: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val showTimestamp: Boolean = true,
    val timestamp: Long = System.currentTimeMillis(),
    val position: WatermarkPosition = WatermarkPosition.BOTTOM_LEFT
)

enum class WatermarkPosition {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT
}