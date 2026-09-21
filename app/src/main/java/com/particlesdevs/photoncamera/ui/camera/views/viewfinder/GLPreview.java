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
    public Point cameraSize;
    private TextureView.SurfaceTextureListener surfaceTextureListener;
    private Handler handler;
    private boolean isPlaceholder;
    private final Paint placeholderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint placeholderFramePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // True while the surface exists and the renderer owns a SurfaceTexture for
    // it. Mirrors TextureView#isAvailable() so the camera stack can tell whether
    // the preview consumer is ready without waiting for one-shot callbacks.
    // Resizing/repositioning the view recreates the GL surface, so the flag is
    // re-armed from every surface callback, not just from the renderer's
    // onSurfaceCreated (which only runs when the EGL context itself is new).
    private volatile boolean surfaceAlive = false;

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
        surfaceAlive = true;
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
        surfaceAlive = true;
        super.surfaceCreated(holder);
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        // The system tore the GL surface down (activity stopped, app sent to
        // background, or the view was resized off the window); the old
        // SurfaceTexture can no longer receive frames.
        surfaceAlive = false;
        super.surfaceDestroyed(holder);
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        // A surviving surface after a resize: the renderer's texture is still
        // valid, so the preview consumer is available again even though
        // Renderer.onSurfaceCreated may not fire for this EGL context.
        surfaceAlive = true;
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

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
    }

    // The preview surface's size is owned by ViewfinderEdgeBlurController: it is
    // the viewfinder frame by default, or the whole layout when the edge-blur
    // option is on. Its measurement therefore follows the layout params, and the
    // aspect-ratio logic lives in ViewfinderFrameView.

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

    /**
     * Requests one pre-peaking frame readback for the histogram/waveform scopes.
     * The callback fires on the GL thread with a copy of the RGBA pixels; a
     * newer request replaces an unserviced one.
     */
    public void requestAnalysisFrame(MainRenderer.AnalysisCallback callback) {
        if (mRenderer != null) {
            queueEvent(() -> mRenderer.requestAnalysis(callback));
        }
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
        return surfaceAlive && mRenderer != null && mRenderer.getmSTexture() != null;
    }

    public void setSurfaceTextureListener(TextureView.SurfaceTextureListener l) {
        this.surfaceTextureListener = l;
    }

    public void scale(int in_width, int in_height, int out_width, int out_height, int or) {
        mRenderer.scale(in_width, in_height, out_width, out_height, or);
    }

    /**
     * Enables/drives the live frosted-glass backdrops for the camera panels.
     * Pass specs built in this view's pixel space, or {@code null}/empty to
     * disable. Safe to call from the UI thread.
     */
    public void setPanelBlur(java.util.List<MainRenderer.PanelBlurSpec> specs) {
        if (mRenderer != null) {
            mRenderer.setPanelBlurSpecs(specs);
        }
    }

    /**
     * Sharp viewfinder rect in surface pixels (Android convention). The renderer
     * letterboxes the preview into it and paints the blurred backdrop around it.
     * Safe to call from the UI thread.
     */
    public void setSharpRect(android.graphics.Rect rect) {
        if (mRenderer != null) {
            mRenderer.setSharpRect(rect);
        }
    }

    /** Enables/disables the blurred backdrop around the sharp rect. */
    public void setEdgeBlurEnabled(boolean enabled) {
        if (mRenderer != null) {
            mRenderer.setEdgeBlurEnabled(enabled);
        }
    }

    /**
     * Rounds the sharp preview's corners so the blurred backdrop shows through
     * the cut corners (the round-edges option).
     */
    public void setRoundCorners(boolean enabled) {
        if (mRenderer != null) {
            mRenderer.setRoundCorners(enabled);
        }
    }
}