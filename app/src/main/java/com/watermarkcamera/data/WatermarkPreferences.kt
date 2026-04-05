package com.watermarkcamera.data

import android.content.Context
import android.content.SharedPreferences

/**
 * 水印设置管理器 - 使用 SharedPreferences 持久化
 */
class WatermarkPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    var showTimestamp: Boolean
        get() = prefs.getBoolean(KEY_SHOW_TIMESTAMP, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_TIMESTAMP, value).apply()

    var showLocationAddress: Boolean
        get() = prefs.getBoolean(KEY_SHOW_LOCATION_ADDRESS, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_LOCATION_ADDRESS, value).apply()

    var showLocationCoords: Boolean
        get() = prefs.getBoolean(KEY_SHOW_LOCATION_COORDS, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_LOCATION_COORDS, value).apply()

    var showCustomText: Boolean
        get() = prefs.getBoolean(KEY_SHOW_CUSTOM_TEXT, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_CUSTOM_TEXT, value).apply()

    var customText: String
        get() = prefs.getString(KEY_CUSTOM_TEXT, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CUSTOM_TEXT, value).apply()

    var saveOriginal: Boolean
        get() = prefs.getBoolean(KEY_SAVE_ORIGINAL, false)
        set(value) = prefs.edit().putBoolean(KEY_SAVE_ORIGINAL, value).apply()

    companion object {
        private const val PREFS_NAME = "watermark_prefs"
        private const val KEY_SHOW_TIMESTAMP = "show_timestamp"
        private const val KEY_SHOW_LOCATION_ADDRESS = "show_location_address"
        private const val KEY_SHOW_LOCATION_COORDS = "show_location_coords"
        private const val KEY_SHOW_CUSTOM_TEXT = "show_custom_text"
        private const val KEY_CUSTOM_TEXT = "custom_text"
        private const val KEY_SAVE_ORIGINAL = "save_original"
    }
}