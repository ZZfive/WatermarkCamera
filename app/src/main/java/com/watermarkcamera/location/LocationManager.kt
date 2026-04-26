package com.watermarkcamera.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager as AndroidLocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * 位置数据
 */
data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val address: String? = null,
    val addressResolved: Boolean = false
)

sealed interface LocationFetchResult {
    data class Success(val location: LocationData) : LocationFetchResult
    data class Partial(val location: LocationData, val reason: PartialReason) : LocationFetchResult
    data class Failure(val reason: FailureReason) : LocationFetchResult
}

enum class PartialReason {
    ADDRESS_UNAVAILABLE
}

enum class FailureReason {
    LOCATION_DISABLED,
    LOCATION_UNAVAILABLE,
    PLAY_SERVICES_UNAVAILABLE,
    TIMEOUT
}

private data class RawLocation(
    val latitude: Double,
    val longitude: Double
)

/**
 * 位置管理器
 *
 * 采用多级回退策略，优先保证国产手机/老旧手机定位成功率：
 * 1. FusedLocationProvider 单次请求（快且准）
 * 2. FusedLocationProvider 监听更新（更可靠）
 * 3. 系统 GPS 定位（不依赖 GMS）
 * 4. 系统网络定位（室内可用）
 * 5. 最后已知位置（兜底）
 */
class LocationManager(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val geocodingHelper = GeocodingHelper(context)
    private val systemLocationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as AndroidLocationManager

    companion object {
        private const val TAG = "LocationManager"
        private const val SINGLE_REQUEST_TIMEOUT_MS = 10000L
        private const val LISTEN_TIMEOUT_MS = 15000L
        private const val SYSTEM_LISTEN_TIMEOUT_MS = 15000L
        private const val LAST_LOCATION_MAX_AGE_MS = 30 * 60 * 1000L // 放宽到30分钟
    }

    private fun isLocationEnabled(): Boolean {
        return try {
            systemLocationManager.isProviderEnabled(AndroidLocationManager.GPS_PROVIDER) ||
                systemLocationManager.isProviderEnabled(AndroidLocationManager.NETWORK_PROVIDER)
        } catch (e: Exception) {
            true
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun getLocationResult(): LocationFetchResult {
        if (!isLocationEnabled()) {
            Log.d(TAG, "System location is disabled")
            return LocationFetchResult.Failure(FailureReason.LOCATION_DISABLED)
        }

        return try {
            val rawLocation = tryAllStrategies()
            if (rawLocation == null) {
                Log.w(TAG, "All location strategies failed")
                return LocationFetchResult.Failure(FailureReason.LOCATION_UNAVAILABLE)
            }

            val address = try {
                geocodingHelper.getAddressFromLocation(rawLocation.latitude, rawLocation.longitude)
            } catch (e: Exception) {
                Log.w(TAG, "Geocoding failed: ${e.message}")
                null
            }

            val locationData = LocationData(
                latitude = rawLocation.latitude,
                longitude = rawLocation.longitude,
                address = address,
                addressResolved = !address.isNullOrBlank()
            )

            if (locationData.addressResolved) {
                LocationFetchResult.Success(locationData)
            } else {
                LocationFetchResult.Partial(locationData, PartialReason.ADDRESS_UNAVAILABLE)
            }
        } catch (e: SecurityException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Location fetch failed: ${e.message}")
            LocationFetchResult.Failure(FailureReason.LOCATION_UNAVAILABLE)
        }
    }

    /**
     * 尝试所有定位策略，按优先级从高到低
     */
    @SuppressLint("MissingPermission")
    private suspend fun tryAllStrategies(): RawLocation? {
        // 策略1: FusedLocation 单次请求（最快最准）
        try {
            val location = requestFusedSingleLocation()
            if (location != null) {
                Log.d(TAG, "Strategy 1 success: FusedLocation single request")
                return location
            }
        } catch (e: Exception) {
            Log.w(TAG, "Strategy 1 failed: ${e.message}")
        }

        // 策略2: FusedLocation 监听更新（更可靠）
        try {
            val location = requestFusedLocationUpdates()
            if (location != null) {
                Log.d(TAG, "Strategy 2 success: FusedLocation updates")
                return location
            }
        } catch (e: Exception) {
            Log.w(TAG, "Strategy 2 failed: ${e.message}")
        }

        // 策略3: 系统 GPS 定位（不依赖 GMS）
        try {
            val location = requestSystemGpsLocation()
            if (location != null) {
                Log.d(TAG, "Strategy 3 success: System GPS")
                return location
            }
        } catch (e: Exception) {
            Log.w(TAG, "Strategy 3 failed: ${e.message}")
        }

        // 策略4: 系统网络定位（室内可用）
        try {
            val location = requestSystemNetworkLocation()
            if (location != null) {
                Log.d(TAG, "Strategy 4 success: System Network")
                return location
            }
        } catch (e: Exception) {
            Log.w(TAG, "Strategy 4 failed: ${e.message}")
        }

        // 策略5: 最后已知位置（兜底，放宽时间限制）
        try {
            val location = tryGetLastLocation()
            if (location != null) {
                Log.d(TAG, "Strategy 5 success: Last known location")
                return location
            }
        } catch (e: Exception) {
            Log.w(TAG, "Strategy 5 failed: ${e.message}")
        }

        return null
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestFusedSingleLocation(): RawLocation? {
        return suspendCancellableCoroutine { continuation ->
            val cancellationToken = com.google.android.gms.tasks.CancellationTokenSource()
            try {
                fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    cancellationToken.token
                ).addOnSuccessListener { location ->
                    if (location != null) {
                        continuation.resume(
                            RawLocation(location.latitude, location.longitude)
                        )
                    } else {
                        continuation.resume(null)
                    }
                }.addOnFailureListener {
                    continuation.resume(null)
                }

                // 超时取消
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    if (!continuation.isCompleted) {
                        cancellationToken.cancel()
                        continuation.resume(null)
                    }
                }, SINGLE_REQUEST_TIMEOUT_MS)
            } catch (e: Exception) {
                if (!continuation.isCompleted) {
                    continuation.resume(null)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestFusedLocationUpdates(): RawLocation? {
        return suspendCancellableCoroutine { continuation ->
            val locationRequest = LocationRequest().apply {
                priority = Priority.PRIORITY_BALANCED_POWER_ACCURACY
                interval = 10000L
                numUpdates = 1
            }

            var locationCallback: LocationCallback? = null
            locationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val location = result.lastLocation
                    if (location != null) {
                        fusedLocationClient.removeLocationUpdates(locationCallback!!)
                        continuation.resume(
                            RawLocation(location.latitude, location.longitude)
                        )
                    }
                }
            }

            try {
                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    Looper.getMainLooper()
                ).addOnFailureListener {
                    if (!continuation.isCompleted) {
                        continuation.resume(null)
                    }
                }
            } catch (e: Exception) {
                if (!continuation.isCompleted) {
                    continuation.resume(null)
                }
                return@suspendCancellableCoroutine
            }

            android.os.Handler(Looper.getMainLooper()).postDelayed({
                if (!continuation.isCompleted) {
                    try {
                        fusedLocationClient.removeLocationUpdates(locationCallback)
                    } catch (e: Exception) {
                        // ignore
                    }
                    continuation.resume(null)
                }
            }, LISTEN_TIMEOUT_MS)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestSystemGpsLocation(): RawLocation? {
        return requestSystemLocation(AndroidLocationManager.GPS_PROVIDER, SYSTEM_LISTEN_TIMEOUT_MS)
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestSystemNetworkLocation(): RawLocation? {
        return requestSystemLocation(AndroidLocationManager.NETWORK_PROVIDER, SYSTEM_LISTEN_TIMEOUT_MS)
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestSystemLocation(provider: String, timeoutMs: Long): RawLocation? {
        if (!systemLocationManager.isProviderEnabled(provider)) {
            return null
        }

        return suspendCancellableCoroutine { continuation ->
            val latch = CountDownLatch(1)
            var resultLocation: Location? = null

            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    resultLocation = location
                    latch.countDown()
                }
            }

            try {
                systemLocationManager.requestLocationUpdates(
                    provider,
                    0L,
                    0f,
                    listener
                )
            } catch (e: Exception) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }

            @Suppress("UNUSED_VARIABLE")
            val completed = latch.await(timeoutMs, TimeUnit.MILLISECONDS)

            try {
                systemLocationManager.removeUpdates(listener)
            } catch (e: Exception) {
                // ignore
            }

            if (resultLocation != null) {
                continuation.resume(
                    RawLocation(resultLocation!!.latitude, resultLocation!!.longitude)
                )
            } else {
                val lastLocation = systemLocationManager.getLastKnownLocation(provider)
                if (lastLocation != null) {
                    continuation.resume(
                        RawLocation(lastLocation.latitude, lastLocation.longitude)
                    )
                } else {
                    continuation.resume(null)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun tryGetLastLocation(): RawLocation? {
        // 先尝试 fused
        var lastLocation: Location? = null
        val latch = CountDownLatch(1)
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                lastLocation = location
                latch.countDown()
            }.addOnFailureListener {
                latch.countDown()
            }
        } catch (e: Exception) {
            latch.countDown()
        }
        latch.await(3000, TimeUnit.MILLISECONDS)

        if (lastLocation != null) {
            val ageMs = System.currentTimeMillis() - lastLocation!!.time
            if (ageMs <= LAST_LOCATION_MAX_AGE_MS) {
                return RawLocation(lastLocation!!.latitude, lastLocation!!.longitude)
            }
        }

        // 再尝试系统 GPS
        try {
            val gpsLast = systemLocationManager.getLastKnownLocation(AndroidLocationManager.GPS_PROVIDER)
            if (gpsLast != null) {
                return RawLocation(gpsLast.latitude, gpsLast.longitude)
            }
        } catch (e: Exception) {
            // ignore
        }

        // 再尝试网络定位
        try {
            val networkLast = systemLocationManager.getLastKnownLocation(AndroidLocationManager.NETWORK_PROVIDER)
            if (networkLast != null) {
                return RawLocation(networkLast.latitude, networkLast.longitude)
            }
        } catch (e: Exception) {
            // ignore
        }

        return null
    }

    suspend fun getLocationWithAddress(): LocationData? {
        return when (val result = getLocationResult()) {
            is LocationFetchResult.Success -> result.location
            is LocationFetchResult.Partial -> result.location.copy(
                address = formatCoordinates(result.location.latitude, result.location.longitude)
            )
            is LocationFetchResult.Failure -> null
        }
    }

    fun getFailureMessage(reason: FailureReason): String {
        return when (reason) {
            FailureReason.LOCATION_DISABLED -> "系统定位未开启"
            FailureReason.LOCATION_UNAVAILABLE -> "暂时无法获取位置"
            FailureReason.PLAY_SERVICES_UNAVAILABLE -> "定位服务不可用"
            FailureReason.TIMEOUT -> "获取位置超时"
        }
    }

    fun getPartialMessage(reason: PartialReason): String {
        return when (reason) {
            PartialReason.ADDRESS_UNAVAILABLE -> "已获取经纬度，地址解析失败"
        }
    }

    fun formatCoordinates(lat: Double, lng: Double): String {
        return "${String.format("%.4f", lat)}, ${String.format("%.4f", lng)}"
    }

    /**
     * 异步获取位置（保留旧接口兼容）
     */
    @SuppressLint("MissingPermission")
    fun getLastKnownLocation(callback: (LocationData?) -> Unit) {
        try {
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
        } catch (e: Exception) {
            callback(null)
        }
    }
}
