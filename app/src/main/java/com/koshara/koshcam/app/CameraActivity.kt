package com.koshara.koshcam.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.koshara.koshcam.root.IRootCameraService
import com.koshara.koshcam.root.RootCameraService
import com.topjohnwu.superuser.ipc.RootService

class CameraActivity : AppCompatActivity(), ServiceConnection {

    private lateinit var cameraManager: CameraManager
    private var cameraDevice: CameraDevice? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private lateinit var surfaceView: SurfaceView
    private lateinit var shutterButton: Button
    private lateinit var thermalHudText: TextView

    // IPC variables
    private var rootCameraService: IRootCameraService? = null
    private var isRootServiceBound = false

    private val thermalHandler = Handler(Looper.getMainLooper())
    private var isAlertShown = false

    private val thermalRunnable = object : Runnable {
        override fun run() {
            updateThermalHud()
            thermalHandler.postDelayed(this, 2000) // Poll every 2 seconds
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        setupProgrammaticUI()

        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1001)
        }

        bindRootService()
    }

    private fun setupProgrammaticUI() {
        val rootLayout = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        surfaceView = SurfaceView(this).apply {
            holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) {
                    openCamera()
                }
                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
                override fun surfaceDestroyed(holder: SurfaceHolder) {
                    cameraDevice?.close()
                }
            })
        }
        rootLayout.addView(surfaceView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        val overlayContainer = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#33000000"))
        }
        rootLayout.addView(overlayContainer, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // Thermal HUD Text View
        thermalHudText = TextView(this).apply {
            text = "SoC: --°C"
            textSize = 14f
            setTextColor(Color.GREEN)
            setBackgroundColor(Color.parseColor("#99000000"))
            setPadding(24, 12, 24, 12)
        }
        val hudParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.START
        ).apply {
            topMargin = 100
            leftMargin = 40
        }
        rootLayout.addView(thermalHudText, hudParams)

        shutterButton = Button(this).apply {
            text = "KOSHCAM AI"
            textSize = 16f
            setTextColor(Color.WHITE)
            val shape = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(Color.parseColor("#DDFF3D00"))
                setStroke(4, Color.WHITE)
            }
            background = shape
            setOnClickListener { triggerRawBurst() }
        }

        val buttonParams = FrameLayout.LayoutParams(
            220, 220,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        ).apply {
            bottomMargin = 100
        }
        rootLayout.addView(shutterButton, buttonParams)

        setContentView(rootLayout)
    }

    private fun bindRootService() {
        val intent = Intent(this, RootCameraService::class.java)
        RootService.bind(intent, this)
    }

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        rootCameraService = IRootCameraService.Stub.asInterface(service)
        isRootServiceBound = true
        Log.i("CameraActivity", "Koshcam RootService bound successfully")
        
        try {
            rootCameraService?.setPerformanceGovernor()
        } catch (e: Exception) {
            Log.e("CameraActivity", "Failed to enable performance governor", e)
        }

        thermalHandler.post(thermalRunnable)
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        rootCameraService = null
        isRootServiceBound = false
        Log.e("CameraActivity", "Koshcam RootService disconnected")
    }

    private fun updateThermalHud() {
        if (rootCameraService == null) return

        try {
            val tempC = rootCameraService!!.thermalTemperature
            thermalHudText.text = "SoC: $tempC°C"

            if (tempC >= 45) {
                thermalHudText.setTextColor(Color.RED)
                if (!isAlertShown) {
                    isAlertShown = true
                    Toast.makeText(this, "CRITICAL THERMAL: Подключите Flydigi BS2", Toast.LENGTH_LONG).show()
                }
            } else if (tempC >= 40) {
                thermalHudText.setTextColor(Color.YELLOW)
                isAlertShown = false
            } else {
                thermalHudText.setTextColor(Color.GREEN)
                isAlertShown = false
            }
        } catch (e: Exception) {
            Log.e("CameraActivity", "Failed to update thermal HUD", e)
        }
    }

    private fun triggerRawBurst() {
        if (rootCameraService == null) {
            Log.e("CameraActivity", "Cannot capture, RootService not bound")
            Toast.makeText(this, "Koshcam AI Processing...", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val result = rootCameraService!!.captureBurst(15)
            Log.i("CameraActivity", "Koshcam Burst Capture Result: $result")
        } catch (e: Exception) {
            Log.e("CameraActivity", "IPC Transaction Failed: Burst Capture", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun openCamera() {
        startBackgroundThread()
        try {
            var targetCameraId = "0"
            for (id in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                    targetCameraId = id
                    break
                }
            }

            cameraManager.openCamera(targetCameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    Log.i("CameraActivity", "Camera $targetCameraId opened")
                }
                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                }
                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    cameraDevice = null
                    Log.e("CameraActivity", "Camera error: $error")
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            Log.e("CameraActivity", "Failed to open camera", e)
        }
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also {
            it.start()
            backgroundHandler = Handler(it.looper)
        }
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e("CameraActivity", "Error stopping background thread", e)
        }
    }

    override fun onResume() {
        super.onResume()
        startBackgroundThread()
        if (surfaceView.holder.surface.isValid) {
            openCamera()
        }
    }

    override fun onPause() {
        thermalHandler.removeCallbacks(thermalRunnable)
        cameraDevice?.close()
        cameraDevice = null
        stopBackgroundThread()
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        thermalHandler.removeCallbacks(thermalRunnable)
        if (isRootServiceBound) {
            RootService.unbind(this)
            isRootServiceBound = false
        }
    }
}
