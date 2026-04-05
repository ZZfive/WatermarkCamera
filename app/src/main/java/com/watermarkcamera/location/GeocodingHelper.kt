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

                // 使用同步方式获取地址（简化逻辑）
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)

                if (!addresses.isNullOrEmpty()) {
                    formatAddress(addresses[0])
                } else {
                    null
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
            // 国家
            addr.countryName?.let { append(it) }
            // 省份/直辖市
            addr.adminArea?.let {
                if (isNotEmpty()) append(" ")
                append(it)
            }
            // 城市
            addr.locality?.let {
                if (isNotEmpty()) append(" ")
                append(it)
            }
            // 区/县
            addr.subLocality?.let {
                if (isNotEmpty()) append(" ")
                append(it)
            }
            // 街道
            addr.thoroughfare?.let {
                if (isNotEmpty()) append(" ")
                append(it)
            }
            // 门牌号
            addr.subThoroughfare?.let {
                if (isNotEmpty()) append(" ")
                append(it)
            }
        }.takeIf { it.isNotEmpty() }
    }
}