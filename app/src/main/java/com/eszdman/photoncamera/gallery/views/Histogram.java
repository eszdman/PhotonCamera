package com.eszdman.photoncamera.gallery.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;

import com.aparapi.Kernel;
import com.aparapi.Range;
import com.eszdman.photoncamera.processing.opengl.scripts.RawSensivity;

public class Histogram extends View {
    private final Paint wallPaint;
    private HistogramModel histogramModel;

    public Histogram(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        wallPaint = new Paint();
    }

    public static HistogramModel analyze(Bitmap bitmap) {
        int size = 256;
        int[][] colorsMap = new int[3][size];
        int maxY = 0;
        int[] imgarr = new int[bitmap.getWidth()*bitmap.getHeight()];
        bitmap.getPixels(imgarr,0,bitmap.getWidth(),0,0,bitmap.getWidth(),bitmap.getHeight());
        new Kernel() {
            @Override
            public void run() {
                int w = getGlobalId(0);
                int h = getGlobalId(1);
                int rgba = imgarr[w + h*bitmap.getWidth()];
                colorsMap[0][((rgba) & 0xff)]++;
                colorsMap[1][((rgba >> 8) & 0xff)]++;
                colorsMap[2][((rgba >> 16) & 0xff)]++;
            }
        }.execute(Range.create2D(bitmap.getWidth(),bitmap.getHeight()));
        //Find max
        for (int i = 0; i < size; i++) {
            maxY = Math.max(maxY, colorsMap[0][i]);
            maxY = Math.max(maxY, colorsMap[1][i]);
            maxY = Math.max(maxY, colorsMap[2][i]);
        }
        new Kernel() {
            @Override
            public void run() {
                int w = getGlobalId(0);
                int h = getGlobalId(1);
                if(w-1 >= 0 && w+1 < size)
                colorsMap[h][w] = (colorsMap[h][w]+colorsMap[h][w-1]+colorsMap[h][w+1])/3;
            }
        }.execute(Range.create2D(size,3));
//        bitmap.recycle();
        return new HistogramModel(size, colorsMap, maxY);
    }

    public void setHistogramModel(HistogramModel histogramModel) {
        this.histogramModel = histogramModel;
        invalidate();
    }

    @SuppressLint("DrawAllocation")
    @Override
    protected void onDraw(Canvas canvas) {
        //super.onDraw(canvas);
        if (histogramModel == null)
            return;
        int width = getWidth();
        int height = getHeight();
        float xInterval = ((float) getWidth() / ((float) histogramModel.getSize() + 1));
        wallPaint.setAntiAlias(true);
        wallPaint.setStyle(Paint.Style.STROKE);
        wallPaint.setARGB(100, 255, 255, 255);
        canvas.drawRect(0, 0, width, height, wallPaint);
        canvas.drawLine(width / 3.f, 0, width / 3.f, height, wallPaint);
        canvas.drawLine(2.f * width / 3.f, 0, 2.f * width / 3.f, height, wallPaint);
        Path wallPath = new Path();
        for (int i = 0; i < 3; i++) {
            if (i == 0) {
                //wallpaint.setColor(0xFF0700);
                wallPaint.setARGB(0xFF, 0xFF, 0x07, 0x00);
            } else if (i == 1) {
                //wallpaint.setColor(0x1924B1);
                wallPaint.setARGB(0xFF, 0x19, 0x24, 0xB1);
            } else {
                //wallpaint.setColor(0x00C90D);
                wallPaint.setARGB(0xFF, 0x00, 0xC9, 0x0D);
            }
            wallPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));
            wallPaint.setStyle(Paint.Style.FILL);
            wallPath.reset();
            wallPath.moveTo(0, height);
            for (int j = 0; j < histogramModel.getSize(); j++) {
                float value = (((float) histogramModel.getColorsMap()[i][j]) * ((float) (height) / histogramModel.getMaxY()));
                wallPath.lineTo(j * xInterval, height - value);
            }
            wallPath.lineTo(histogramModel.getSize() * xInterval, height);
            //wallPath.lineTo(histogramModel.getSize() * offset, height);
            canvas.drawPath(wallPath, wallPaint);
        }

    }

    /**
     * Simple data class that stores the data required to draw histogram
     */
    public static class HistogramModel {
        private final int size;
        private final int maxY;
        private final int[][] colorsMap;

        public HistogramModel(int size, int[][] colorsMap, int maxY) {
            this.size = size;
            this.maxY = maxY;
            this.colorsMap = colorsMap;
        }

        public int getSize() {
            return size;
        }

        public int getMaxY() {
            return maxY;
        }

        public int[][] getColorsMap() {
            return colorsMap;
        }

    }

}
