package com.eszdman.photoncamera.processing;

import android.graphics.Point;
import android.hardware.camera2.CaptureResult;

import com.eszdman.photoncamera.app.PhotonCamera;

import java.nio.ByteBuffer;

public class ImageBufferUtils {
    public static void RemoveHotpixelsRaw(ByteBuffer in, Point size, CaptureResult res){
        Point[] hotpixels = res.get(CaptureResult.STATISTICS_HOT_PIXEL_MAP);
        for(Point hotpixel: hotpixels){
            int ind = size.x*hotpixel.y + hotpixel.x;
            in.asShortBuffer().put(ind, (short) 1024);
        }
        /*for(int h = 30; h<90;h++) {
            for(int w =30; w<90;w++) {
                int ind = size.x*h + w;
                in.asShortBuffer().put(ind, (short) 1024);
            }
        }*/
    }
}
