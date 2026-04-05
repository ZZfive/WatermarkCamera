package com.watermarkcamera.ui.camera

import android.app.Application
import android.content.ContentValues
import android.graphics.Bitmap
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    val customText: String get() = preferences.customText

    fun initialize(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        watermarkComposer = WatermarkComposer(getApplication())

        viewModelScope.launch {
            cameraXManager = CameraXManager(getApplication(), lifecycleOwner)
            try {
                val started = cameraXManager.startCamera(previewView)
                _uiState.update {
                    it.copy(
                        isCameraReady = started,
                        isUsingFrontCamera = cameraXManager.isUsingFrontCamera
                    )
                }

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

    /**
     * 切换前后摄像头
     */
    fun switchCamera(previewView: PreviewView) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val started = cameraXManager.switchCamera(previewView)
                _uiState.update {
                    it.copy(
                        isCameraReady = started,
                        isUsingFrontCamera = cameraXManager.isUsingFrontCamera,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "切换摄像头失败: ${e.message}"
                    )
                }
            }
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
                val originalBitmap = cameraXManager.takePictureToBitmap()
                if (originalBitmap != null) {
                    // 合成水印
                    val watermarkedBitmap = composeWatermarkBitmap(originalBitmap)
                    if (watermarkedBitmap != null) {
                        // 保存水印照片
                        val watermarkedUri = saveBitmapToGallery(watermarkedBitmap, "Watermark_")

                        // 如果设置保留原图，也保存原图
                        if (preferences.saveOriginal) {
                            saveBitmapToGallery(originalBitmap, "Original_")
                        }

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
                                errorMessage = "水印合成失败"
                            )
                        }
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

    private fun composeWatermarkBitmap(originalBitmap: Bitmap): Bitmap? {
        return try {
            val locationData = _uiState.value.locationData
            val layoutConfig = preferences.loadLayoutConfig()

            val watermarkConfig = WatermarkConfig(
                customText = preferences.customText,
                locationAddress = locationData?.address ?: "",
                latitude = locationData?.latitude,
                longitude = locationData?.longitude,
                timestamp = System.currentTimeMillis()
            )
            watermarkComposer.composeWatermark(originalBitmap, watermarkConfig, layoutConfig)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun saveBitmapToGallery(bitmap: Bitmap, prefix: String): Uri? {
        val context = getApplication<Application>()
        val filename = "${prefix}${System.currentTimeMillis()}.jpg"

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/WatermarkCamera")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val uri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )

            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { os ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, os)
                }
                val updateValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                context.contentResolver.update(it, updateValues, null, null)
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

            val uri = Uri.fromFile(file)
            val mediaScanIntent = android.content.Intent(android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            mediaScanIntent.data = uri
            context.sendBroadcast(mediaScanIntent)
            uri
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
