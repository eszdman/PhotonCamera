package com.eszdman.photoncamera.processing.opengl.scripts;

import android.graphics.Point;
import android.hardware.camera2.CaptureResult;

import java.nio.ByteBuffer;

public class RawParams {
    public float sensitivity;
    public Point hotMapSize;
    public float oldWhiteLevel;
    public short[] hotPixelMap;
    public ByteBuffer input;

    public RawParams(CaptureResult result) {
        Point[] hotPixs = result.get(CaptureResult.STATISTICS_HOT_PIXEL_MAP);
        if (hotPixs == null) {
            hotMapSize = new Point(1, 1);
            hotPixelMap = new short[1];
        }
    }
}
