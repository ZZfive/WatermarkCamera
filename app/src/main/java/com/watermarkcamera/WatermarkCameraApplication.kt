package com.watermarkcamera

import android.app.Application
import com.amap.api.maps.MapsInitializer
import com.amap.api.services.core.ServiceSettings

class WatermarkCameraApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MapsInitializer.updatePrivacyShow(this, true, true)
        MapsInitializer.updatePrivacyAgree(this, true)
        ServiceSettings.updatePrivacyShow(this, true, true)
        ServiceSettings.updatePrivacyAgree(this, true)
    }
}