package com.particlesdevs.photoncamera.ui.camera.views.viewfinder;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.View;

import androidx.annotation.Nullable;

import com.particlesdevs.photoncamera.circularbarlib.util.Motion;

/**
 * Clean Leica-style Viewfinder HUD and real-time live RGB histogram overlay.
 * Renders on standard hardware-accelerated Android View pipeline with zero CPU overhead.
 */
public class ViewfinderHudView extends View {
    public static final float WAVEFORM_WIDTH_DP = 144f;
    public static final float WAVEFORM_HEIGHT_DP = 72f;
    private static final float HISTOGRAM_WIDTH_DP = 72f;
    private static final float HISTOGRAM_HEIGHT_DP = 36f;

    private final TextPaint hudPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    private final Paint strikePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint histBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint histBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint histChannelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint waveBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint waveGridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path histPath = new Path();
    private final PorterDuffXfermode histXfermode = new PorterDuffXfermode(PorterDuff.Mode.ADD);
    private final RectF scopeDst = new RectF();
    private final float[] scopeBox = new float[8];

    private float mDensity = 1.0f;
    private float screenRatio = 1.0f;
    private float oisTextWidth = 0f;

    private String mExpoText = null;
    private String mIsoText = null;
    private String mFocalText = null;
    private String mFocusText = null;
    private String mWbText = null;
    private boolean mIsTripod = false;
    private boolean mIsOisSupported = false;
    private boolean mIsOisActive = false;

    private int[][] mHistColorsMap = null;
    private int mHistMaxY = 1;
    private int mHistSize = 64;
    private Bitmap mWaveBitmap = null;

    private int mHudMode = 0; // 0 = Off, 1 = HUD, 2 = HUD + Histogram, 4 = HUD + Waveform
    private float mAnimatedOrientation = 0f;
    private int mTargetOrientation = 0;
    private ValueAnimator mRotationAnimator = null;

    public ViewfinderHudView(Context context) {
        super(context);
        init();
    }

    public ViewfinderHudView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ViewfinderHudView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        mDensity = dm.density;
        screenRatio = (float) Math.max(dm.heightPixels, dm.widthPixels) / Math.min(dm.heightPixels, dm.widthPixels);

        float fontSize = 13f * mDensity;

        hudPaint.setColor(Color.WHITE);
        hudPaint.setTextSize(fontSize);
        hudPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        hudPaint.setTextAlign(Paint.Align.LEFT);
        hudPaint.setShadowLayer(4f, 1f, 1f, Color.BLACK);

        strikePaint.setColor(Color.WHITE);
        strikePaint.setStrokeWidth(1.5f * mDensity);
        strikePaint.setStyle(Paint.Style.STROKE);
        strikePaint.setShadowLayer(4f, 1f, 1f, Color.BLACK);

        oisTextWidth = hudPaint.measureText("OIS");

        histBgPaint.setColor(Color.argb(100, 0, 0, 0));
        histBgPaint.setStyle(Paint.Style.FILL);

        histBorderPaint.setColor(Color.argb(90, 255, 255, 255));
        histBorderPaint.setStyle(Paint.Style.STROKE);
        histBorderPaint.setStrokeWidth(1.0f * mDensity);

        wavePaint.setFilterBitmap(true);

        waveBgPaint.setColor(Color.BLACK);
        waveBgPaint.setStyle(Paint.Style.FILL);

        waveGridPaint.setColor(Color.argb(70, 255, 255, 255));
        waveGridPaint.setStyle(Paint.Style.STROKE);
        waveGridPaint.setStrokeWidth(1.0f * mDensity);
    }

    public void setHudMode(int mode) {
        if (this.mHudMode != mode) {
            this.mHudMode = mode;
            if (mode == 0) {
                clear();
            } else {
                invalidate();
            }
        }
    }

    public void setOrientation(int orientation) {
        if (this.mTargetOrientation == orientation) return;
        this.mTargetOrientation = orientation;

        float startAngle = mAnimatedOrientation;
        float diff = (orientation - startAngle) % 360f;
        if (diff > 180f) diff -= 360f;
        if (diff < -180f) diff += 360f;
        float endAngle = startAngle + diff;

        if (mRotationAnimator != null && mRotationAnimator.isRunning()) {
            mRotationAnimator.cancel();
        }

        mRotationAnimator = ValueAnimator.ofFloat(startAngle, endAngle);
        mRotationAnimator.setDuration(Motion.durationMedium1(getContext()));
        mRotationAnimator.setInterpolator(Motion.emphasizedDecelerate(getContext()));
        mRotationAnimator.addUpdateListener(animation -> {
            mAnimatedOrientation = (float) animation.getAnimatedValue();
            invalidate();
        });
        mRotationAnimator.start();
    }

    public void setHudData(String expo, String iso, String focal, String focus, String wb,
                           boolean isTripod, boolean isOisSupported, boolean isOisActive) {
        this.mExpoText = expo;
        this.mIsoText = iso;
        this.mFocalText = focal;
        this.mFocusText = focus;
        this.mWbText = wb;
        this.mIsTripod = isTripod;
        this.mIsOisSupported = isOisSupported;
        this.mIsOisActive = isOisActive;
        invalidate();
    }

    public void setHistogramData(int[][] colorsMap, int maxY, int size) {
        this.mHistColorsMap = colorsMap;
        this.mHistMaxY = Math.max(1, maxY);
        this.mHistSize = size;
        if (mHudMode == 2) {
            invalidate();
        }
    }

    /**
     * Handed a completed waveform bitmap's pixels on the UI thread; copied into
     * a view-owned bitmap so the producer can reuse its buffer next tick.
     */
    public void setWaveformPixels(int[] pixels, int width, int height) {
        if (pixels == null || width <= 0 || height <= 0) return;
        if (mWaveBitmap == null || mWaveBitmap.getWidth() != width
                || mWaveBitmap.getHeight() != height) {
            mWaveBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        }
        mWaveBitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        if (mHudMode == 4) {
            invalidate();
        }
    }

    public void clear() {
        if (mRotationAnimator != null) {
            mRotationAnimator.cancel();
        }
        mExpoText = null;
        mIsoText = null;
        mFocalText = null;
        mFocusText = null;
        mWbText = null;
        mHistColorsMap = null;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mHudMode == 1) {
            drawHUD(canvas);
        } else if (mHudMode == 2) {
            drawHUD(canvas);
            drawHistogram(canvas);
        } else if (mHudMode == 4) {
            drawHUD(canvas);
            drawWaveform(canvas);
        }
    }

    private void drawHUD(Canvas canvas) {
        if (mExpoText == null) return;

        float marginSide = 14f * mDensity;
        float marginTop = 12f * mDensity;
        float lineSpacing = 16f * mDensity;

        int rowCount = 5 + (mIsTripod ? 1 : 0) + (mIsOisSupported ? 1 : 0);
        float hudHeight = rowCount * lineSpacing;
        float hudWidth = 64f * mDensity;

        boolean isLandscape = (mTargetOrientation == 90 || mTargetOrientation == 270);

        float pivotX = marginSide + (isLandscape ? (hudHeight / 2f) : (hudWidth / 2f));
        float pivotY = marginTop + (isLandscape ? (hudWidth / 2f) : (hudHeight / 2f));

        float x = pivotX - (hudWidth / 2f);
        float y = pivotY - (hudHeight / 2f) + (10f * mDensity); // 10dp text baseline

        canvas.save();
        if (mAnimatedOrientation != 0f) {
            canvas.rotate(mAnimatedOrientation, pivotX, pivotY);
        }

        // Row 1: Shutter Speed
        canvas.drawText(mExpoText, x, y, hudPaint);
        y += lineSpacing;

        // Row 2: ISO
        canvas.drawText(mIsoText, x, y, hudPaint);
        y += lineSpacing;

        // Row 3: Lens Specs (Focal Length & Aperture)
        canvas.drawText(mFocalText, x, y, hudPaint);
        y += lineSpacing;

        // Row 4: Focus Mode & Distance
        canvas.drawText(mFocusText, x, y, hudPaint);
        y += lineSpacing;

        // Row 5: White Balance / CCT in Kelvin
        if (mWbText != null) {
            canvas.drawText(mWbText, x, y, hudPaint);
            y += lineSpacing;
        }

        // Row 6: OIS Status (only if active lens has hardware OIS)
        if (mIsOisSupported) {
            canvas.drawText("OIS", x, y, hudPaint);

            // Draw clean strike-through line if OIS is currently inactive
            if (!mIsOisActive) {
                float strikeY = y - (hudPaint.getTextSize() * 0.32f);
                canvas.drawLine(x - 2f * mDensity, strikeY, x + oisTextWidth + 2f * mDensity, strikeY, strikePaint);
            }
            y += lineSpacing;
        }

        // Row 7: Tripod Status (only when mounted on a tripod)
        if (mIsTripod) {
            canvas.drawText("[TRIPOD]", x, y, hudPaint);
            y += lineSpacing;
        }

        canvas.restore();
    }

    private void drawHistogram(Canvas canvas) {
        if (mHistColorsMap == null || mHistColorsMap.length < 3) return;

        computeScopeBox(canvas, HISTOGRAM_WIDTH_DP, HISTOGRAM_HEIGHT_DP);
        float w = scopeBox[6];
        float h = scopeBox[7];
        float left = scopeBox[0];
        float top = scopeBox[1];
        float right = scopeBox[2];
        float bottom = scopeBox[3];
        float pivotX = scopeBox[4];
        float pivotY = scopeBox[5];

        canvas.save();
        if (mAnimatedOrientation != 0f) {
            canvas.rotate(mAnimatedOrientation, pivotX, pivotY);
        }

        // 1. Draw solid dark background with a 2.5dp bleed buffer to mask underlying OpenGL peaking edges
        float bleedPad = 2.5f * mDensity;
        canvas.drawRect(left - bleedPad, top - bleedPad, right + bleedPad, bottom + bleedPad, histBgPaint);
        canvas.drawRect(left, top, right, bottom, histBorderPaint);
        canvas.drawLine(left + (w / 3f), top, left + (w / 3f), bottom, histBorderPaint);
        canvas.drawLine(left + (2f * w / 3f), top, left + (2f * w / 3f), bottom, histBorderPaint);

        // 2. Draw additive RGB channels (Red, Green, Blue)
        float xInterval = w / (float) (mHistSize + 1);
        histChannelPaint.setAntiAlias(true);
        histChannelPaint.setStyle(Paint.Style.FILL);
        histChannelPaint.setXfermode(histXfermode);

        for (int i = 0; i < 3; i++) {
            if (i == 0) {
                histChannelPaint.setARGB(0xDD, 0xFF, 0x1A, 0x1A); // Red channel
            } else if (i == 1) {
                histChannelPaint.setARGB(0xDD, 0x1A, 0xD4, 0x2A); // Green channel
            } else {
                histChannelPaint.setARGB(0xDD, 0x2A, 0x55, 0xFF); // Blue channel
            }

            histPath.reset();
            histPath.moveTo(left, bottom);
            for (int j = 0; j < mHistSize; j++) {
                float val = ((float) mHistColorsMap[i][j] * (h / (float) mHistMaxY));
                float px = left + (j * xInterval);
                float py = bottom - Math.min(val, h);
                histPath.lineTo(px, py);
            }
            histPath.lineTo(left + (mHistSize * xInterval), bottom);
            canvas.drawPath(histPath, histChannelPaint);
        }
        histChannelPaint.setXfermode(null);

        canvas.restore();
    }

    /**
     * Overlaid additive RGB waveform in the same top-right slot as the
     * histogram. Traces are drawn from a pre-rendered bitmap (square-root
     * intensity, high values at the top) over a graticule.
     */
    private void drawWaveform(Canvas canvas) {
        if (mWaveBitmap == null) return;

        computeScopeBox(canvas, WAVEFORM_WIDTH_DP, WAVEFORM_HEIGHT_DP);
        float w = scopeBox[6];
        float h = scopeBox[7];
        float left = scopeBox[0];
        float top = scopeBox[1];
        float right = scopeBox[2];
        float bottom = scopeBox[3];
        float pivotX = scopeBox[4];
        float pivotY = scopeBox[5];

        canvas.save();
        if (mAnimatedOrientation != 0f) {
            canvas.rotate(mAnimatedOrientation, pivotX, pivotY);
        }

        float bleedPad = 2.5f * mDensity;
        canvas.drawRect(left - bleedPad, top - bleedPad, right + bleedPad, bottom + bleedPad, waveBgPaint);
        canvas.drawRect(left, top, right, bottom, waveGridPaint);
        canvas.drawLine(left, top + (h * 0.25f), right, top + (h * 0.25f), waveGridPaint);
        canvas.drawLine(left, top + (h * 0.50f), right, top + (h * 0.50f), waveGridPaint);
        canvas.drawLine(left, top + (h * 0.75f), right, top + (h * 0.75f), waveGridPaint);

        scopeDst.set(left, top, right, bottom);
        canvas.drawBitmap(mWaveBitmap, null, scopeDst, wavePaint);

        canvas.restore();
    }

    private void computeScopeBox(Canvas canvas, float widthDp, float heightDp) {
        float marginSide = 14f * mDensity;
        float marginTop = 12f * mDensity;
        float w = widthDp * mDensity;
        float h = heightDp * mDensity;
        boolean isLandscape = (mTargetOrientation == 90 || mTargetOrientation == 270);
        float pivotX = canvas.getWidth() - marginSide - (isLandscape ? (h / 2f) : (w / 2f));
        float pivotY = marginTop + (isLandscape ? (w / 2f) : (h / 2f));
        scopeBox[0] = pivotX - (w / 2f);
        scopeBox[1] = pivotY - (h / 2f);
        scopeBox[2] = scopeBox[0] + w;
        scopeBox[3] = scopeBox[1] + h;
        scopeBox[4] = pivotX;
        scopeBox[5] = pivotY;
        scopeBox[6] = w;
        scopeBox[7] = h;
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mRotationAnimator != null) {
            mRotationAnimator.cancel();
        }
        super.onDetachedFromWindow();
    }
}