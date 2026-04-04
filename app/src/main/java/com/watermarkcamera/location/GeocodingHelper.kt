package com.watermarkcamera.location

import android.content.Context
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * 地理编码助手 - 经纬度转地址
 */
class GeocodingHelper(private val context: Context) {

    /**
     * 根据经纬度获取地址
     */
    suspend fun getAddressFromLocation(latitude: Double, longitude: Double): String? {
        // 检查Geocoder是否可用
        if (!Geocoder.isPresent()) {
            return null
        }

        return withContext(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(context, Locale.getDefault())

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // Android 13+ 使用异步方式
                    var result: String? = null
                    val latch = java.util.concurrent.CountDownLatch(1)
                    geocoder.getFromLocation(latitude, longitude, 1) { addrs ->
                        result = formatAddress(addrs.firstOrNull())
                        latch.countDown()
                    }
                    try {
                        latch.await(5, java.util.concurrent.TimeUnit.SECONDS)
                    } catch (e: InterruptedException) {
                        // ignore
                    }
                    result
                } else {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                    formatAddress(addresses?.firstOrNull())
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    private fun formatAddress(addr: android.location.Address?): String? {
        if (addr == null) return null

        return buildString {
            // 国家和城市
            addr.countryName?.let { append(it) }
            addr.adminArea?.let {
                if (isNotEmpty()) append(" ")
                append(it)
            }
            addr.locality?.let {
                if (isNotEmpty()) append(" ")
                append(it)
            }

            // 如果太短，尝试添加街道
            if (length < 6) {
                addr.thoroughfare?.let {
                    if (isNotEmpty()) append(" ")
                    append(it)
                }
            }

            // 门牌号
            addr.subThoroughfare?.let {
                if (isNotEmpty()) append(" ")
                append(it)
            }
        }.takeIf { it.isNotEmpty() }
    }

    /**
     * 格式化地址，保留关键信息
     */
    fun formatAddress(address: String): String {
        // 简化地址，过长的地址只保留关键部分
        return if (address.length > 30) {
            address.take(27) + "..."
        } else {
            address
        }
    }
}