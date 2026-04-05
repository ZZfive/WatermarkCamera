package com.watermarkcamera.location

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
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

    companion object {
        private const val TAG = "LocationManager"
        private const val NETWORK_TIMEOUT_MS = 8000L   // 网络定位8秒超时
        private const val GPS_TIMEOUT_MS = 15000L       // GPS定位15秒超时
    }

    /**
     * 获取当前位置 - 多种策略组合确保稳定获取
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): LocationData? {
        // 策略1: 尝试 lastKnownLocation（最快，毫秒级）
        try {
            val lastLocation = fusedLocationClient.lastLocation
            val result = suspendCancellableCoroutine<LocationData?> { continuation ->
                lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        Log.d(TAG, "lastKnownLocation success: ${location.latitude}, ${location.longitude}")
                        continuation.resume(LocationData(location.latitude, location.longitude))
                    } else {
                        Log.d(TAG, "lastKnownLocation is null")
                        continuation.resume(null)
                    }
                }.addOnFailureListener { e ->
                    Log.w(TAG, "lastKnownLocation failed: ${e.message}")
                    continuation.resume(null)
                }
            }
            if (result != null) return result
        } catch (e: Exception) {
            Log.w(TAG, "lastKnownLocation exception: ${e.message}")
        }

        // 策略2: 网络定位（室内可用，10秒内）
        val networkLocation = try {
            requestLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, NETWORK_TIMEOUT_MS)
        } catch (e: Exception) {
            Log.w(TAG, "Network location failed: ${e.message}")
            null
        }
        if (networkLocation != null) {
            Log.d(TAG, "Network location success: ${networkLocation.latitude}, ${networkLocation.longitude}")
            return networkLocation
        }

        // 策略3: GPS定位（室外准确，需要15秒）
        val gpsLocation = try {
            requestLocation(Priority.PRIORITY_HIGH_ACCURACY, GPS_TIMEOUT_MS)
        } catch (e: Exception) {
            Log.w(TAG, "GPS location failed: ${e.message}")
            null
        }
        if (gpsLocation != null) {
            Log.d(TAG, "GPS location success: ${gpsLocation.latitude}, ${gpsLocation.longitude}")
            return gpsLocation
        }

        Log.w(TAG, "All location methods failed")
        return null
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestLocation(priority: Int, timeoutMs: Long): LocationData? {
        return suspendCancellableCoroutine { continuation ->
            val cancellationToken = CancellationTokenSource()

            // 设置超时
            val timeoutHandler = Handler(Looper.getMainLooper())
            val timeoutRunnable = Runnable {
                cancellationToken.cancel()
                Log.d(TAG, "Location request timed out after ${timeoutMs}ms")
                continuation.resume(null)
            }
            timeoutHandler.postDelayed(timeoutRunnable, timeoutMs)

            fusedLocationClient.getCurrentLocation(priority, cancellationToken.token)
                .addOnSuccessListener { location ->
                    timeoutHandler.removeCallbacks(timeoutRunnable)
                    if (location != null) {
                        continuation.resume(LocationData(location.latitude, location.longitude))
                    } else {
                        continuation.resume(null)
                    }
                }
                .addOnFailureListener { e ->
                    timeoutHandler.removeCallbacks(timeoutRunnable)
                    Log.w(TAG, "getCurrentLocation failed: ${e.message}")
                    continuation.resume(null)
                }

            continuation.invokeOnCancellation {
                timeoutHandler.removeCallbacks(timeoutRunnable)
                cancellationToken.cancel()
            }
        }
    }

    /**
     * 获取位置并解析地址
     */
    suspend fun getLocationWithAddress(): LocationData? {
        val location = getCurrentLocation() ?: return null

        // 尝试获取地址
        val address = try {
            geocodingHelper.getAddressFromLocation(location.latitude, location.longitude)
        } catch (e: Exception) {
            Log.w(TAG, "Geocoding failed: ${e.message}")
            null
        }

        return LocationData(
            latitude = location.latitude,
            longitude = location.longitude,
            address = address ?: formatAsCoordinates(location.latitude, location.longitude)
        )
    }

    private fun formatAsCoordinates(lat: Double, lng: Double): String {
        return "${String.format("%.4f", lat)}, ${String.format("%.4f", lng)}"
    }

    /**
     * 异步获取位置
     */
    @SuppressLint("MissingPermission")
    fun getLastKnownLocation(callback: (LocationData?) -> Unit) {
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    callback(LocationData(location.latitude, location.longitude))
                } else {
                    callback(null)
                }
            }
            .addOnFailureListener {
                callback(null)
            }
    }
}