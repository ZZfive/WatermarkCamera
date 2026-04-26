package com.watermarkcamera.ui.preview

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class PreviewUiState(
    val photoUri: Uri? = null,
    val isSharing: Boolean = false,
    val errorMessage: String? = null
)

class PreviewViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(PreviewUiState())
    val uiState: StateFlow<PreviewUiState> = _uiState.asStateFlow()

    fun loadPhoto(uri: String) {
        try {
            val photoUri = Uri.parse(uri)
            _uiState.update { it.copy(photoUri = photoUri) }
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = "加载照片失败") }
        }
    }

    fun sharePhoto() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSharing = true) }

            try {
                val uri = _uiState.value.photoUri ?: return@launch
                val shareUri = toSharableUri(uri)
                val application = getApplication<Application>()

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/jpeg"
                    putExtra(Intent.EXTRA_STREAM, shareUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                application.packageManager.queryIntentActivities(shareIntent, 0)
                    .forEach { resolveInfo ->
                        application.grantUriPermission(
                            resolveInfo.activityInfo.packageName,
                            shareUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }

                val chooser = Intent.createChooser(shareIntent, "分享照片")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                application.startActivity(chooser)

                _uiState.update { it.copy(isSharing = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSharing = false,
                        errorMessage = "分享失败: ${e.message}"
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun toSharableUri(uri: Uri): Uri {
        if (uri.scheme != "file") return uri

        val file = File(requireNotNull(uri.path))
        return FileProvider.getUriForFile(
            getApplication(),
            "${getApplication<Application>().packageName}.fileprovider",
            file
        )
    }
}
