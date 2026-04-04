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
    val showLocation: Boolean = true,
    val showCustomText: Boolean = true,
    val customText: String = ""
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = WatermarkPreferences(application)

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            showTimestamp = preferences.showTimestamp,
            showLocation = preferences.showLocation,
            showCustomText = preferences.showCustomText,
            customText = preferences.customText
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun updateShowTimestamp(show: Boolean) {
        preferences.showTimestamp = show
        _uiState.update { it.copy(showTimestamp = show) }
    }

    fun updateShowLocation(show: Boolean) {
        preferences.showLocation = show
        _uiState.update { it.copy(showLocation = show) }
    }

    fun updateShowCustomText(show: Boolean) {
        preferences.showCustomText = show
        _uiState.update { it.copy(showCustomText = show) }
    }

    fun updateCustomText(text: String) {
        preferences.customText = text
        _uiState.update { it.copy(customText = text) }
    }
}