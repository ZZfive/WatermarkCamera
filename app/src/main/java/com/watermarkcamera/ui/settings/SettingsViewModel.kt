package com.watermarkcamera.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.watermarkcamera.data.WatermarkPreferences
import com.watermarkcamera.watermark.CoordsDisplayMode
import com.watermarkcamera.watermark.WatermarkAlignment
import com.watermarkcamera.watermark.WatermarkBlockConfig
import com.watermarkcamera.watermark.WatermarkLayoutConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class WatermarkBlockType {
    TIMESTAMP, ADDRESS, COORDS, CUSTOM
}

data class SettingsUiState(
    val selectedTab: WatermarkBlockType = WatermarkBlockType.TIMESTAMP,
    val layoutConfig: WatermarkLayoutConfig = WatermarkLayoutConfig(),
    val customText: String = "",
    val saveOriginal: Boolean = false
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = WatermarkPreferences(application)

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            layoutConfig = preferences.loadLayoutConfig(),
            customText = preferences.customText,
            saveOriginal = preferences.saveOriginal
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun selectTab(tab: WatermarkBlockType) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    // ========== 时间块更新 ==========
    fun updateTimestampEnabled(enabled: Boolean) {
        val newConfig = _uiState.value.layoutConfig.timestamp.copy(enabled = enabled)
        updateLayoutConfig(WatermarkBlockType.TIMESTAMP, newConfig)
        preferences.timestampEnabled = enabled
    }

    fun updateTimestampAlignment(alignment: WatermarkAlignment) {
        val newConfig = _uiState.value.layoutConfig.timestamp.copy(alignment = alignment)
        updateLayoutConfig(WatermarkBlockType.TIMESTAMP, newConfig)
        preferences.timestampAlignment = alignment
    }

    fun updateTimestampFontSize(fontSize: Int) {
        val newConfig = _uiState.value.layoutConfig.timestamp.copy(fontSizeSp = fontSize)
        updateLayoutConfig(WatermarkBlockType.TIMESTAMP, newConfig)
        preferences.timestampFontSize = fontSize
    }

    fun updateTimestampShowBackground(show: Boolean) {
        val newConfig = _uiState.value.layoutConfig.timestamp.copy(showBackground = show)
        updateLayoutConfig(WatermarkBlockType.TIMESTAMP, newConfig)
        preferences.timestampShowBackground = show
    }

    // ========== 地址块更新 ==========
    fun updateAddressEnabled(enabled: Boolean) {
        val newConfig = _uiState.value.layoutConfig.address.copy(enabled = enabled)
        updateLayoutConfig(WatermarkBlockType.ADDRESS, newConfig)
        preferences.addressEnabled = enabled
    }

    fun updateAddressAlignment(alignment: WatermarkAlignment) {
        val newConfig = _uiState.value.layoutConfig.address.copy(alignment = alignment)
        updateLayoutConfig(WatermarkBlockType.ADDRESS, newConfig)
        preferences.addressAlignment = alignment
    }

    fun updateAddressFontSize(fontSize: Int) {
        val newConfig = _uiState.value.layoutConfig.address.copy(fontSizeSp = fontSize)
        updateLayoutConfig(WatermarkBlockType.ADDRESS, newConfig)
        preferences.addressFontSize = fontSize
    }

    fun updateAddressShowBackground(show: Boolean) {
        val newConfig = _uiState.value.layoutConfig.address.copy(showBackground = show)
        updateLayoutConfig(WatermarkBlockType.ADDRESS, newConfig)
        preferences.addressShowBackground = show
    }

    // ========== 经纬度块更新 ==========
    fun updateCoordsEnabled(enabled: Boolean) {
        val newConfig = _uiState.value.layoutConfig.coords.copy(enabled = enabled)
        updateLayoutConfig(WatermarkBlockType.COORDS, newConfig)
        preferences.coordsEnabled = enabled
    }

    fun updateCoordsAlignment(alignment: WatermarkAlignment) {
        val newConfig = _uiState.value.layoutConfig.coords.copy(alignment = alignment)
        updateLayoutConfig(WatermarkBlockType.COORDS, newConfig)
        preferences.coordsAlignment = alignment
    }

    fun updateCoordsFontSize(fontSize: Int) {
        val newConfig = _uiState.value.layoutConfig.coords.copy(fontSizeSp = fontSize)
        updateLayoutConfig(WatermarkBlockType.COORDS, newConfig)
        preferences.coordsFontSize = fontSize
    }

    fun updateCoordsShowBackground(show: Boolean) {
        val newConfig = _uiState.value.layoutConfig.coords.copy(showBackground = show)
        updateLayoutConfig(WatermarkBlockType.COORDS, newConfig)
        preferences.coordsShowBackground = show
    }

    // ========== 自定义文本块更新 ==========
    fun updateCustomEnabled(enabled: Boolean) {
        val newConfig = _uiState.value.layoutConfig.custom.copy(enabled = enabled)
        updateLayoutConfig(WatermarkBlockType.CUSTOM, newConfig)
        preferences.customEnabled = enabled
    }

    fun updateCustomAlignment(alignment: WatermarkAlignment) {
        val newConfig = _uiState.value.layoutConfig.custom.copy(alignment = alignment)
        updateLayoutConfig(WatermarkBlockType.CUSTOM, newConfig)
        preferences.customAlignment = alignment
    }

    fun updateCustomFontSize(fontSize: Int) {
        val newConfig = _uiState.value.layoutConfig.custom.copy(fontSizeSp = fontSize)
        updateLayoutConfig(WatermarkBlockType.CUSTOM, newConfig)
        preferences.customFontSize = fontSize
    }

    fun updateCustomShowBackground(show: Boolean) {
        val newConfig = _uiState.value.layoutConfig.custom.copy(showBackground = show)
        updateLayoutConfig(WatermarkBlockType.CUSTOM, newConfig)
        preferences.customShowBackground = show
    }

    // ========== 经纬度显示模式 ==========
    fun updateCoordsDisplayMode(mode: CoordsDisplayMode) {
        _uiState.update { it.copy(layoutConfig = it.layoutConfig.copy(coordsMode = mode)) }
        preferences.coordsDisplayMode = mode
    }

    // ========== 全局设置 ==========
    fun updateCustomText(text: String) {
        preferences.customText = text
        _uiState.update { it.copy(customText = text) }
    }

    fun updateSaveOriginal(save: Boolean) {
        preferences.saveOriginal = save
        _uiState.update { it.copy(saveOriginal = save) }
    }

    private fun updateLayoutConfig(blockType: WatermarkBlockType, newBlock: WatermarkBlockConfig) {
        val newLayoutConfig = when (blockType) {
            WatermarkBlockType.TIMESTAMP -> _uiState.value.layoutConfig.copy(timestamp = newBlock)
            WatermarkBlockType.ADDRESS -> _uiState.value.layoutConfig.copy(address = newBlock)
            WatermarkBlockType.COORDS -> _uiState.value.layoutConfig.copy(coords = newBlock)
            WatermarkBlockType.CUSTOM -> _uiState.value.layoutConfig.copy(custom = newBlock)
        }
        _uiState.update { it.copy(layoutConfig = newLayoutConfig) }
    }

    // Helper to get current block config based on selected tab
    fun getCurrentBlockConfig(): WatermarkBlockConfig {
        return when (_uiState.value.selectedTab) {
            WatermarkBlockType.TIMESTAMP -> _uiState.value.layoutConfig.timestamp
            WatermarkBlockType.ADDRESS -> _uiState.value.layoutConfig.address
            WatermarkBlockType.COORDS -> _uiState.value.layoutConfig.coords
            WatermarkBlockType.CUSTOM -> _uiState.value.layoutConfig.custom
        }
    }
}
