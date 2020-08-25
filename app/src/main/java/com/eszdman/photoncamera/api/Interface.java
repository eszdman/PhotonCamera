package com.eszdman.photoncamera.api;

import android.app.ActivityManager;

import com.eszdman.photoncamera.Control.Gravity;
import com.eszdman.photoncamera.Control.Manual;
import com.eszdman.photoncamera.Control.Sensors;
import com.eszdman.photoncamera.Control.Swipe;
import com.eszdman.photoncamera.Control.TouchFocus;
import com.eszdman.photoncamera.ImageProcessing;
import com.eszdman.photoncamera.Render.Parameters;
import com.eszdman.photoncamera.Wrapper;
import com.eszdman.photoncamera.ui.CameraFragment;
import com.eszdman.photoncamera.ui.MainActivity;

import static android.content.Context.ACTIVITY_SERVICE;

public class Interface {
    public static Interface i;
    public final MainActivity mainActivity;
    public CameraFragment camera;
    public final Settings settings;
    public final Photo photo;
    public final Wrapper wrapper;
    public final ImageProcessing processing;
    public final Swipe swipedetection;
    public final Gravity gravity;
    public final Sensors sensors;
    public Manual manual;
    public final Parameters parameters;
    public final TouchFocus touchFocus;
    public final CameraUI cameraui;
    public Interface(MainActivity act) {
        i = this;
        mainActivity = act;
        gravity = new Gravity();
        settings = new Settings();
        photo = new Photo();
        wrapper = new Wrapper();
        processing = new ImageProcessing();
        swipedetection = new Swipe();
        touchFocus = new TouchFocus();
        parameters = new Parameters();
        sensors = new Sensors();
        cameraui = new CameraUI();
    }
    // Get a MemoryInfo object for the device's current memory status.
    /*public ActivityManager.MemoryInfo getAvailableMemory() {
        ActivityManager activityManager = (ActivityManager) mainActivity.getSystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);
        return memoryInfo;
    }*/
}
