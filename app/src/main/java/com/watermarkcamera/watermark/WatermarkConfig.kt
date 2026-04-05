package com.watermarkcamera.watermark

/**
 * 水印配置数据类 - 包含运行时数据
 * 布局配置在 WatermarkLayoutConfig 中
 */
data class WatermarkConfig(
    val customText: String = "",
    val locationAddress: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val timestamp: Long = System.currentTimeMillis()
)
