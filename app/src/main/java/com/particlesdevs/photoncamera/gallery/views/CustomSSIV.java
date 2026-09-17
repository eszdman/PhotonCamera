package com.particlesdevs.photoncamera.gallery.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewParent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;

import static com.particlesdevs.photoncamera.gallery.helper.Constants.DOUBLE_TAP_ZOOM_DURATION_MS;

public class CustomSSIV extends SubsamplingScaleImageView {

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
        // Keep 160 dpi baseline (was 200) — smaller tiles => lower peak Graphics memory, always on to keep 4GB baseline low. Full-res on zoom still.
        setMinimumTileDpi(160);
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

