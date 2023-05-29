package com.particlesdevs.photoncamera.util;

import android.graphics.Canvas;
import android.graphics.Paint;

public interface drawGraphStrategy {
    void drawGraph(float[] data, int width, int height, float max, Paint paint, Canvas canvas);
}