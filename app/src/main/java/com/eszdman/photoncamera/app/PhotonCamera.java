package com.eszdman.photoncamera.app;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.hardware.SensorManager;
import android.os.Build;
import android.widget.Toast;
import androidx.core.content.ContextCompat;
import com.eszdman.photoncamera.api.Settings;
import com.eszdman.photoncamera.capture.CaptureController;
import com.eszdman.photoncamera.control.Gravity;
import com.eszdman.photoncamera.control.Sensors;
import com.eszdman.photoncamera.pro.SupportedDevice;
import com.eszdman.photoncamera.processing.render.Parameters;
import com.eszdman.photoncamera.settings.SettingsManager;
import com.eszdman.photoncamera.ui.SplashActivity;
import com.eszdman.photoncamera.ui.camera.CameraActivity;
import com.eszdman.photoncamera.util.log.ActivityLifecycleMonitor;
import com.manual.ManualMode;

public class PhotonCamera extends Application {
    public static final boolean DEBUG = false;
    private static PhotonCamera sPhotonCamera;
    private CameraActivity mCameraActivity;
    private Settings mSettings;
    private Gravity mGravity;
    private Sensors mSensors;
    private Parameters mParameters;
    private CaptureController mCaptureController;
    private SupportedDevice mSupportedDevice;
    private ManualMode mManualMode;
    private SettingsManager mSettingsManager;

    public static Settings getSettings() {
        return sPhotonCamera.mSettings;
    }

    public static Gravity getGravity() {
        return sPhotonCamera.mGravity;
    }

    public static Sensors getSensors() {
        return sPhotonCamera.mSensors;
    }

    public static Parameters getParameters() {
        return sPhotonCamera.mParameters;
    }

    public static CaptureController getCaptureController() {
        return sPhotonCamera.mCaptureController;
    }

    public static void setCaptureController(CaptureController captureController) {
        sPhotonCamera.mCaptureController = captureController;
    }

    public static SupportedDevice getSupportedDevice() {
        return sPhotonCamera.mSupportedDevice;
    }

    public static ManualMode getManualMode() {
        return sPhotonCamera.mManualMode;
    }

    public static void setManualMode(ManualMode manualMode) {
        sPhotonCamera.mManualMode = manualMode;
    }

    public static SettingsManager getSettingsManager() {
        return sPhotonCamera.mSettingsManager;
    }

    public static void restartApp() {
        Context context = sPhotonCamera;
        Intent intent = new Intent(context, SplashActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        context.startActivity(intent);
        System.exit(0);
    }

    public static void showToast(String msg) {
        Toast.makeText(sPhotonCamera, msg, Toast.LENGTH_SHORT).show();
    }

    public static Resources getResourcesStatic() {
        return sPhotonCamera.getResources();
    }

    public static Drawable getDrawableStatic(int resID) {
        return ContextCompat.getDrawable(sPhotonCamera, resID);
    }

    public static PackageInfo getPackageInfo() throws PackageManager.NameNotFoundException {
        return sPhotonCamera.getPackageManager().getPackageInfo(sPhotonCamera.getPackageName(), 0);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new ActivityLifecycleMonitor());
        sPhotonCamera = this;
        showToast(Build.BRAND.toLowerCase() + ":" + Build.DEVICE.toLowerCase());
        initModules();
    }

    private void initModules() {

        SensorManager sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mGravity = new Gravity(sensorManager);
        mSensors = new Sensors(sensorManager);

        mSettingsManager = new SettingsManager(this);
        mSettings = new Settings();

        mParameters = new Parameters();
        mSupportedDevice = new SupportedDevice(mSettingsManager);
    }
    //  a MemoryInfo object for the device's current memory status.
    /*public ActivityManager.MemoryInfo AvailableMemory() {
        ActivityManager activityManager = (ActivityManager) mCameraActivity.SystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);
        return memoryInfo;
    }*/
}
