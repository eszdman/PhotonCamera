package com.particlesdevs.photoncamera.util;

import android.graphics.*;

public class drawGraphRGB implements drawGraphStrategy {
    private int r, g, b;

    public drawGraphRGB(int r, int g, int b) {
        this.r = r;
        this.g = g;
        this.b = b;
    }

    @Override
    public void drawGraph(float[] data, int width, int height, float max, Paint paint, Canvas canvas) {
        float xInterval = ((float) width / ((float) data.length + 1));
        Path path = new Path();
        paint.setARGB(255, r, g, b);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_OVER));
        paint.setStyle(Paint.Style.FILL);
        path.reset();
        path.moveTo(0, height);
        for (int j = 0; j < data.length; j++) {
            float value = ((data[j]) * ((float) (height) / max));
            path.lineTo(j * xInterval, height - value);
        }
        path.lineTo(data.length * xInterval, height);
        canvas.drawPath(path, paint);
    }
}