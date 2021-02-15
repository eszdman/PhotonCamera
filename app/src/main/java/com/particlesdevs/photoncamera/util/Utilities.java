package com.particlesdevs.photoncamera.util;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;

import com.particlesdevs.photoncamera.processing.opengl.postpipeline.PostPipeline;

import java.util.Locale;

public class Utilities {
    private static final PorterDuffXfermode porterDuffXfermode = new PorterDuffXfermode(PorterDuff.Mode.ADD);
    public static void drawArray(float[] input, Bitmap output){
        float max = 0.f;
        for(float ccur : input){
            if(ccur > max) max = ccur;
        }
        int width = output.getWidth();
        int height = output.getHeight();
        Canvas canvas = new Canvas(output);
        Paint wallPaint = new Paint();
        wallPaint.setAntiAlias(true);
        wallPaint.setStyle(Paint.Style.STROKE);
        wallPaint.setARGB(100, 255, 255, 255);
        canvas.drawRect(0, 0, width, height, wallPaint);
        canvas.drawLine(width / 3.f, 0, width / 3.f, height, wallPaint);
        canvas.drawLine(2.f * width / 3.f, 0, 2.f * width / 3.f, height, wallPaint);
        float xInterval = ((float) width / ((float) input.length + 1));
        Path wallPath = new Path();
        wallPaint.setARGB(255, 255, 255, 255);
        wallPaint.setXfermode(porterDuffXfermode);
        wallPaint.setStyle(Paint.Style.FILL);
        wallPath.reset();
        wallPath.moveTo(0, height);
        for (int j = 0; j < input.length; j++) {
            float value = (((float) input[j]) * ((float) (height) / max));
            wallPath.lineTo(j * xInterval, height - value);
        }
        wallPath.lineTo(input.length * xInterval, height);
        canvas.drawPath(wallPath, wallPaint);
    }
    public static String formatExposureTime(final double value) {
        String output;

        if (value < 1.0f) {
            output = String.format(Locale.getDefault(), "%d/%d", 1, (int) (0.5f + 1 / value));
        } else {
            final int integer = (int) value;
            final double time = value - integer;
            output = String.format(Locale.getDefault(), "%d''", integer);

            if (time > 0.0001f) {
                output += String.format(Locale.getDefault(), " %d/%d", 1, (int) (0.5f + 1 / time));
            }
        }

        return output;
    }
}
