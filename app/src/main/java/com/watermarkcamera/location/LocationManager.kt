package com.watermarkcamera.location

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
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

    /**
     * 获取当前位置 - 优先获取 lastKnownLocation，更快更稳定
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): LocationData? {
        return suspendCancellableCoroutine { continuation ->
            // 先尝试 lastKnownLocation（快速）
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        continuation.resume(LocationData(location.latitude, location.longitude))
                    } else {
                        // 如果没有 lastKnownLocation，尝试获取当前位置
                        requestFreshLocation { freshLocation ->
                            continuation.resume(freshLocation)
                        }
                    }
                }
                .addOnFailureListener {
                    // lastKnownLocation 失败，尝试获取当前位置
                    requestFreshLocation { freshLocation ->
                        continuation.resume(freshLocation)
                    }
                }
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestFreshLocation(callback: (LocationData?) -> Unit) {
        val cancellationToken = CancellationTokenSource()

        // 5秒超时
        Handler(Looper.getMainLooper()).postDelayed({
            cancellationToken.cancel()
            callback(null)
        }, 5000)

        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            cancellationToken.token
        ).addOnSuccessListener { location ->
            if (location != null) {
                callback(LocationData(location.latitude, location.longitude))
            } else {
                callback(null)
            }
        }.addOnFailureListener {
            callback(null)
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