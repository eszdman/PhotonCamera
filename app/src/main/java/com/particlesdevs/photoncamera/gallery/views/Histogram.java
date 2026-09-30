package com.particlesdevs.photoncamera.gallery.views;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageDecoder;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import com.particlesdevs.photoncamera.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.particlesdevs.photoncamera.processing.opengl.GLImage;
import com.particlesdevs.photoncamera.processing.opengl.scripts.GLHistogram;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Gallery histogram view and computer.
 *
 * <p>The analysis runs on one long-lived worker thread that owns a single GL
 * context and a single {@link GLHistogram} for the whole gallery session, and
 * the decoded model is delivered on the main thread. Reusing one context keeps
 * the app's EGL slot count flat and removes the per-image GL setup; nothing is
 * ever computed on the UI thread.
 */
public class Histogram extends View {
    private static final String TAG = "Histogram";
    /** Bins of the histogram (matches the GL shader's HISTSIZE). */
    private static final int HISTOGRAM_SIZE = 256;
    /**
     * Longest side the histogram source is decoded to. 256 bins over a 320 px
     * source is statistically identical to the old 800 px decode for a 100 MP
     * file, at a fraction of the decode cost.
     */
    private static final int SOURCE_SIZE = 320;

    private final Paint wallPaint;
    private final PorterDuffXfermode porterDuffXfermode = new PorterDuffXfermode(PorterDuff.Mode.ADD);
    private HistogramLoadingListener sHistogramLoadingListener;
    private HistogramModel histogramModel;
    private final Path wallPath = new Path();
    /** Owned by the worker thread once created; null while idle/unused. */
    GLHistogram glHistogram;
    private ExecutorService histogramExecutor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean closed;

    float reinhard_extended(float v, float max_white){
        float numerator = v * (1.0f + (v / (max_white * max_white)));
        return numerator / (1.0f + v);
    }

    public Histogram(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        wallPaint = new Paint();
    }

    /** Delivered on the main thread for a completed analysis. */
    public interface HistogramResultListener {
        void onHistogramReady(@NonNull HistogramModel model);
    }

    /**
     * Decodes a small source for {@code uri} and computes its histogram on the
     * dedicated worker, then delivers the model on the main thread. Safe to
     * call repeatedly (the GL context and buffers are reused); the worker's
     * queue preserves the order in which requests were made.
     */
    public void analyzeAsync(@NonNull ContentResolver resolver, @NonNull Uri uri,
                             @NonNull HistogramResultListener listener) {
        final ExecutorService executor;
        synchronized (this) {
            if (closed) return;
            if (histogramExecutor == null || histogramExecutor.isShutdown()) {
                histogramExecutor = Executors.newSingleThreadExecutor(r -> {
                    Thread thread = new Thread(r, "HistogramThread");
                    thread.setPriority(Thread.MIN_PRIORITY);
                    return thread;
                });
            }
            executor = histogramExecutor;
        }
        executor.execute(() -> {
            Bitmap source = null;
            try {
                source = decodeSource(resolver, uri, SOURCE_SIZE);
                if (source == null) return;
                if (glHistogram == null) glHistogram = new GLHistogram(HISTOGRAM_SIZE);
                glHistogram.Ac = false;
                HistogramModel model = buildModel(glHistogram.Compute(new GLImage(source)));
                if (model == null) return;
                mainHandler.post(() -> {
                    if (!closed) listener.onHistogramReady(model);
                });
            } catch (Throwable t) {
                Log.d(TAG, "Histogram computation failed " + Log.getStackTraceString(t));
            } finally {
                if (source != null && !source.isRecycled()) {
                    try {
                        source.recycle();
                    } catch (Throwable ignored) {
                    }
                }
            }
        });
    }

    /** Stops the worker and releases its GL context. Idempotent. */
    public void close() {
        final ExecutorService executor;
        synchronized (this) {
            if (closed) return;
            closed = true;
            executor = histogramExecutor;
            histogramExecutor = null;
        }
        if (executor == null) return;
        try {
            // Destroy the GL objects on the thread that created them.
            executor.execute(() -> {
                GLHistogram histogram = glHistogram;
                glHistogram = null;
                if (histogram != null) {
                    try {
                        histogram.close();
                    } catch (Throwable ignored) {
                    }
                }
            });
        } catch (Throwable ignored) {
        }
        executor.shutdown();
    }

    @Nullable
    private Bitmap decodeSource(ContentResolver resolver, Uri uri, int maxSize) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                ImageDecoder.Source src = ImageDecoder.createSource(resolver, uri);
                Bitmap decoded = ImageDecoder.decodeBitmap(src, (decoder, info, source) -> {
                    int w = info.getSize().getWidth();
                    int h = info.getSize().getHeight();
                    int max = Math.max(w, h);
                    if (max > maxSize) {
                        float ratio = (float) maxSize / max;
                        decoder.setTargetSize(Math.max(1, Math.round(w * ratio)),
                                Math.max(1, Math.round(h * ratio)));
                    }
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                    decoder.setUnpremultipliedRequired(false);
                });
                if (decoded != null) return decoded;
            } catch (Throwable ignored) {
                // Fall through to BitmapFactory (formats ImageDecoder rejects).
            }
        }
        try (InputStream is = resolver.openInputStream(uri)) {
            if (is == null) return null;
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(is, null, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
            int max = Math.max(bounds.outWidth, bounds.outHeight);
            int sample = 1;
            while (max / (sample * 2) >= maxSize) sample *= 2;
            try (InputStream is2 = resolver.openInputStream(uri)) {
                if (is2 == null) return null;
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = sample;
                opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
                return BitmapFactory.decodeStream(is2, null, opts);
            }
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Builds an owned model from the GL bins. The copy is required: the
     * GLHistogram reuses its output array for the next compute, so a published
     * model must not alias it.
     */
    @Nullable
    private HistogramModel buildModel(int[][] bins) {
        if (bins == null || bins.length < 3) return null;
        int[][] colorsMap = new int[4][HISTOGRAM_SIZE];
        for (int channel = 0; channel < 4; channel++) {
            int[] from = bins[channel];
            int length = from == null ? 0 : Math.min(HISTOGRAM_SIZE, from.length);
            if (length > 0) System.arraycopy(from, 0, colorsMap[channel], 0, length);
        }
        // Convert to sqrt space
        for (int i = 0; i < HISTOGRAM_SIZE; i++) {
            colorsMap[0][i] = (int) Math.sqrt(colorsMap[0][i]);
            colorsMap[1][i] = (int) Math.sqrt(colorsMap[1][i]);
            colorsMap[2][i] = (int) Math.sqrt(colorsMap[2][i]);
        }
        // Find max
        int maxY = 0;
        for (int i = 1; i < HISTOGRAM_SIZE - 1; i++) {
            int m = Math.max(colorsMap[0][i], Math.max(colorsMap[1][i], colorsMap[2][i]));
            maxY = Math.max(maxY, m);
        }
        int m0 = Math.max(colorsMap[0][0], Math.max(colorsMap[1][0], colorsMap[2][0]));
        int ms = Math.max(colorsMap[0][HISTOGRAM_SIZE - 1], Math.max(colorsMap[1][HISTOGRAM_SIZE - 1], colorsMap[2][HISTOGRAM_SIZE - 1]));
        if (maxY < Math.max(m0, ms)) {
            maxY = (maxY + Math.max(m0, ms)) / 2;
        }
        return new HistogramModel(HISTOGRAM_SIZE, colorsMap, maxY);
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
