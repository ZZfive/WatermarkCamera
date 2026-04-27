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
import com.watermarkcamera.location.LocationFetchResult
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

                if (_uiState.value.hasLocationPermission && !_uiState.value.isManualLocationLocked) {
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
        if (hasLocationPermission && !_uiState.value.isManualLocationLocked) {
            fetchLocation()
        }
    }

    fun setManualLocation(place: ManualPlaceData) {
        _uiState.update {
            it.copy(
                locationData = place.toLocationUiData(),
                isManualLocationLocked = true
            )
        }
    }

    fun consumeReturnedPlace(place: ManualPlaceData?) {
        if (place != null) {
            setManualLocation(place)
        }
    }

    fun refreshAutoLocation() {
        if (_uiState.value.isManualLocationLocked) {
            switchToAutoLocation()
        } else {
            fetchLocation()
        }
    }

    fun switchToAutoLocation() {
        _uiState.update {
            it.copy(
                isManualLocationLocked = false,
                locationData = it.locationData?.copy(
                    source = LocationSource.AUTO,
                    title = null,
                    isLoading = true,
                    statusMessage = "正在获取位置..."
                ) ?: LocationUiData(
                    source = LocationSource.AUTO,
                    isLoading = true,
                    statusMessage = "正在获取位置..."
                )
            )
        }

        if (_uiState.value.hasLocationPermission) {
            fetchLocation()
        }
    }

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
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { current ->
                if (current.isManualLocationLocked) {
                    current
                } else {
                    current.copy(
                        locationData = current.locationData?.copy(
                            isLoading = true,
                            statusMessage = "正在获取位置...",
                            source = LocationSource.AUTO,
                            title = null
                        ) ?: LocationUiData(
                            statusMessage = "正在获取位置...",
                            isLoading = true,
                            source = LocationSource.AUTO
                        )
                    )
                }
            }

            if (_uiState.value.isManualLocationLocked) {
                return@launch
            }

            try {
                when (val result = locationManager.getLocationResult()) {
                    is LocationFetchResult.Success -> {
                        val locationData = result.location.toUiData()
                        _uiState.update { current ->
                            if (current.isManualLocationLocked) current else current.copy(locationData = locationData)
                        }
                    }
                    is LocationFetchResult.Partial -> {
                        val locationData = result.location.toUiData(
                            statusMessage = locationManager.getPartialMessage(result.reason)
                        )
                        _uiState.update { current ->
                            if (current.isManualLocationLocked) current else current.copy(locationData = locationData)
                        }
                    }
                    is LocationFetchResult.Failure -> {
                        val locationData = LocationUiData(
                            statusMessage = locationManager.getFailureMessage(result.reason),
                            isLoading = false,
                            source = LocationSource.AUTO
                        )
                        _uiState.update { current ->
                            if (current.isManualLocationLocked) current else current.copy(locationData = locationData)
                        }
                    }
                }
            } catch (e: Exception) {
                val locationData = LocationUiData(
                    statusMessage = "位置不可用",
                    isLoading = false,
                    source = LocationSource.AUTO
                )
                _uiState.update { current ->
                    if (current.isManualLocationLocked) current else current.copy(locationData = locationData)
                }
            }
        }
    }

    private fun com.watermarkcamera.location.LocationData.toUiData(statusMessage: String? = null): LocationUiData {
        return LocationUiData(
            address = address,
            latitude = latitude,
            longitude = longitude,
            statusMessage = statusMessage,
            isLoading = false,
            addressResolved = addressResolved,
            source = LocationSource.AUTO
        )
    }

    fun takePicture() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }

            try {
                val originalBitmap = cameraXManager.takePictureToBitmap()
                if (originalBitmap != null) {
                    val watermarkedBitmap = composeWatermarkBitmap(originalBitmap)
                    if (watermarkedBitmap != null) {
                        val watermarkedUri = saveBitmapToGallery(watermarkedBitmap, "Watermark_")

                        if (preferences.saveOriginal) {
                            saveBitmapToGallery(originalBitmap, "Original_")
                        }

                        withContext(Dispatchers.Main) {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    capturedPhotoUri = watermarkedUri
                                )
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    errorMessage = "水印合成失败"
                                )
                            }
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = "拍照失败"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "拍照失败: ${e.message}"
                        )
                    }
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
