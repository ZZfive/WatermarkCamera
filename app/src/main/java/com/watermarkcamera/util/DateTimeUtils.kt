package com.watermarkcamera.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateTimeUtils {

    private val dateTimeFormat = SimpleDateFormat("yyyy年MM月dd日 HH:mm:ss", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun formatDateTime(timestamp: Long = System.currentTimeMillis()): String {
        return dateTimeFormat.format(Date(timestamp))
    }

    fun formatDate(timestamp: Long = System.currentTimeMillis()): String {
        return dateFormat.format(Date(timestamp))
    }

    fun formatTime(timestamp: Long = System.currentTimeMillis()): String {
        return timeFormat.format(Date(timestamp))
    }

    fun formatForFilename(timestamp: Long = System.currentTimeMillis()): String {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        return sdf.format(Date(timestamp))
    }
}