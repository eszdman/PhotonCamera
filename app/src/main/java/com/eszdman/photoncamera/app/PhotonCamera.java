package com.eszdman.photoncamera.app;

import android.app.Application;
import android.content.Context;
import android.hardware.SensorManager;
import com.eszdman.photoncamera.Control.Gravity;
import com.eszdman.photoncamera.Control.Sensors;
import com.eszdman.photoncamera.Control.Swipe;
import com.eszdman.photoncamera.Control.TouchFocus;
import com.eszdman.photoncamera.ImageProcessing;
import com.eszdman.photoncamera.Render.Parameters;
import com.eszdman.photoncamera.api.CameraFragment;
import com.eszdman.photoncamera.api.LifeCycleMonitor;
import com.eszdman.photoncamera.api.Photo;
import com.eszdman.photoncamera.api.Settings;
import com.eszdman.photoncamera.settings.SettingsManager;
import com.eszdman.photoncamera.ui.CameraUI;
import com.eszdman.photoncamera.ui.MainActivity;
import com.eszdman.photoncamera.ui.SettingsActivity;
import com.manual.ManualMode;

public class PhotonCamera extends Application {
    private static PhotonCamera sPhotonCamera;
    private MainActivity mainActivity;
    private Settings settings;
    private Photo photo;
    private ImageProcessing imageProcessing;
    private Swipe swipe;
    private Gravity gravity;
    private Sensors sensors;
    private Parameters parameters;
    private TouchFocus touchFocus;
    private CameraUI cameraUI;
    private CameraFragment cameraFragment;
    private ManualMode manualMode;
    private SettingsActivity settingsActivity;
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

    public static ImageProcessing getImageProcessing() {
        return sPhotonCamera.imageProcessing;
    }

    public static Photo getPhoto() {
        return sPhotonCamera.photo;
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

    public static CameraUI getCameraUI() {
        return sPhotonCamera.cameraUI;
    }

    public static CameraFragment getCameraFragment() {
        return sPhotonCamera.cameraFragment;
    }

    public static void setCameraFragment(CameraFragment cameraFragment) {
        sPhotonCamera.cameraFragment = cameraFragment;
    }

    /**
     * Not required anymore, replaced by {@link com.eszdman.photoncamera.ui.SettingsActivity2}
     */
    @Deprecated
    public static SettingsActivity getSettingsActivity() {
        return sPhotonCamera.settingsActivity;
    }

    @Deprecated
    public static void setSettingsActivity(SettingsActivity settingsActivity) {
        sPhotonCamera.settingsActivity = settingsActivity;
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
        registerActivityLifecycleCallbacks(new LifeCycleMonitor());
        sPhotonCamera = this;

        SensorManager sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        gravity = new Gravity(sensorManager);
        sensors = new Sensors(sensorManager);

        settingsManager = new SettingsManager(this);
        settings = new Settings();
        photo = new Photo();
        imageProcessing = new ImageProcessing();
        swipe = new Swipe();
        touchFocus = new TouchFocus();
        parameters = new Parameters();
        cameraUI = new CameraUI();
    }

    //  a MemoryInfo object for the device's current memory status.
    /*public ActivityManager.MemoryInfo AvailableMemory() {
        ActivityManager activityManager = (ActivityManager) mainActivity.SystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);
        return memoryInfo;
    }*/
}
