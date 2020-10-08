package com.eszdman.photoncamera.app;

import android.app.Application;
import android.content.Context;
import android.hardware.SensorManager;

import com.eszdman.photoncamera.api.Settings;
import com.eszdman.photoncamera.control.Gravity;
import com.eszdman.photoncamera.control.Sensors;
import com.eszdman.photoncamera.control.Swipe;
import com.eszdman.photoncamera.control.TouchFocus;
import com.eszdman.photoncamera.processing.render.Parameters;
import com.eszdman.photoncamera.settings.SettingsManager;
import com.eszdman.photoncamera.ui.camera.CameraActivity;
import com.eszdman.photoncamera.ui.camera.CameraFragment;
import com.eszdman.photoncamera.util.log.ActivityLifecycleMonitor;
import com.manual.ManualMode;

public class PhotonCamera extends Application {
    public static final boolean DEBUG = false;
    private static PhotonCamera sPhotonCamera;
    private CameraActivity mCameraActivity;
    private Settings mSettings;
    private Swipe mSwipe;
    private Gravity mGravity;
    private Sensors mSensors;
    private Parameters mParameters;
    private TouchFocus mTouchFocus;
    private CameraFragment mCameraFragment;
    private ManualMode mManualMode;
    private SettingsManager mSettingsManager;

    public static CameraActivity getCameraActivity() {
        return sPhotonCamera.mCameraActivity;
    }

    public static void setCameraActivity(CameraActivity cameraActivity) {
        sPhotonCamera.mCameraActivity = cameraActivity;
    }

    public static Settings getSettings() {
        return sPhotonCamera.mSettings;
    }

    public static Swipe getSwipe() {
        return sPhotonCamera.mSwipe;
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

    public static TouchFocus getTouchFocus() {
        return sPhotonCamera.mTouchFocus;
    }

    public static CameraFragment getCameraFragment() {
        return sPhotonCamera.mCameraFragment;
    }

    public static void setCameraFragment(CameraFragment cameraFragment) {
        sPhotonCamera.mCameraFragment = cameraFragment;
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

    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new ActivityLifecycleMonitor());
        sPhotonCamera = this;
        initModules();
    }

    private void initModules() {

        SensorManager sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mGravity = new Gravity(sensorManager);
        mSensors = new Sensors(sensorManager);

        mSwipe = new Swipe();
        mTouchFocus = new TouchFocus();

        mSettingsManager = new SettingsManager(this);
        mSettings = new Settings();

        mParameters = new Parameters();
    }


    //  a MemoryInfo object for the device's current memory status.
    /*public ActivityManager.MemoryInfo AvailableMemory() {
        ActivityManager activityManager = (ActivityManager) mCameraActivity.SystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);
        return memoryInfo;
    }*/
}
