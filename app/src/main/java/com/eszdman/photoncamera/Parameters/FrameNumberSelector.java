package com.eszdman.photoncamera.Parameters;

import com.eszdman.photoncamera.api.Interface;
import com.eszdman.photoncamera.api.Settings;


public class FrameNumberSelector {
    public static int frameCount;

    public static void getFrames() {
        double output = (Math.exp(1.3595 + 0.0020 * ExposureIndex.index())) / 17;
        output *= Interface.i.settings.frameCount;
        frameCount = Math.min(Math.max((int) output, 4), Interface.i.settings.frameCount);
        if(Interface.i.settings.selectedMode == Settings.CameraMode.UNLIMITED) frameCount = -1;
    }
}
