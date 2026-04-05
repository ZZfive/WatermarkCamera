package com.watermarkcamera.watermark

/**
 * 单个水印块配置
 */
data class WatermarkBlockConfig(
    val enabled: Boolean = true,
    val alignment: WatermarkAlignment = WatermarkAlignment.BOTTOM_LEFT,
    val fontSizeSp: Int = 24  // 字体大小 12-48sp
)
