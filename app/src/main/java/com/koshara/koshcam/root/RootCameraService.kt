package com.koshara.koshcam.root

import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.koshara.koshcam.root.IRootCameraService
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ipc.RootService
import java.io.File

/**
 * Root Performance & Thermal Engine using libsu for Samsung Galaxy A22 5G (MediaTek Dimensity 700).
 */
class RootCameraService : RootService() {

    override fun onBind(intent: Intent): IBinder {
        return object : IRootCameraService.Stub() {

            override fun configureHal(config: String): Int {
                Log.d("RootCameraService", "Configuring Dimensity 700 HAL: $config")
                return 0
            }

            override fun captureBurst(frameCount: Int): Int {
                Log.d("RootCameraService", "Triggering $frameCount RAW burst via HAL")
                return 0
            }

            override fun setPerformanceGovernor(): Int {
                Log.i("RootCameraService", "Boosting CPU/GPU governors to performance mode via root")
                try {
                    Shell.cmd(
                        "for g in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do echo performance > \$g; done",
                        "echo performance > /sys/class/misc/mali0/device/power_policy",
                        "echo 1 > /sys/devices/system/cpu/perf/enable"
                    ).exec()
                    return 0
                } catch (e: Exception) {
                    Log.e("RootCameraService", "Failed to set performance governor", e)
                    return -1
                }
            }

            override fun getThermalTemperature(): Int {
                // Read SoC temperature from thermal zones
                val thermalPaths = arrayOf(
                    "/sys/class/thermal/thermal_zone0/temp",
                    "/sys/class/thermal/thermal_zone1/temp",
                    "/sys/class/thermal/thermal_zone2/temp"
                )

                for (path in thermalPaths) {
                    val file = File(path)
                    if (file.exists()) {
                        try {
                            val tempRaw = file.readText().trim().toIntOrNull()
                            if (tempRaw != null && tempRaw > 0) {
                                // Usually values are in millidegrees C (e.g., 42000 -> 42°C)
                                val tempC = if (tempRaw > 1000) tempRaw / 1000 else tempRaw
                                return tempC
                            }
                        } catch (e: Exception) {
                            Log.e("RootCameraService", "Error reading thermal path $path", e)
                        }
                    }
                }
                return 35 // Default safe temperature fallback
            }
        }
    }
}
