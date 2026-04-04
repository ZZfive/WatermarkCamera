package com.watermarkcamera.ui.camera

import android.app.Application
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.camera.view.PreviewView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.watermarkcamera.camera.CameraXManager
import com.watermarkcamera.data.WatermarkPreferences
import com.watermarkcamera.location.LocationManager
import com.watermarkcamera.watermark.WatermarkComposer
import com.watermarkcamera.watermark.WatermarkConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private lateinit var cameraXManager: CameraXManager
    private val locationManager = LocationManager(application)
    private lateinit var watermarkComposer: WatermarkComposer
    private val preferences = WatermarkPreferences(application)

    // 从 SharedPreferences 读取设置
    val showTimestamp: Boolean get() = preferences.showTimestamp
    val showLocation: Boolean get() = preferences.showLocation
    val showCustomText: Boolean get() = preferences.showCustomText
    val customText: String get() = preferences.customText

    fun initialize(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        watermarkComposer = WatermarkComposer(getApplication())

        viewModelScope.launch {
            cameraXManager = CameraXManager(getApplication(), lifecycleOwner)
            try {
                val started = cameraXManager.startCamera(previewView)
                _uiState.update { it.copy(isCameraReady = started) }

                // 自动获取位置
                if (_uiState.value.hasLocationPermission) {
                    fetchLocation()
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "相机初始化失败: ${e.message}") }
            }
        }
    }

    fun onPermissionResult(hasCameraPermission: Boolean, hasLocationPermission: Boolean) {
        _uiState.update {
            it.copy(
                hasCameraPermission = hasCameraPermission,
                hasLocationPermission = hasLocationPermission
            )
        }
        if (hasLocationPermission) {
            fetchLocation()
        }
    }

    fun fetchLocation() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(locationData = it.locationData?.copy(isLoading = true) ?: LocationUiData(
                    address = "正在获取位置...",
                    latitude = 0.0,
                    longitude = 0.0,
                    isLoading = true
                ))
            }

            try {
                val locationData = locationManager.getLocationWithAddress()
                if (locationData != null) {
                    _uiState.update {
                        it.copy(
                            locationData = LocationUiData(
                                address = locationData.address,
                                latitude = locationData.latitude,
                                longitude = locationData.longitude,
                                isLoading = false
                            )
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            locationData = LocationUiData(
                                address = "位置获取失败",
                                latitude = 0.0,
                                longitude = 0.0,
                                isLoading = false
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        locationData = LocationUiData(
                            address = "位置不可用",
                            latitude = 0.0,
                            longitude = 0.0,
                            isLoading = false
                        )
                    )
                }
            }
        }
    }

    fun takePicture() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                val originalUri = cameraXManager.takePicture()
                if (originalUri != null) {
                    // 合成水印
                    val watermarkedUri = composeWatermark(originalUri)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            capturedPhotoUri = watermarkedUri
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "拍照失败"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "拍照失败: ${e.message}"
                    )
                }
            }
        }
    }

    private suspend fun composeWatermark(originalUri: Uri): Uri? {
        return withContext(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()

                // 读取原始图片
                val inputStream = context.contentResolver.openInputStream(originalUri)
                val originalBitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                if (originalBitmap == null) return@withContext null

                // 从 SharedPreferences 读取水印配置
                val locationData = _uiState.value.locationData
                val watermarkConfig = WatermarkConfig(
                    showText = preferences.showCustomText && preferences.customText.isNotEmpty(),
                    customText = preferences.customText,
                    showLocation = preferences.showLocation && locationData != null && locationData.address.isNotEmpty(),
                    locationAddress = locationData?.address ?: "",
                    latitude = locationData?.latitude,
                    longitude = locationData?.longitude,
                    showTimestamp = preferences.showTimestamp,
                    timestamp = System.currentTimeMillis()
                )

                // 合成水印
                val watermarkedBitmap = watermarkComposer.composeWatermark(originalBitmap, watermarkConfig)

                // 保存到相册
                saveToGallery(context, watermarkedBitmap)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    private fun saveToGallery(context: android.content.Context, bitmap: Bitmap): Uri? {
        val filename = "Watermark_${System.currentTimeMillis()}.jpg"

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/WatermarkCamera")
            }

            val uri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )

            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { os ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, os)
                }
            }

            uri
        } else {
            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val watermarkDir = File(picturesDir, "WatermarkCamera")
            if (!watermarkDir.exists()) watermarkDir.mkdirs()

            val file = File(watermarkDir, filename)
            FileOutputStream(file).use { os ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, os)
            }

            Uri.fromFile(file)
        }
    }

    fun clearCapturedPhoto() {
        _uiState.update { it.copy(capturedPhotoUri = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        if (::cameraXManager.isInitialized) {
            cameraXManager.stopCamera()
        }
    }
}