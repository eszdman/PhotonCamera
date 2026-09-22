package com.particlesdevs.photoncamera.gallery.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.ViewParent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;

import static com.particlesdevs.photoncamera.gallery.helper.Constants.DOUBLE_TAP_ZOOM_DURATION_MS;

public class CustomSSIV extends SubsamplingScaleImageView {

    /**
     * Ceiling for the tile DPI. SSIV upscales tile bitmaps whenever the
     * effective scale (scaled by minimumTileDpi/displayDpi) is below the
     * screen resolution, and upscaled tiles show their per-tile filtering
     * seams. Capping at 320 keeps tiles near screen resolution on dense
     * displays while bounding tile memory.
     */
    private static final int MAX_TILE_DPI = 320;

    private TouchCallBack touchCallBack;

    public CustomSSIV(Context context) {
        super(context);
        init();
    }
    public CustomSSIV(Context context, AttributeSet attrs){
        super(context, attrs);
        init();
    }

    private void init() {
        setMinimumDpi(40);
        setOrientation(SubsamplingScaleImageView.ORIENTATION_USE_EXIF);
        setQuickScaleEnabled(true);
        setEagerLoadingEnabled(false);
        setDoubleTapZoomDuration(DOUBLE_TAP_ZOOM_DURATION_MS);
        setPreferredBitmapConfig(Bitmap.Config.ARGB_8888);
        // Tile at screen resolution instead of the old fixed 160 dpi: SSIV
        // scales the effective tile sample size by minimumTileDpi/displayDpi,
        // so 160 on a ~400 dpi panel decoded tiles at ~40% resolution and
        // upscaled them ~2.5x, which magnified the per-tile filter seams into
        // visible lines. Capped so tile memory stays bounded on dense panels.
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int averageDpi = Math.round((metrics.xdpi + metrics.ydpi) / 2f);
        setMinimumTileDpi(Math.min(MAX_TILE_DPI, Math.max(1, averageDpi)));
    }

    /**
     * Release native tile memory when the View is recycled by ViewPager2.
     * Call from Adapter.onViewRecycled.
     */
    public void recycleIfNeeded() {
        try {
            recycle();
        } catch (Exception ignored) {}
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        int action = event.getActionMasked();
        int ptrCount = event.getPointerCount();
        boolean zoomed = isReady() && getScale() > getMinScale() + 0.01f;
        // Always disallow parent intercept while pinch or zoomed pan – including MOVE
        boolean shouldLock = ptrCount > 1 || zoomed;
        if (shouldLock) {
            ViewParent p = getParent();
            while (p != null) {
                p.requestDisallowInterceptTouchEvent(true);
                p = p.getParent();
            }
        }
        boolean handled = super.onTouchEvent(event);
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_POINTER_UP) {
            postDelayed(() -> {
                boolean stillZoomed = isReady() && getScale() > getMinScale() + 0.02f;
                boolean stillPinching = getPointerCountCompat() > 1;
                if (!stillZoomed && !stillPinching) {
                    ViewParent p2 = getParent();
                    while (p2 != null) {
                        p2.requestDisallowInterceptTouchEvent(false);
                        p2 = p2.getParent();
                    }
                }
            }, 30);
        }
        if (touchCallBack != null) {
            touchCallBack.onTouched(getId());
        }
        return handled;
    }

    // Helper for postDelayed check – MotionEvent not available there, use SSIV scale only
    private int getPointerCountCompat() {
        // We cannot know pointer count after up, so conservatively return 1 if zoomed else 0
        // Actual multi-touch release is handled via isReady check above
        return 1;
    }

    /**
     * Visible source rect for region HDR decode (Option B).
     * Returns null if not ready or dimensions unknown.
     */
    @Nullable
    public Rect getVisibleFileRect() {
        if (!isReady() || getSWidth() <= 0 || getSHeight() <= 0) return null;
        PointF center = getCenter();
        if (center == null) return null;
        int vW = getWidth();
        int vH = getHeight();
        float scale = getScale();
        if (vW <= 0 || vH <= 0 || scale <= 0) return null;
        float srcW = vW / scale;
        float srcH = vH / scale;
        // 10% padding to avoid re-decode on tiny pans
        float padW = srcW * 0.1f;
        float padH = srcH * 0.1f;
        int left = (int) Math.max(0, center.x - srcW / 2 - padW);
        int top = (int) Math.max(0, center.y - srcH / 2 - padH);
        int right = (int) Math.min(getSWidth(), center.x + srcW / 2 + padW);
        int bottom = (int) Math.min(getSHeight(), center.y + srcH / 2 + padH);
        if (right <= left || bottom <= top) return null;
        // Clamp min size 200 to avoid tiny crop decode overhead
        if (right - left < 200) {
            int cx = (left + right) / 2;
            left = Math.max(0, cx - 100);
            right = Math.min(getSWidth(), left + 200);
        }
        if (bottom - top < 200) {
            int cy = (top + bottom) / 2;
            top = Math.max(0, cy - 100);
            bottom = Math.min(getSHeight(), top + 200);
        }
        return new Rect(left, top, right, bottom);
    }

    public void setTouchCallBack(TouchCallBack touchCallBack) {
        this.touchCallBack = touchCallBack;
    }

    public interface TouchCallBack {
        void onTouched(int id);
    }
}

