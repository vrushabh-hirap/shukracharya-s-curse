package com.vrushabhhirap.shukracharyascurse.utils

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import java.util.Locale

/**
 * Diagnostic utility gathering non-sensitive hardware and runtime environment
 * metadata for telemetry and diagnostic breadcrumbs.
 */
object DeviceInfoHelper {

    data class DeviceProfile(
        val manufacturer: String,
        val model: String,
        val brand: String,
        val device: String,
        val apiLevel: Int,
        val osVersion: String,
        val abi: String,
        val screenDensity: Int,
        val memoryClassMb: Int
    )

    fun getDeviceProfile(context: Context): DeviceProfile {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm?.defaultDisplay?.getMetrics(metrics)

        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        val memoryClass = am?.memoryClass ?: 64

        val primaryAbi = if (Build.SUPPORTED_ABIS.isNotEmpty()) {
            Build.SUPPORTED_ABIS[0]
        } else {
            "unknown"
        }

        return DeviceProfile(
            manufacturer = Build.MANUFACTURER ?: "unknown",
            model = Build.MODEL ?: "unknown",
            brand = Build.BRAND ?: "unknown",
            device = Build.DEVICE ?: "unknown",
            apiLevel = Build.VERSION.SDK_INT,
            osVersion = Build.VERSION.RELEASE ?: "unknown",
            abi = primaryAbi,
            screenDensity = metrics.densityDpi,
            memoryClassMb = memoryClass
        )
    }

    fun getFormattedSummary(context: Context): String {
        val p = getDeviceProfile(context)
        return String.format(
            Locale.US,
            "%s %s (API %d, %s, %d dpi)",
            p.manufacturer.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() },
            p.model,
            p.apiLevel,
            p.abi,
            p.screenDensity
        )
    }
}
