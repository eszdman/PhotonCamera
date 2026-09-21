package com.particlesdevs.photoncamera.app

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
import android.os.Parcel
import android.util.Log
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.particlesdevs.photoncamera.root.IRootCameraService
import com.particlesdevs.photoncamera.root.RootCameraService
import com.topjohnwu.superuser.ipc.RootService

class CameraActivity : AppCompatActivity(), ServiceConnection {

    private lateinit var cameraManager: CameraManager
    private var cameraDevice: CameraDevice? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private lateinit var surfaceView: SurfaceView
    private lateinit var shutterButton: Button

    // IPC переменные
    private var rootCameraService: IRootCameraService? = null
    private var isRootServiceBound = false

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

        // Redesigned UI for Samsung A22 5G: Modern Glassmorphism look
        val overlayContainer = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#44000000")) // Semi-transparent glass effect
        }
        rootLayout.addView(overlayContainer, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        shutterButton = Button(this).apply {
            text = "AI RAW"
            textSize = 18f
            setTextColor(Color.WHITE)
            // Use a sleek rounded background
            val shape = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(Color.parseColor("#CCFF3D00")) // Deep orange with transparency
                setStroke(4, Color.WHITE)
            }
            background = shape
            setOnClickListener { triggerRawBurst() }
        }

        // Optimized for the A22's tall 20:9 screen and right-handed use
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
        Log.i("CameraActivity", "RootService bound successfully")
        configureHalForMediaTek()
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        rootCameraService = null
        isRootServiceBound = false
        Log.e("CameraActivity", "RootService disconnected")
    }

    private fun configureHalForMediaTek() {
        if (rootCameraService == null) return

        // Низкоуровневая конфигурация под Dimensity ISP (RAW10 / Bypass)
        val configString = "mtk_hal_raw_bypass=1;mode=raw_sensor;iso=100;exp=1/50"

        try {
            val result = rootCameraService!!.configureHal(configString)
            Log.i("CameraActivity", "HAL Config Result: $result")
        } catch (e: Exception) {
            Log.e("CameraActivity", "IPC Transaction Failed: HAL Config", e)
        }
    }

    private fun triggerRawBurst() {
        if (rootCameraService == null) {
            Log.e("CameraActivity", "Cannot capture, RootService not bound")
            return
        }

        try {
            // Запрашиваем 15 RAW кадров для обработки в NCNN
            val result = rootCameraService!!.captureBurst(15)
            Log.i("CameraActivity", "Burst Capture Result: $result")
            
            // Simulation of pipeline execution after burst capture
            // In a real app, the RootService would notify when data is ready
        } catch (e: Exception) {
            Log.e("CameraActivity", "IPC Transaction Failed: Burst Capture", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun openCamera() {
        startBackgroundThread()
        try {
            // Агрессивный перебор камер для поиска основного сенсора
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
        cameraDevice?.close()
        cameraDevice = null
        stopBackgroundThread()
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isRootServiceBound) {
            RootService.unbind(this)
            isRootServiceBound = false
        }
    }
}