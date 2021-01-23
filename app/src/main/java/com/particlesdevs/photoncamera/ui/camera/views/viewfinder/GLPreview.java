package com.particlesdevs.photoncamera.ui.camera.views.viewfinder;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.TextureView;

public class GLPreview extends GLSurfaceView {
    MainRenderer mRenderer;
    private int mRatioWidth;
    private int mRatioHeight;
    public Point cameraSize;
    private TextureView.SurfaceTextureListener surfaceTextureListener;

    public GLPreview(Context context) {
        super(context);
        init();
    }

    public GLPreview(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        mRenderer = new MainRenderer(this);
        setEGLContextClientVersion(2);
        setRenderer(mRenderer);
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
    }

    public void fireOnSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int w, int h) {
        if (surfaceTextureListener != null)
            surfaceTextureListener.onSurfaceTextureAvailable(surfaceTexture, w, h);
    }

    public void fireOnSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        if (surfaceTextureListener != null)
            surfaceTextureListener.onSurfaceTextureDestroyed(surfaceTexture);
    }

    public void surfaceCreated(SurfaceHolder holder) {
        super.surfaceCreated(holder);
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        super.surfaceDestroyed(holder);
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        super.surfaceChanged(holder, format, w, h);
        if (surfaceTextureListener != null)
            surfaceTextureListener.onSurfaceTextureSizeChanged(getSurfaceTexture(), w, h);
    }

    @Override
    public void onResume() {
        super.onResume();
        //mRenderer.onResume();
    }

    @Override
    public void onPause() {
        fireOnSurfaceTextureDestroyed(getSurfaceTexture());
        //mRenderer.onPause();
        super.onPause();
    }

    /**
     * Sets the aspect ratio for this view. The size of the view will be measured based on the ratio
     * calculated from the parameters. Note that the actual sizes of parameters don't matter, that
     * is, calling setAspectRatio(2, 3) and setAspectRatio(4, 6) make the same result.
     *
     * @param width  Relative horizontal size
     * @param height Relative vertical size
     */
    public void setAspectRatio(int width, int height) {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Size cannot be negative.");
        }
        mRatioWidth = width;
        mRatioHeight = height;
        this.post(() -> requestLayout());

    }


    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
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

    public SurfaceTexture getSurfaceTexture() {
        return mRenderer.getmSTexture();
    }

    public void setTransform(Matrix matrix) {

    }

    public void setOrientation(int or) {
        mRenderer.setOrientation(or);
    }

    boolean available = false;

    public boolean isAvailable() {
        return available;
    }

    public void setSurfaceTextureListener(TextureView.SurfaceTextureListener l) {
        this.surfaceTextureListener = l;
        available = true;
    }

    public void scale(int in_width, int in_height, int out_width, int out_height, int or) {
        mRenderer.scale(in_width, in_height, out_width, out_height, or);
    }
}
