package com.watermarkcamera.watermark

/**
 * 完整水印布局配置 - 包含4个独立块
 */
data class WatermarkLayoutConfig(
    val timestamp: WatermarkBlockConfig = WatermarkBlockConfig(
        enabled = true,
        alignment = WatermarkAlignment.BOTTOM_LEFT,
        fontSizeSp = 24
    ),
    val address: WatermarkBlockConfig = WatermarkBlockConfig(
        enabled = true,
        alignment = WatermarkAlignment.BOTTOM_LEFT,
        fontSizeSp = 20
    ),
    val coords: WatermarkBlockConfig = WatermarkBlockConfig(
        enabled = true,
        alignment = WatermarkAlignment.BOTTOM_LEFT,
        fontSizeSp = 16
    ),
    val custom: WatermarkBlockConfig = WatermarkBlockConfig(
        enabled = true,
        alignment = WatermarkAlignment.BOTTOM_RIGHT,
        fontSizeSp = 22
    )
)
