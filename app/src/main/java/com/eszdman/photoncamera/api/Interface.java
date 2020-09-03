package com.eszdman.photoncamera.api;

import com.eszdman.photoncamera.Control.Gravity;
import com.eszdman.photoncamera.Control.Manual;
import com.eszdman.photoncamera.Control.Sensors;
import com.eszdman.photoncamera.Control.Swipe;
import com.eszdman.photoncamera.Control.TouchFocus;
import com.eszdman.photoncamera.ImageProcessing;
import com.eszdman.photoncamera.Render.Parameters;
import com.eszdman.photoncamera.Wrapper;
import com.eszdman.photoncamera.ui.CameraUI;
import com.eszdman.photoncamera.ui.MainActivity;
import com.eszdman.photoncamera.ui.SettingsActivity;

public class Interface {
    private static Interface sInterface;
    private final MainActivity mainActivity;
    private final Settings settings;
    private final Photo photo;
    private final Wrapper wrapper;
    private final ImageProcessing imageProcessing;
    private final Swipe swipe;
    private final Gravity gravity;
    private final Sensors sensors;
    private final Parameters parameters;
    private final TouchFocus touchFocus;
    private final CameraUI cameraUI;
    private CameraFragment cameraFragment;
    private Manual manual;
    private SettingsActivity settingsActivity;

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

    public static Manual getManual() {
        return sInterface.manual;
    }

    public static void setManual(Manual manual) {
        sInterface.manual = manual;
    }

    public static SettingsActivity getSettingsActivity() {
        return sInterface.settingsActivity;
    }

    public static void setSettingsActivity(SettingsActivity settingsActivity) {
        sInterface.settingsActivity = settingsActivity;
    }

    //  a MemoryInfo object for the device's current memory status.
    /*public ActivityManager.MemoryInfo AvailableMemory() {
        ActivityManager activityManager = (ActivityManager) mainActivity.SystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.MemoryInfo(memoryInfo);
        return memoryInfo;
    }*/
}
