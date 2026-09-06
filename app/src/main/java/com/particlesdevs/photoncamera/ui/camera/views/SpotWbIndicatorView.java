package com.particlesdevs.photoncamera.ui.camera.views;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;

import com.particlesdevs.photoncamera.R;

/**
 * Lightweight, hardware-accelerated indicator for Live View Spot White Balance.
 * Accurately renders the exact rectangular sampling area with corner brackets,
 * a central crosshair, and a "WB" badge using the theme's focus color.
 */
public class SpotWbIndicatorView extends View {
    private final Paint boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF boundsRect = new RectF();
    private ColorStateList colorStateList;
    private int defaultColor = Color.WHITE;
    private String badgeText = "WB";
    private int mTargetOrientation = 0;

    public SpotWbIndicatorView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.getTheme().obtainStyledAttributes(
                attrs,
                R.styleable.FocusCircleView,
                0, 0
        );
        colorStateList = a.getColorStateList(R.styleable.FocusCircleView_android_color);
        float thickness = a.getDimension(R.styleable.FocusCircleView_android_thickness, 1.5f);
        if (colorStateList == null) {
            colorStateList = ColorStateList.valueOf(Color.WHITE);
        }
        a.recycle();

        this.defaultColor = colorStateList.getDefaultColor();

        // 1. Box Paint (corner brackets & crosshair)
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(thickness);
        boxPaint.setColor(defaultColor);

        // 2. Text Badge Paint ("WB" label)
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setColor(defaultColor);
        textPaint.setTextSize(getResources().getDisplayMetrics().density * 9.0f); // 9sp
        textPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    /**
     * Resets indicator to default measuring state (Theme color + "WB" badge).
     */
    public void setMeasuringState() {
        boxPaint.setColor(defaultColor);
        textPaint.setColor(defaultColor);
        badgeText = "WB";
        invalidate();
    }

    /**
     * Transitions indicator to failure state (Red outline + "FAIL" badge).
     */
    public void setErrorState(@Nullable String errorLabel) {
        int errorColor = 0xFFFF4444; // Material Red
        boxPaint.setColor(errorColor);
        textPaint.setColor(errorColor);
        badgeText = (errorLabel != null && !errorLabel.isEmpty()) ? errorLabel : "FAIL";
        invalidate();
    }

    /**
     * Smoothly rotates the indicator to match the device orientation (250ms).
     * Calculates the shortest angular path to eliminate 360-degree spins, identical to HUD rotation.
     */
    public void setOrientation(int orientation) {
        if (this.mTargetOrientation == orientation) return;
        this.mTargetOrientation = orientation;

        float startAngle = getRotation();
        float diff = (orientation - startAngle) % 360f;
        if (diff > 180f) diff -= 360f;
        if (diff < -180f) diff += 360f;
        float endAngle = startAngle + diff;

        animate().rotation(endAngle)
                .setDuration(250)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int defaultSize = (int) (getResources().getDisplayMetrics().density * 44.0f); // 44dp default size
        int w = resolveSize(defaultSize, widthMeasureSpec);
        int h = resolveSize(defaultSize, heightMeasureSpec);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float padding = boxPaint.getStrokeWidth();
        boundsRect.set(padding, padding, w - padding, h - padding);

        // 1. Draw rounded outer sampling box boundary
        canvas.drawRoundRect(boundsRect, 6f, 6f, boxPaint);

        // 2. Draw subtle center crosshair
        float cx = w / 2.0f;
        float cy = h / 2.0f;
        float crossLen = w * 0.15f;
        canvas.drawLine(cx - crossLen, cy, cx + crossLen, cy, boxPaint);
        canvas.drawLine(cx, cy - crossLen, cx, cy + crossLen, boxPaint);

        // 3. Draw "WB" text badge in the upper right corner
        float textX = w * 0.72f;
        float textY = h * 0.32f;
        canvas.drawText(badgeText, textX, textY, textPaint);
    }
}