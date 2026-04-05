package com.watermarkcamera.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.watermarkcamera.data.WatermarkPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SettingsUiState(
    val showTimestamp: Boolean = true,
    val showLocationAddress: Boolean = true,
    val showLocationCoords: Boolean = true,
    val showCustomText: Boolean = true,
    val customText: String = "",
    val saveOriginal: Boolean = false
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = WatermarkPreferences(application)

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            showTimestamp = preferences.showTimestamp,
            showLocationAddress = preferences.showLocationAddress,
            showLocationCoords = preferences.showLocationCoords,
            showCustomText = preferences.showCustomText,
            customText = preferences.customText,
            saveOriginal = preferences.saveOriginal
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun updateShowTimestamp(show: Boolean) {
        preferences.showTimestamp = show
        _uiState.update { it.copy(showTimestamp = show) }
    }

    fun updateShowLocationAddress(show: Boolean) {
        preferences.showLocationAddress = show
        _uiState.update { it.copy(showLocationAddress = show) }
    }

    fun updateShowLocationCoords(show: Boolean) {
        preferences.showLocationCoords = show
        _uiState.update { it.copy(showLocationCoords = show) }
    }

    fun updateShowCustomText(show: Boolean) {
        preferences.showCustomText = show
        _uiState.update { it.copy(showCustomText = show) }
    }

    fun updateCustomText(text: String) {
        preferences.customText = text
        _uiState.update { it.copy(customText = text) }
    }

    fun updateSaveOriginal(save: Boolean) {
        preferences.saveOriginal = save
        _uiState.update { it.copy(saveOriginal = save) }
    }
}