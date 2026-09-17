package com.particlesdevs.photoncamera.ui.camera.views.viewfinder;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

/**
 * Invisible anchor for the live viewfinder rectangle. It is measured to the
 * camera preview aspect (the same numbers the preview view used before the
 * preview surface became full-bleed) and every HUD overlay is constrained to
 * it, so the preview surface itself can extend across the whole screen and
 * paint the edge-blur bands around this frame.
 */
public class ViewfinderFrameView extends View {
    private int mRatioWidth;
    private int mRatioHeight;

    public ViewfinderFrameView(Context context) {
        this(context, null);
    }

    public ViewfinderFrameView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * Sets the viewfinder aspect from the camera preview's pixel dimensions.
     * The measured size is those dimensions, exactly like the historical
     * preview view, so the viewfinder geometry is unchanged.
     */
    public void setAspectRatio(int width, int height) {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Size cannot be negative.");
        }
        mRatioWidth = width;
        mRatioHeight = height;
        post(this::requestLayout);
    }

    public int getRatioWidth() {
        return mRatioWidth;
    }

    public int getRatioHeight() {
        return mRatioHeight;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        if (mRatioWidth == 0 || mRatioHeight == 0) {
            setMeasuredDimension(width, height);
        } else {
            if (width > height * mRatioWidth / mRatioHeight) {
                setMeasuredDimension(width, width * mRatioHeight / mRatioWidth);
            } else {
                setMeasuredDimension(height * mRatioWidth / mRatioHeight, height);
            }
            setMeasuredDimension(mRatioWidth, mRatioHeight);
        }
    }
}
