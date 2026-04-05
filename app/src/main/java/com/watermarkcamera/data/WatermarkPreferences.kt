package com.watermarkcamera.data

import android.content.Context
import android.content.SharedPreferences
import com.watermarkcamera.watermark.WatermarkAlignment
import com.watermarkcamera.watermark.WatermarkBlockConfig
import com.watermarkcamera.watermark.WatermarkLayoutConfig

/**
 * 水印设置管理器 - 使用 SharedPreferences 持久化
 */
class WatermarkPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    // ========== 全局设置 ==========
    var customText: String
        get() = prefs.getString(KEY_CUSTOM_TEXT, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CUSTOM_TEXT, value).apply()

    var saveOriginal: Boolean
        get() = prefs.getBoolean(KEY_SAVE_ORIGINAL, false)
        set(value) = prefs.edit().putBoolean(KEY_SAVE_ORIGINAL, value).apply()

    // ========== 时间块设置 ==========
    var timestampEnabled: Boolean
        get() = prefs.getBoolean(KEY_TIMESTAMP_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_TIMESTAMP_ENABLED, value).apply()

    var timestampAlignment: WatermarkAlignment
        get() = WatermarkAlignment.valueOf(prefs.getString(KEY_TIMESTAMP_ALIGNMENT, "BOTTOM_LEFT") ?: "BOTTOM_LEFT")
        set(value) = prefs.edit().putString(KEY_TIMESTAMP_ALIGNMENT, value.name).apply()

    var timestampFontSize: Int
        get() = prefs.getInt(KEY_TIMESTAMP_FONT_SIZE, 24)
        set(value) = prefs.edit().putInt(KEY_TIMESTAMP_FONT_SIZE, value).apply()

    // ========== 地址块设置 ==========
    var addressEnabled: Boolean
        get() = prefs.getBoolean(KEY_ADDRESS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ADDRESS_ENABLED, value).apply()

    var addressAlignment: WatermarkAlignment
        get() = WatermarkAlignment.valueOf(prefs.getString(KEY_ADDRESS_ALIGNMENT, "BOTTOM_LEFT") ?: "BOTTOM_LEFT")
        set(value) = prefs.edit().putString(KEY_ADDRESS_ALIGNMENT, value.name).apply()

    var addressFontSize: Int
        get() = prefs.getInt(KEY_ADDRESS_FONT_SIZE, 20)
        set(value) = prefs.edit().putInt(KEY_ADDRESS_FONT_SIZE, value).apply()

    // ========== 经纬度块设置 ==========
    var coordsEnabled: Boolean
        get() = prefs.getBoolean(KEY_COORDS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_COORDS_ENABLED, value).apply()

    var coordsAlignment: WatermarkAlignment
        get() = WatermarkAlignment.valueOf(prefs.getString(KEY_COORDS_ALIGNMENT, "BOTTOM_LEFT") ?: "BOTTOM_LEFT")
        set(value) = prefs.edit().putString(KEY_COORDS_ALIGNMENT, value.name).apply()

    var coordsFontSize: Int
        get() = prefs.getInt(KEY_COORDS_FONT_SIZE, 16)
        set(value) = prefs.edit().putInt(KEY_COORDS_FONT_SIZE, value).apply()

    // ========== 自定义文本块设置 ==========
    var customEnabled: Boolean
        get() = prefs.getBoolean(KEY_CUSTOM_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_CUSTOM_ENABLED, value).apply()

    var customAlignment: WatermarkAlignment
        get() = WatermarkAlignment.valueOf(prefs.getString(KEY_CUSTOM_ALIGNMENT, "BOTTOM_RIGHT") ?: "BOTTOM_RIGHT")
        set(value) = prefs.edit().putString(KEY_CUSTOM_ALIGNMENT, value.name).apply()

    var customFontSize: Int
        get() = prefs.getInt(KEY_CUSTOM_FONT_SIZE, 22)
        set(value) = prefs.edit().putInt(KEY_CUSTOM_FONT_SIZE, value).apply()

    /**
     * 加载完整布局配置
     */
    fun loadLayoutConfig(): WatermarkLayoutConfig {
        return WatermarkLayoutConfig(
            timestamp = WatermarkBlockConfig(
                enabled = timestampEnabled,
                alignment = timestampAlignment,
                fontSizeSp = timestampFontSize
            ),
            address = WatermarkBlockConfig(
                enabled = addressEnabled,
                alignment = addressAlignment,
                fontSizeSp = addressFontSize
            ),
            coords = WatermarkBlockConfig(
                enabled = coordsEnabled,
                alignment = coordsAlignment,
                fontSizeSp = coordsFontSize
            ),
            custom = WatermarkBlockConfig(
                enabled = customEnabled,
                alignment = customAlignment,
                fontSizeSp = customFontSize
            )
        )
    }

    /**
     * 保存完整布局配置
     */
    fun saveLayoutConfig(config: WatermarkLayoutConfig) {
        timestampEnabled = config.timestamp.enabled
        timestampAlignment = config.timestamp.alignment
        timestampFontSize = config.timestamp.fontSizeSp

        addressEnabled = config.address.enabled
        addressAlignment = config.address.alignment
        addressFontSize = config.address.fontSizeSp

        coordsEnabled = config.coords.enabled
        coordsAlignment = config.coords.alignment
        coordsFontSize = config.coords.fontSizeSp

        customEnabled = config.custom.enabled
        customAlignment = config.custom.alignment
        customFontSize = config.custom.fontSizeSp
    }

    companion object {
        private const val PREFS_NAME = "watermark_prefs"
        private const val KEY_CUSTOM_TEXT = "custom_text"
        private const val KEY_SAVE_ORIGINAL = "save_original"

        // Timestamp
        private const val KEY_TIMESTAMP_ENABLED = "timestamp_enabled"
        private const val KEY_TIMESTAMP_ALIGNMENT = "timestamp_alignment"
        private const val KEY_TIMESTAMP_FONT_SIZE = "timestamp_font_size"

        // Address
        private const val KEY_ADDRESS_ENABLED = "address_enabled"
        private const val KEY_ADDRESS_ALIGNMENT = "address_alignment"
        private const val KEY_ADDRESS_FONT_SIZE = "address_font_size"

        // Coords
        private const val KEY_COORDS_ENABLED = "coords_enabled"
        private const val KEY_COORDS_ALIGNMENT = "coords_alignment"
        private const val KEY_COORDS_FONT_SIZE = "coords_font_size"

        // Custom
        private const val KEY_CUSTOM_ENABLED = "custom_enabled"
        private const val KEY_CUSTOM_ALIGNMENT = "custom_alignment"
        private const val KEY_CUSTOM_FONT_SIZE = "custom_font_size"
    }
}
