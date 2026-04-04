package com.watermarkcamera.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

/**
 * 位置数据
 */
data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val address: String = ""
)

/**
 * 位置管理器
 */
class LocationManager(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val geocodingHelper = GeocodingHelper(context)

    /**
     * 获取当前位置（一次性）- 使用 getCurrentLocation 更可靠
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): LocationData? {
        return try {
            withTimeout(LOCATION_TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                    val cancellationToken = CancellationTokenSource()

                    fusedLocationClient.getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        cancellationToken.token
                    ).addOnSuccessListener { location: Location? ->
                        if (location != null) {
                            continuation.resume(LocationData(location.latitude, location.longitude))
                        } else {
                            fusedLocationClient.lastLocation.addOnSuccessListener { lastLocation ->
                                if (lastLocation != null) {
                                    continuation.resume(LocationData(lastLocation.latitude, lastLocation.longitude))
                                } else {
                                    continuation.resume(null)
                                }
                            }.addOnFailureListener {
                                continuation.resume(null)
                            }
                        }
                    }.addOnFailureListener {
                        continuation.resume(null)
                    }

                    continuation.invokeOnCancellation {
                        cancellationToken.cancel()
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 获取位置并解析地址
     */
    suspend fun getLocationWithAddress(): LocationData? {
        val location = getCurrentLocation() ?: return null

        // 尝试获取地址，如果失败则只返回经纬度
        val address = try {
            geocodingHelper.getAddressFromLocation(location.latitude, location.longitude)
        } catch (e: Exception) {
            null
        }

        return location.copy(address = address ?: formatLocationAsAddress(location.latitude, location.longitude))
    }

    /**
     * 将经纬度格式化为一个简短的地址字符串
     */
    private fun formatLocationAsAddress(lat: Double, lng: Double): String {
        return "${String.format("%.4f", lat)}, ${String.format("%.4f", lng)}"
    }

    /**
     * 异步获取位置（不阻塞）
     */
    @SuppressLint("MissingPermission")
    fun getLastKnownLocation(callback: (Location?) -> Unit) {
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                callback(location)
            }
            .addOnFailureListener {
                callback(null)
            }
    }

    companion object {
        private const val LOCATION_TIMEOUT_MS = 15000L // 15秒超时
    }
}