package com.particlesdevs.photoncamera.util;

import android.graphics.Canvas;
import android.graphics.Paint;

public class graphDrawerClass {
    private drawGraphStrategy strategy;

    public void setStrategy(drawGraphStrategy strategy) {
        this.strategy = strategy;
    }

    public void drawGraph(float[] data, int width, int height, float max, Paint paint, Canvas canvas) {
        strategy.drawGraph(data, width, height, max, paint, canvas);
    }
}
