package com.particlesdevs.photoncamera.gallery.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.util.AttributeSet;
import com.particlesdevs.photoncamera.util.Log;
import android.view.View;

import com.particlesdevs.photoncamera.processing.opengl.GLImage;
import com.particlesdevs.photoncamera.processing.opengl.scripts.GLHistogram;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class Histogram extends View {
    private final Paint wallPaint;
    private final PorterDuffXfermode porterDuffXfermode = new PorterDuffXfermode(PorterDuff.Mode.ADD);
    private HistogramLoadingListener sHistogramLoadingListener;
    private HistogramModel histogramModel;
    private final Path wallPath = new Path();
    GLHistogram glHistogram;
    ExecutorService histogramExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "HistogramThread");
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    float reinhard_extended(float v, float max_white){
        float numerator = v * (1.0f + (v / (max_white * max_white)));
        return numerator / (1.0f + v);
    }

    public Histogram(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        wallPaint = new Paint();
        histogramExecutor.execute(() -> {
            glHistogram = new GLHistogram(256);
        });
    }
    public HistogramModel analyze(Bitmap bitmap) {
        int size = 256;
        int[][][] colorsMap = new int[1][4][size];

        //colorsMap = HistogramRs.getHistogram(bitmap);
        AtomicBoolean compute = new AtomicBoolean(false);
        histogramExecutor.execute(() -> {
            GLImage glImage = new GLImage(bitmap);
            glHistogram.Ac = false;
            colorsMap[0] = glHistogram.Compute(glImage);
            glImage.close();
            compute.set(true);
        });
        while (!compute.get()) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Log.d("Histogram", "Thread interrupted:"+Log.getStackTraceString(e));
            }
        }
        // Convert to sqrt space
        for (int i = 0; i < size; i++) {
            colorsMap[0][0][i] = (int) Math.sqrt(colorsMap[0][0][i]);
            colorsMap[0][1][i] = (int) Math.sqrt(colorsMap[0][1][i]);
            colorsMap[0][2][i] = (int) Math.sqrt(colorsMap[0][2][i]);
        }
        //Find max
        int maxY = 0;
        for (int i = 1; i < size-1; i++) {
            int m = Math.max(colorsMap[0][0][i], Math.max(colorsMap[0][1][i], colorsMap[0][2][i]));
            maxY = Math.max(maxY, m);
        }
        int m0 = Math.max(colorsMap[0][0][0], Math.max(colorsMap[0][1][0], colorsMap[0][2][0]));
        int ms = Math.max(colorsMap[0][0][size-1], Math.max(colorsMap[0][1][size-1], colorsMap[0][2][size-1]));
        if (maxY < Math.max(m0,ms)) {
            maxY = (maxY + Math.max(m0,ms))/2;
        }

        return new HistogramModel(size, colorsMap[0], maxY);
    }

    public void setHistogramLoadingListener(HistogramLoadingListener histogramLoadingListener) {
        this.sHistogramLoadingListener = histogramLoadingListener;
    }

    public void setHistogramModel(HistogramModel histogramModel) {
        this.histogramModel = histogramModel;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        //super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();

        wallPaint.setAntiAlias(true);
        wallPaint.setStyle(Paint.Style.STROKE);
        wallPaint.setARGB(100, 255, 255, 255);
        canvas.drawRect(0, 0, width, height, wallPaint);
        canvas.drawLine(width / 3.f, 0, width / 3.f, height, wallPaint);
        canvas.drawLine(2.f * width / 3.f, 0, 2.f * width / 3.f, height, wallPaint);

        if (histogramModel == null) {
            if (sHistogramLoadingListener != null) {
                sHistogramLoadingListener.isLoading(false);
            }
            return;
        }
        if (sHistogramLoadingListener != null) {
            sHistogramLoadingListener.isLoading(true);
        }

        float xInterval = ((float) getWidth() / ((float) histogramModel.getSize() + 1));
        for (int i = 0; i < 3; i++) {
            if (i == 0) {
                //wallpaint.setColor(0xFF0700);
                wallPaint.setARGB(0xFF, 0xFF, 0x07, 0x00);
            } else if (i == 1) {
                //wallpaint.setColor(0x1924B1);
                wallPaint.setARGB(0xFF, 0x00, 0xC9, 0x0D);
            } else {
                //wallpaint.setColor(0x00C90D);
                wallPaint.setARGB(0xFF, 0x19, 0x24, 0xB1);
            }
            wallPaint.setXfermode(porterDuffXfermode);
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
        if (sHistogramLoadingListener != null) {
            sHistogramLoadingListener.isLoading(false);
        }
    }

    public interface HistogramLoadingListener {
        void isLoading(boolean loading);
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
