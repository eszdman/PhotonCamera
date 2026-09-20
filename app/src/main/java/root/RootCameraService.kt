package com.particlesdevs.photoncamera.root

import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.topjohnwu.superuser.ipc.RootService
import java.io.File

class RootCameraService : RootService() {

    private val binder = object : Binder(), IRootCameraOps {
        override fun configureRawSession(sessionId: Int): Boolean {
            return try {
                // Прямой вызов HAL для инициализации RAW10/RAW_SENSOR
                val cmd = "service call media.camera 1 i32 $sessionId"
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
                process.waitFor() == 0
            } catch (e: Exception) {
                Log.e("RootCameraService", "HAL Override Failed", e)
                false
            }
        }

        override fun triggerBurstCapture(frameCount: Int): Boolean {
            return try {
                // Жестко запрашиваем 15 кадров подряд
                val cmd = "cmd camera2 provider-call capture-burst --count $frameCount"
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
                process.waitFor() == 0
            } catch (e: Exception) {
                Log.e("RootCameraService", "Burst Failed", e)
                false
            }
        }

        override fun lockSensorRegisters(gain: Int, exposure: Long) {
            val path = "/sys/devices/platform/interconnect/camera_sensor/raw_lock"
            val file = File(path)
            if (file.exists()) {
                file.writeText("$gain $exposure")
            }
        }
    }

    override fun onBind(intent: Intent): IBinder = binder

    interface IRootCameraOps {
        fun configureRawSession(sessionId: Int): Boolean
        fun triggerBurstCapture(frameCount: Int): Boolean
        fun lockSensorRegisters(gain: Int, exposure: Long)
    }
}