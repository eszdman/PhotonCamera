package com.eszdman.photoncamera.Parameters;

import android.hardware.camera2.CaptureRequest;

import com.eszdman.photoncamera.Settings;


public class FrameNumberSelector {
    public static int framecount;
    public static void getFrames(){
        double output = (Math.exp(1.3595 + 0.0020*ExposureIndex.index()))/20;
        output*= Settings.instance.framecount;
        framecount = Math.min(Math.max((int)output,2),Settings.instance.framecount);
    }
}
