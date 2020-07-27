package com.eszdman.photoncamera.OpenGL.Scripts;

import android.graphics.Point;
import android.hardware.camera2.CaptureResult;

import java.nio.ByteBuffer;

public class RawParams {
    public float sensivity;
    public Point hotMapSize;
    public float oldwhitelevel;
    public short[] hotPixelMap;
    public ByteBuffer input;
    public RawParams(CaptureResult result){
        Point[] hotpixs =  result.get(CaptureResult.STATISTICS_HOT_PIXEL_MAP);
        if(hotpixs == null) {
            hotMapSize = new Point(1,1);
            hotPixelMap = new short[1];
        } else {

        }
    }
}
