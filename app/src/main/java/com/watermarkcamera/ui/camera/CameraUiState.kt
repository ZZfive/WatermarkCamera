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
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val statusMessage: String? = null,
    val isLoading: Boolean = false,
    val addressResolved: Boolean = false
)

fun LocationUiData.hasCoordinates(): Boolean {
    return latitude != null && longitude != null
}

fun LocationUiData.hasAddress(): Boolean {
    return addressResolved && !address.isNullOrBlank()
}

fun LocationUiData.coordinateText(): String? {
    val lat = latitude ?: return null
    val lng = longitude ?: return null
    return "纬度: ${String.format(java.util.Locale.US, "%.6f", lat)}  经度: ${String.format(java.util.Locale.US, "%.6f", lng)}"
}
