package com.watermarkcamera.ui.camera

import android.net.Uri

/**
 * 相机界面状态
 */
data class CameraUiState(
    val isLoading: Boolean = false,
    val isCameraReady: Boolean = false,
    val capturedPhotoUri: Uri? = null,
    val locationData: LocationUiData? = null,
    val errorMessage: String? = null,
    val hasCameraPermission: Boolean = false,
    val hasLocationPermission: Boolean = false,
    val showPermissionRationale: Boolean = false,
    val isUsingFrontCamera: Boolean = false
)

/**
 * 位置UI数据
 */
data class LocationUiData(
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val isLoading: Boolean = false
)
