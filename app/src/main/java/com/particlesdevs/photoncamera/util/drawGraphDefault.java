package com.particlesdevs.photoncamera.util;

import android.graphics.*;

public class drawGraphDefault implements drawGraphStrategy {
    @Override
    public void drawGraph(float[] data, int width, int height, float max, Paint paint, Canvas canvas) {
        float xInterval = ((float) width / ((float) data.length + 1));
        Path path = new Path();
        paint.setARGB(255, 255, 255, 255);
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
