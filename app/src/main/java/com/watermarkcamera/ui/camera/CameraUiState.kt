package com.watermarkcamera.ui.camera

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

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
    val isUsingFrontCamera: Boolean = false,
    val isManualLocationLocked: Boolean = false
)

enum class LocationSource {
    AUTO,
    MANUAL
}

/**
 * 位置UI数据
 */
data class LocationUiData(
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val statusMessage: String? = null,
    val isLoading: Boolean = false,
    val addressResolved: Boolean = false,
    val source: LocationSource = LocationSource.AUTO,
    val title: String? = null
)

@Parcelize
data class ManualPlaceData(
    val title: String,
    val address: String,
    val latitude: Double,
    val longitude: Double
) : Parcelable

fun ManualPlaceData.toLocationUiData(): LocationUiData {
    return LocationUiData(
        address = address,
        latitude = latitude,
        longitude = longitude,
        statusMessage = "手动选择的位置",
        isLoading = false,
        addressResolved = true,
        source = LocationSource.MANUAL,
        title = title
    )
}

fun LocationUiData.isManual(): Boolean {
    return source == LocationSource.MANUAL
}

fun LocationUiData.displayAddress(): String? {
    return when {
        !title.isNullOrBlank() && !address.isNullOrBlank() && title != address -> "$title · $address"
        !address.isNullOrBlank() -> address
        !title.isNullOrBlank() -> title
        else -> null
    }
}

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
