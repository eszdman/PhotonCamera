package com.eszdman.photoncamera.api;

import android.app.ActivityManager;
import android.renderscript.RenderScript;
import com.eszdman.photoncamera.Control.Gravity;
import com.eszdman.photoncamera.Control.Manual;
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
    public MainActivity mainActivity;
    public CameraFragment camera;
    public Settings settings;
    public Photo photo;
    public Wrapper wrapper;
    public ImageProcessing processing;
    public Swipe swipedetection;
    public Gravity gravity;
    public Manual manual;
    public RenderScript rs;
    public Parameters parameters;
    public TouchFocus touchFocus;
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
    }
    // Get a MemoryInfo object for the device's current memory status.
    public ActivityManager.MemoryInfo getAvailableMemory() {
        ActivityManager activityManager = (ActivityManager) mainActivity.getSystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);
        return memoryInfo;
    }
}
