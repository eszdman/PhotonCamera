package com.eszdman.photoncamera.api;

import android.app.Application;
import com.eszdman.photoncamera.Control.Gravity;
import com.eszdman.photoncamera.Control.Sensors;
import com.eszdman.photoncamera.Control.Swipe;
import com.eszdman.photoncamera.Control.TouchFocus;
import com.eszdman.photoncamera.ImageProcessing;
import com.eszdman.photoncamera.Render.Parameters;
import com.eszdman.photoncamera.Wrapper;
import com.eszdman.photoncamera.settings.SettingsManager;
import com.eszdman.photoncamera.ui.CameraUI;
import com.eszdman.photoncamera.ui.MainActivity;
import com.eszdman.photoncamera.ui.SettingsActivity;
import com.manual.ManualMode;

public class Interface extends Application {
    private static Interface sInterface;
    private MainActivity mainActivity;
    private Settings settings;
    private Photo photo;
    private Wrapper wrapper;
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

    public Interface() {

    }

    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new LifeCycleMonitor());
    }

    public Interface(MainActivity act) {
        sInterface = this;
        mainActivity = act;
        gravity = new Gravity();
        settings = new Settings();
        photo = new Photo();
        wrapper = new Wrapper();
        imageProcessing = new ImageProcessing();
        swipe = new Swipe();
        touchFocus = new TouchFocus();
        parameters = new Parameters();
        sensors = new Sensors();
        cameraUI = new CameraUI();
        manualMode = ManualMode.getInstance(act);
        settingsManager = new SettingsManager(act);

    }

    public static MainActivity getMainActivity() {
        return sInterface.mainActivity;
    }

    public static Settings getSettings() {
        return sInterface.settings;
    }

    public static Photo getPhoto() {
        return sInterface.photo;
    }

    public static Wrapper getWrapper() {
        return sInterface.wrapper;
    }

    public static ImageProcessing getImageProcessing() {
        return sInterface.imageProcessing;
    }

    public static Swipe getSwipe() {
        return sInterface.swipe;
    }

    public static Gravity getGravity() {
        return sInterface.gravity;
    }

    public static Sensors getSensors() {
        return sInterface.sensors;
    }

    public static Parameters getParameters() {
        return sInterface.parameters;
    }

    public static TouchFocus getTouchFocus() {
        return sInterface.touchFocus;
    }

    public static CameraUI getCameraUI() {
        return sInterface.cameraUI;
    }

    public static CameraFragment getCameraFragment() {
        return sInterface.cameraFragment;
    }

    public static void setCameraFragment(CameraFragment cameraFragment) {
        sInterface.cameraFragment = cameraFragment;
    }

    public static SettingsActivity getSettingsActivity() {
        return sInterface.settingsActivity;
    }

    public static void setSettingsActivity(SettingsActivity settingsActivity) {
        sInterface.settingsActivity = settingsActivity;
    }

    public static ManualMode getManualMode() {
        return sInterface.manualMode;
    }

    public static SettingsManager getSettingsManager() {
        return sInterface.settingsManager;
    }

    //  a MemoryInfo object for the device's current memory status.
    /*public ActivityManager.MemoryInfo AvailableMemory() {
        ActivityManager activityManager = (ActivityManager) mainActivity.SystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);
        return memoryInfo;
    }*/
}
