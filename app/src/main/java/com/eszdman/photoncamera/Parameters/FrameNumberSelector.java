package com.eszdman.photoncamera.Parameters;

import com.eszdman.photoncamera.api.Interface;


public class FrameNumberSelector {
    public static int frameCount;

    public static void getFrames() {
        double output = (Math.exp(1.3595 + 0.0020 * ExposureIndex.index())) / 27;
        output *= Interface.i.settings.frameCount;
        frameCount = Math.min(Math.max((int) output, 2), Interface.i.settings.frameCount);
    }
}
