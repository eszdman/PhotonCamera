package com.particlesdevs.photoncamera.ui.camera.views.viewfinder;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.TextureView;
import com.particlesdevs.photoncamera.circularbarlib.api.ManualModeConsole;

public class GLPreview extends GLSurfaceView {
    MainRenderer mRenderer;
    private int mRatioWidth;
    private int mRatioHeight;
    public Point cameraSize;
    private TextureView.SurfaceTextureListener surfaceTextureListener;
    private Handler handler;
    private boolean isPlaceholder;
    private final Paint placeholderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint placeholderFramePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // True while the renderer owns a SurfaceTexture bound to a live GL surface.
    // Mirrors TextureView#isAvailable() so the camera stack can tell whether
    // the preview consumer is ready without waiting for one-shot callbacks.
    private volatile boolean surfaceReady = false;

    public GLPreview(Context context) {
        super(context);
        init();
    }

    public GLPreview(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        // In the layout editor there is no EGL context and the PhotonCamera singleton
        // is never created, so skip GL/OpenGL setup and render a static placeholder.
        if (isInEditMode()) {
            isPlaceholder = true;
            setBackgroundColor(Color.rgb(24, 24, 24));
            return;
        }
        handler = new Handler(Looper.getMainLooper());
        mRenderer = new MainRenderer(this);

        setEGLContextClientVersion(2);
        setRenderer(mRenderer);
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
    }

    @Override
    public void draw(Canvas canvas) {
        if (isPlaceholder) {
            drawPlaceholder(canvas);
            return;
        }
        super.draw(canvas);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (isPlaceholder) {
            drawPlaceholder(canvas);
            return;
        }
        super.onDraw(canvas);
    }

    private void drawPlaceholder(Canvas canvas) {
        int w = canvas.getWidth();
        int h = canvas.getHeight();
        placeholderPaint.setColor(Color.rgb(24, 24, 24));
        canvas.drawRect(0, 0, w, h, placeholderPaint);
        // A framed rectangle standing in for the camera viewfinder.
        placeholderFramePaint.setColor(Color.rgb(58, 58, 58));
        placeholderFramePaint.setStyle(Paint.Style.STROKE);
        placeholderFramePaint.setStrokeWidth(Math.max(2f, h / 150f));
        canvas.drawRect(0, 0, w, h, placeholderFramePaint);
        canvas.drawRect(0, 0, w * 3f / 4f, h * 3f / 4f, placeholderFramePaint);
    }

    public void fireOnSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int w, int h) {
        // The renderer only calls this right after creating a fresh SurfaceTexture
        // for the current GL surface, so from this point on the preview consumer
        // exists and the camera can be opened against it.
        surfaceReady = true;
        handler.post(() -> {
            if (surfaceTextureListener != null)
                surfaceTextureListener.onSurfaceTextureAvailable(surfaceTexture, w, h);
        });
    }

    public void fireOnSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        handler.post(() -> {
            if (surfaceTextureListener != null)
                surfaceTextureListener.onSurfaceTextureDestroyed(surfaceTexture);
        });
    }

    public void surfaceCreated(SurfaceHolder holder) {
        super.surfaceCreated(holder);
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        // The system tore the GL surface down (activity stopped, app sent to
        // background); the old SurfaceTexture can no longer receive frames.
        surfaceReady = false;
        super.surfaceDestroyed(holder);
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        super.surfaceChanged(holder, format, w, h);
        handler.post(() -> {
            if (surfaceTextureListener != null)
                surfaceTextureListener.onSurfaceTextureSizeChanged(getSurfaceTexture(), w, h);
        });
    }

    @Override
    public void onResume() {
        super.onResume();

        // mRenderer.onResume();
    }

    @Override
    public void onPause() {
        fireOnSurfaceTextureDestroyed(getSurfaceTexture());
        // mRenderer.onPause();
        super.onPause();
    }

    /**
     * Sets the aspect ratio for this view. The size of the view will be measured
     * based on the ratio
     * calculated from the parameters. Note that the actual sizes of parameters
     * don't matter, that
     * is, calling setAspectRatio(2, 3) and setAspectRatio(4, 6) make the same
     * result.
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
        this.post(this::requestLayout);

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

        if (mRatioWidth == 0 || mRatioHeight == 0)
            setMeasuredDimension(width, height);
        else {
            if (width > height * mRatioWidth / mRatioHeight)
                setMeasuredDimension(width, width * mRatioHeight / mRatioWidth);
            else
                setMeasuredDimension(height * mRatioWidth / mRatioHeight, height);
            setMeasuredDimension(mRatioWidth, mRatioHeight);
        }
    }

    public SurfaceTexture getSurfaceTexture() {
        return mRenderer == null ? null : mRenderer.getmSTexture();
    }

    public void setTransform(Matrix matrix) {

    }

    public void setManualModeConsole(ManualModeConsole console) {
        if (mRenderer != null) {
            mRenderer.setManualModeConsole(console);
        }
    }

    public void setOrientation(int or) {
        mRenderer.setOrientation(or);
    }

    public void setMirror(boolean mirror) {
        mRenderer.setMirror(mirror);
        requestRender();
    }

    /**
     * Begins settle tracking for an ISZ lens-switch mask (freezes the
     * presented frame until the sensor settles, then resumes live rendering
     * on its own). Safe to call before the renderer exists (layout editor):
     * no-op then.
     */
    public void beginPreviewSettleTracking() {
        if (mRenderer != null) mRenderer.beginSettleTracking();
    }

    public boolean isAvailable() {
        return surfaceReady && mRenderer != null && mRenderer.getmSTexture() != null;
    }

    public void setSurfaceTextureListener(TextureView.SurfaceTextureListener l) {
        this.surfaceTextureListener = l;
    }

    public void scale(int in_width, int in_height, int out_width, int out_height, int or) {
        mRenderer.scale(in_width, in_height, out_width, out_height, or);
    }
}