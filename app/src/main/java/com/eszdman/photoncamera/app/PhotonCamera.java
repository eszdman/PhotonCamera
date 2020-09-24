package com.eszdman.photoncamera.app;

import android.app.Application;
import android.content.Context;
import android.hardware.SensorManager;
import com.eszdman.photoncamera.Control.Gravity;
import com.eszdman.photoncamera.Control.Sensors;
import com.eszdman.photoncamera.Control.Swipe;
import com.eszdman.photoncamera.Control.TouchFocus;
import com.eszdman.photoncamera.Render.Parameters;
import com.eszdman.photoncamera.api.CameraFragment;
import com.eszdman.photoncamera.api.Settings;
import com.eszdman.photoncamera.log.ActivityLifecycleMonitor;
import com.eszdman.photoncamera.settings.SettingsManager;
import com.eszdman.photoncamera.ui.MainActivity;
import com.manual.ManualMode;

public class PhotonCamera extends Application {
    public static final boolean DEBUG = false;
    private static PhotonCamera sPhotonCamera;
    private MainActivity mainActivity;
    private Settings settings;
    private Swipe swipe;
    private Gravity gravity;
    private Sensors sensors;
    private Parameters parameters;
    private TouchFocus touchFocus;
    private CameraFragment cameraFragment;
    private ManualMode manualMode;
    private SettingsManager settingsManager;

    public static MainActivity getMainActivity() {
        return sPhotonCamera.mainActivity;
    }

    public static void setMainActivity(MainActivity mainActivity) {
        sPhotonCamera.mainActivity = mainActivity;
    }

    public static Settings getSettings() {
        return sPhotonCamera.settings;
    }

    public static Swipe getSwipe() {
        return sPhotonCamera.swipe;
    }

    public static Gravity getGravity() {
        return sPhotonCamera.gravity;
    }

    public static Sensors getSensors() {
        return sPhotonCamera.sensors;
    }

    public static Parameters getParameters() {
        return sPhotonCamera.parameters;
    }

    public static TouchFocus getTouchFocus() {
        return sPhotonCamera.touchFocus;
    }

    public static CameraFragment getCameraFragment() {
        return sPhotonCamera.cameraFragment;
    }

    public static void setCameraFragment(CameraFragment cameraFragment) {
        sPhotonCamera.cameraFragment = cameraFragment;
    }

    public static ManualMode getManualMode() {
        return sPhotonCamera.manualMode;
    }

    public static void setManualMode(ManualMode manualMode) {
        sPhotonCamera.manualMode = manualMode;
    }

    public static SettingsManager getSettingsManager() {
        return sPhotonCamera.settingsManager;
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
        gravity = new Gravity(sensorManager);
        sensors = new Sensors(sensorManager);

        swipe = new Swipe();
        touchFocus = new TouchFocus();

        settingsManager = new SettingsManager(this);
        settings = new Settings();

        parameters = new Parameters();
    }


    //  a MemoryInfo object for the device's current memory status.
    /*public ActivityManager.MemoryInfo AvailableMemory() {
        ActivityManager activityManager = (ActivityManager) mainActivity.SystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);
        return memoryInfo;
    }*/
}
