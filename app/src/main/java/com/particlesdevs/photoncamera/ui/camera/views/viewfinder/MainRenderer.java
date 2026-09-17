package com.particlesdevs.photoncamera.ui.camera.views.viewfinder;

import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import com.particlesdevs.photoncamera.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.capture.CaptureController;
import com.particlesdevs.photoncamera.circularbarlib.api.ManualModeConsole;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.List;
import java.util.Arrays;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class MainRenderer implements GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {

    private int[] hTex;
    private final FloatBuffer pVertex;
    private final FloatBuffer pTexCoord;
    private final float[] mTexRotateMatrix = new float[] { 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1 };

    private SurfaceTexture mSTexture;

    private boolean mGLInit = false;
    private boolean mUpdateST = false;
    private volatile boolean mMirrorPreview;

    /**
     * Live frosted-glass region (quick settings bar, lens/zoom pills, manual
     * bar, knob wheel). Set from the UI thread; read on the GL thread every
     * frame. {@code null} or an empty list means "no panel blur".
     */
    public static final class PanelBlurSpec {
        public final boolean enabled;
        /** Panel centre in view pixels (Android convention: origin top-left). */
        public final float centerX;
        public final float centerY;
        /** Half extents in view pixels, including the panel's scale. */
        public final float halfW;
        public final float halfH;
        /** On-screen panel rotation in degrees (Android convention, clockwise). */
        public final float angle;
        /** Panel corner radius in px (for the rounded mask). */
        public final float cornerRadius;
        /** Blur kernel radius in px. */
        public final float blurRadius;
        /** Panel alpha, so the backdrop fades with the panel. */
        public final float alpha;
        /**
         * Optional clip rectangle in view pixels (Android convention), applied
         * in the panel's local frame so a shape larger than the view (the knob
         * wheel disc) only covers the part that is actually drawn. Zero half
         * extents disable the clip.
         */
        public final float clipCenterX;
        public final float clipCenterY;
        public final float clipHalfW;
        public final float clipHalfH;
        /** Draws the knob's dome scrim (arc through the clip rect) instead of a box. */
        public final boolean dome;

        public PanelBlurSpec(boolean enabled, float centerX, float centerY,
                             float halfW, float halfH, float angle,
                             float cornerRadius, float blurRadius, float alpha) {
            this(enabled, centerX, centerY, halfW, halfH, angle, cornerRadius,
                    blurRadius, alpha, 0f, 0f, 0f, 0f, false);
        }

        public PanelBlurSpec(boolean enabled, float centerX, float centerY,
                             float halfW, float halfH, float angle,
                             float cornerRadius, float blurRadius, float alpha,
                             float clipCenterX, float clipCenterY,
                             float clipHalfW, float clipHalfH) {
            this(enabled, centerX, centerY, halfW, halfH, angle, cornerRadius,
                    blurRadius, alpha, clipCenterX, clipCenterY, clipHalfW, clipHalfH,
                    false);
        }

        public PanelBlurSpec(boolean enabled, float centerX, float centerY,
                             float halfW, float halfH, float angle,
                             float cornerRadius, float blurRadius, float alpha,
                             float clipCenterX, float clipCenterY,
                             float clipHalfW, float clipHalfH,
                             boolean dome) {
            this.enabled = enabled;
            this.centerX = centerX;
            this.centerY = centerY;
            this.halfW = halfW;
            this.halfH = halfH;
            this.angle = angle;
            this.cornerRadius = cornerRadius;
            this.blurRadius = blurRadius;
            this.alpha = alpha;
            this.clipCenterX = clipCenterX;
            this.clipCenterY = clipCenterY;
            this.clipHalfW = clipHalfW;
            this.clipHalfH = clipHalfH;
            this.dome = dome;
        }
    }

    private volatile List<PanelBlurSpec> mPanelBlurSpecs;

    public void setPanelBlurSpecs(List<PanelBlurSpec> specs) {
        mPanelBlurSpecs = specs;
        mView.requestRender();
    }

    /** Draws the blurred backdrop around the sharp rect when true. */
    private volatile boolean mEdgeBlurEnabled;
    /** Sharp (viewfinder) rect in surface pixels, Android top-left convention. */
    private volatile boolean mSharpRectSet;
    private volatile int mSharpX;
    private volatile int mSharpY;
    private volatile int mSharpW = 1;
    private volatile int mSharpH = 1;
    private float mEdgeScrimR;
    private float mEdgeScrimG;
    private float mEdgeScrimB;
    private float mEdgeScrimA;
    private float mEdgeBlurRadiusPx = 32f;

    public void setEdgeBlurEnabled(boolean enabled) {
        mEdgeBlurEnabled = enabled;
        mView.requestRender();
    }

    /**
     * Rounds the sharp preview's corners (when the round-edges option is on) so
     * the blurred backdrop shows through the cut corners.
     */
    public void setRoundCorners(boolean enabled) {
        mRoundCorners = enabled;
        mView.requestRender();
    }

    public void setSharpRect(Rect rect) {
        if (rect == null) {
            mSharpRectSet = false;
        } else {
            mSharpX = rect.left;
            mSharpY = rect.top;
            mSharpW = Math.max(1, rect.width());
            mSharpH = Math.max(1, rect.height());
            mSharpRectSet = true;
        }
        mView.requestRender();
    }

    /** Downscale factor of the blur render targets relative to the surface. */
    private static final float BLUR_SCALE = 0.25f;

    /** Frames between retries of a failed program build (~2s at 30fps). */
    private static final int PROGRAM_RETRY_FRAMES = 60;
    private int mProgramRetryCountdown = PROGRAM_RETRY_FRAMES;

    /** Rounds the sharp preview's corners so the backdrop shows through. */
    private volatile boolean mRoundCorners;
    private float mRoundCornerRadiusPx = 40f;

    private int mSharpProgram;
    private int mBlurOesProgram;
    private int mBlur2dProgram;
    private int mPanelBlurProgram;
    private int mEdgeBlurProgram;
    private int uCornerRadius;
    private int uSharpOrigin;
    private int uEdgeViewSize;
    private int uEdgeSharpOrigin;
    private int uEdgeSharpSize;
    private int uEdgeCornerRadius;
    private int uEdgeScrimColor;
    private int uEdgeScrimAlpha;
    /** Clamp rect for the separable blur passes, in FBO UV space. */
    private float mBlurClampMinX;
    private float mBlurClampMinY = 1f;
    private float mBlurClampMaxX = 1f;
    private float mBlurClampMaxY = 1f;

    private int mFboA;
    private int mFboB;
    private int mBlurTexA;
    private int mBlurTexB;
    private int mBlurW;
    private int mBlurH;
    private int mViewW = 1;
    private int mViewH = 1;

    /**
     * Frames to observe before an ISZ lens transition is considered settled.
     * ~10 frames at 30fps covers the sensor's readout-mode switch latency.
     */
    public static final int ISZ_SETTLE_FRAMES = 10;

    /**
     * While true, newly arrived frames are counted toward the settle threshold
     * for ISZ sensor-mode transitions. Every frame is still latched (on the GL
     * thread, without rendering) so the camera pipeline keeps flowing, while
     * the SurfaceView keeps presenting the frozen pre-switch frame until the
     * sensor has settled. Latching without rendering can never stall the
     * pipeline, so this mask cannot wedge the preview.
     */
    private volatile boolean mSettleTracking;
    private final IszSettleCounter mSettleCounter = new IszSettleCounter(ISZ_SETTLE_FRAMES);

    /**
     * Begins settle tracking for an ISZ lens-switch mask. Re-arming resets the
     * counter. Tracking stops on its own at the threshold; a stuck flag with
     * no frames is inert. Safe to call from any thread.
     */
    public void beginSettleTracking() {
        mSettleCounter.reset();
        mSettleTracking = true;
    }

    private final GLPreview mView;
    private ManualModeConsole mManualModeConsole;

    public void setManualModeConsole(ManualModeConsole console) {
        this.mManualModeConsole = console;
    }

    MainRenderer(GLPreview view) {
        mView = view;
        mRoundCornerRadiusPx = view.getResources().getDimension(R.dimen.viewfinder_round_corner_radius);
        pVertex = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        float[] vtmp = { 1.0f, -1.0f, -1.0f, -1.0f, 1.0f, 1.0f, -1.0f, 1.0f };
        pVertex.put(vtmp);
        pVertex.position(0);
        pTexCoord = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        float[] ttmp = { 1.0f, 1.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f };
        pTexCoord.put(ttmp);
        pTexCoord.position(0);
        setOrientation(180);
    }

    public void onDrawFrame(GL10 unused) {
        if (!mGLInit)
            return;
        // Always start from the window framebuffer: a skipped pass must never
        // leave the blur FBO bound across frames.
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);

        // A program can fail to build (unreadable asset, driver hiccup); retry
        // rarely so the pipeline can heal itself without hammering the GL thread.
        if (mSharpProgram == 0 || mBlurOesProgram == 0 || mBlur2dProgram == 0
                || mPanelBlurProgram == 0 || mEdgeBlurProgram == 0) {
            if (--mProgramRetryCountdown <= 0) {
                ensureGlPrograms();
                mProgramRetryCountdown = PROGRAM_RETRY_FRAMES;
            }
        }

        synchronized (this) {
            if (mUpdateST) {
                mSTexture.updateTexImage();
                mUpdateST = false;
            }
        }

        // Sharp preview, letterboxed into the viewfinder rect when the surface
        // is full-bleed (edge blur enabled). Without a sharp rect the whole
        // surface is the viewfinder, exactly as before.
        int sharpLeft = mSharpRectSet ? Math.max(0, mSharpX) : 0;
        int sharpWidth = mSharpRectSet ? Math.min(mSharpW, mViewW - sharpLeft) : mViewW;
        int sharpBottom = mSharpRectSet ? Math.max(0, mViewH - (mSharpY + mSharpH)) : 0;
        int sharpHeight = mSharpRectSet ? Math.min(mSharpH, mViewH - sharpBottom) : mViewH;
        sharpWidth = Math.max(1, sharpWidth);
        sharpHeight = Math.max(1, sharpHeight);
        // The blurred backdrop is drawn whenever the option is on: it fills the
        // letterbox areas and shows through the rounded-corner cutouts.
        boolean edgeBlur = mEdgeBlurEnabled;

        // Blur first: both the panel backdrops and the edge backdrop sample it.
        List<PanelBlurSpec> blur = mPanelBlurSpecs;
        boolean hasPanels = blur != null && !blur.isEmpty();
        boolean blurReady = false;
        if ((hasPanels || edgeBlur)
                && mBlurOesProgram != 0 && mBlur2dProgram != 0
                && ensureBlurTargets()) {
            float blurRadius = hasPanels ? blur.get(0).blurRadius : mEdgeBlurRadiusPx;
            blurReady = runBlurPasses(blurRadius, sharpLeft, sharpBottom, sharpWidth, sharpHeight);
        }

        // Every frame starts from black: the sharp pass is the only
        // fullscreen paint, so without this clear any skipped/degenerate
        // sharp draw would leave recycled EGL garbage on screen. The blur
        // passes leave a blur FBO bound, so the window framebuffer is bound
        // back first.
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
        GLES30.glViewport(0, 0, mViewW, mViewH);
        GLES20.glClearColor(0f, 0f, 0f, 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        // Full-bleed blurred backdrop under the sharp preview.
        if (blurReady && edgeBlur) {
            drawEdgeBackdrop(sharpLeft, sharpBottom, sharpWidth, sharpHeight);
        }

        // Sharp preview, letterboxed into the viewfinder rect when the surface
        // is full-bleed (edge blur enabled). Without a sharp rect the whole
        // surface is the viewfinder, exactly as before.
        setViewportClamped(sharpLeft, sharpBottom, sharpWidth, sharpHeight, mViewW, mViewH);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glUseProgram(mSharpProgram);
        GLES20.glUniformMatrix4fv(uTexRotateMatrix, 1, false, mTexRotateMatrix, 0);
        GLES20.glUniform1i(enablePeak, getPeakEnabled());
        GLES20.glUniform1i(mirror, mMirrorPreview ? 1 : 0);
        GLES20.glUniform2f(resolution, sharpWidth, sharpHeight);
        GLES20.glUniform1f(uCornerRadius, mRoundCorners ? mRoundCornerRadiusPx : 0f);
        GLES20.glUniform2f(uSharpOrigin, sharpLeft, sharpBottom);
        bindQuadAttributes(mSharpProgram);
        // The blur passes bind 2D textures to unit 0; re-bind the camera texture.
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, hTex[0]);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        // GLES20.glFlush();

        // Live frosted-glass backdrops behind the visible camera panels.
        if (blurReady && hasPanels) {
            compositePanels(blur, sharpLeft, sharpBottom, sharpWidth, sharpHeight);
        }
    }

    /**
     * Full-bleed blurred backdrop drawn under the sharp preview. Each letterbox
     * band samples the same frame strip the mirrored bands used (the strip of
     * the viewfinder adjacent to that edge) with the frame's own orientation,
     * so the content is blurred but not flipped. Inside the sharp rect the
     * exact preview position is kept, which is what shows through the
     * rounded-corner cutouts. Blends the shared panel scrim so the bands read
     * as the same glass treatment.
     */
    private void drawEdgeBackdrop(int sharpLeft, int sharpBottom, int sharpWidth, int sharpHeight) {
        if (mEdgeBlurProgram == 0) {
            return;
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
        GLES30.glViewport(0, 0, mViewW, mViewH);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glUseProgram(mEdgeBlurProgram);
        GLES20.glUniform2f(uEdgeViewSize, mViewW, mViewH);
        GLES20.glUniform2f(uEdgeSharpOrigin, sharpLeft, sharpBottom);
        GLES20.glUniform2f(uEdgeSharpSize, sharpWidth, sharpHeight);
        GLES20.glUniform1f(uEdgeCornerRadius, mRoundCorners ? mRoundCornerRadiusPx : 0f);
        GLES20.glUniform3f(uEdgeScrimColor, mEdgeScrimR, mEdgeScrimG, mEdgeScrimB);
        GLES20.glUniform1f(uEdgeScrimAlpha, mEdgeScrimA);
        bindQuadAttributes(mEdgeBlurProgram);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mBlurTexB);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    }

    /**
     * Renders one downsampled, separable-blurred copy of the preview into
     * {@code mBlurTexB}. Runs entirely on the GL thread.
     */
    private boolean runBlurPasses(float blurRadius, int sharpLeft, int sharpBottom,
                                  int sharpWidth, int sharpHeight) {
        // Screen-space kernel: the camera pass offsets in view pixels, the FBO
        // passes in FBO pixels. The blurred copy is letterboxed into the sharp
        // rect exactly like the sharp pass, so the offsets stay the plain
        // screen-space radius (identity placement when the rect is full-surface).
        float fboScaleX = (float) mBlurW / (float) Math.max(1, mViewW);
        float fboScaleY = (float) mBlurH / (float) Math.max(1, mViewH);
        float fboOffsetX = blurRadius * fboScaleX;
        float fboOffsetY = blurRadius * fboScaleY;
        // Separable passes sample clamped to the sharp rect (FBO UV space).
        mBlurClampMinX = sharpLeft * fboScaleX / Math.max(1, mBlurW);
        mBlurClampMinY = sharpBottom * fboScaleY / Math.max(1, mBlurH);
        mBlurClampMaxX = (sharpLeft + sharpWidth) * fboScaleX / Math.max(1, mBlurW);
        mBlurClampMaxY = (sharpBottom + sharpHeight) * fboScaleY / Math.max(1, mBlurH);

        // Pass A: horizontal blur of the camera texture into FBO A, inside the
        // sharp rect only.
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, mFboA);
        setViewportClamped(Math.round(sharpLeft * fboScaleX), Math.round(sharpBottom * fboScaleY),
                Math.round(sharpWidth * fboScaleX), Math.round(sharpHeight * fboScaleY),
                mBlurW, mBlurH);
        GLES20.glUseProgram(mBlurOesProgram);
        GLES20.glUniform2f(GLES20.glGetUniformLocation(mBlurOesProgram, "uViewSize"), mViewW, mViewH);
        GLES20.glUniform2f(GLES20.glGetUniformLocation(mBlurOesProgram, "uFboSize"), mBlurW, mBlurH);
        GLES20.glUniform2f(GLES20.glGetUniformLocation(mBlurOesProgram, "uOffsetPx"), blurRadius, 0f);
        GLES20.glUniform2f(GLES20.glGetUniformLocation(mBlurOesProgram, "uSharpOrigin"),
                sharpLeft, sharpBottom);
        GLES20.glUniform2f(GLES20.glGetUniformLocation(mBlurOesProgram, "uSharpSize"),
                sharpWidth, sharpHeight);
        GLES20.glUniform1f(GLES20.glGetUniformLocation(mBlurOesProgram, "uCos"), mTexRotateMatrix[0]);
        GLES20.glUniform1f(GLES20.glGetUniformLocation(mBlurOesProgram, "uSin"), mTexRotateMatrix[1]);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(mBlurOesProgram, "mirror"), mMirrorPreview ? 1 : 0);
        bindQuadAttributes(mBlurOesProgram);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, hTex[0]);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        // Pass B: vertical blur of FBO A into FBO B.
        drawFboBlur(mFboB, mBlurTexA, 0f, fboOffsetY);

        // Second H+V iteration smooths the widely spread taps of the 32dp kernel.
        drawFboBlur(mFboA, mBlurTexB, fboOffsetX, 0f);
        drawFboBlur(mFboB, mBlurTexA, 0f, fboOffsetY);
        return true;
    }

    /**
     * Composites the blurred preview over the sharp frame once per visible
     * panel, each masked to its own rounded/circular region. Sampling is clamped
     * to the sharp rect, so a panel over a letterbox band sees the same blurred
     * edge content as the backdrop (identity when the rect is the whole surface).
     */
    private void compositePanels(List<PanelBlurSpec> specs, int sharpLeft, int sharpBottom,
                                 int sharpWidth, int sharpHeight) {
        if (mPanelBlurProgram == 0) {
            return;
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
        GLES30.glViewport(0, 0, mViewW, mViewH);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glUseProgram(mPanelBlurProgram);
        GLES20.glUniform2f(GLES20.glGetUniformLocation(mPanelBlurProgram, "uViewSize"), mViewW, mViewH);
        float viewW = Math.max(1f, mViewW);
        float viewH = Math.max(1f, mViewH);
        GLES20.glUniform2f(GLES20.glGetUniformLocation(mPanelBlurProgram, "uSampleMin"),
                sharpLeft / viewW, sharpBottom / viewH);
        GLES20.glUniform2f(GLES20.glGetUniformLocation(mPanelBlurProgram, "uSampleMax"),
                (sharpLeft + sharpWidth) / viewW, (sharpBottom + sharpHeight) / viewH);
        int centerLoc = GLES20.glGetUniformLocation(mPanelBlurProgram, "uCenter");
        int halfSizeLoc = GLES20.glGetUniformLocation(mPanelBlurProgram, "uHalfSize");
        int angleLoc = GLES20.glGetUniformLocation(mPanelBlurProgram, "uAngle");
        int radiusLoc = GLES20.glGetUniformLocation(mPanelBlurProgram, "uRadius");
        int alphaLoc = GLES20.glGetUniformLocation(mPanelBlurProgram, "uAlpha");
        int clipCenterLoc = GLES20.glGetUniformLocation(mPanelBlurProgram, "uClipCenter");
        int clipHalfSizeLoc = GLES20.glGetUniformLocation(mPanelBlurProgram, "uClipHalfSize");
        int domeLoc = GLES20.glGetUniformLocation(mPanelBlurProgram, "uDome");
        bindQuadAttributes(mPanelBlurProgram);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mBlurTexB);
        for (PanelBlurSpec spec : specs) {
            if (!spec.enabled) {
                continue;
            }
            GLES20.glUniform2f(centerLoc, spec.centerX, mViewH - spec.centerY);
            GLES20.glUniform2f(halfSizeLoc, spec.halfW, spec.halfH);
            // Android rotates clockwise in y-down space; GL is y-up, so negate.
            GLES20.glUniform1f(angleLoc, (float) Math.toRadians(-spec.angle));
            GLES20.glUniform1f(radiusLoc, spec.cornerRadius);
            GLES20.glUniform1f(alphaLoc, spec.alpha);
            // Clip is expressed in the same Android convention as the centre.
            GLES20.glUniform2f(clipCenterLoc, spec.clipCenterX, mViewH - spec.clipCenterY);
            GLES20.glUniform2f(clipHalfSizeLoc, spec.clipHalfW, spec.clipHalfH);
            GLES20.glUniform1f(domeLoc, spec.dome ? 1f : 0f);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        }
        GLES20.glDisable(GLES20.GL_BLEND);
    }

    /** Runs one 2D blur pass from {@code srcTexture} into {@code fbo}. */
    private void drawFboBlur(int fbo, int srcTexture, float offsetX, float offsetY) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo);
        setViewportClamped(0, 0, mBlurW, mBlurH, mBlurW, mBlurH);
        GLES20.glUseProgram(mBlur2dProgram);
        GLES20.glUniform2f(GLES20.glGetUniformLocation(mBlur2dProgram, "uFboSize"), mBlurW, mBlurH);
        GLES20.glUniform2f(GLES20.glGetUniformLocation(mBlur2dProgram, "uOffsetPx"), offsetX, offsetY);
        GLES20.glUniform2f(GLES20.glGetUniformLocation(mBlur2dProgram, "uClampMin"),
                mBlurClampMinX, mBlurClampMinY);
        GLES20.glUniform2f(GLES20.glGetUniformLocation(mBlur2dProgram, "uClampMax"),
                mBlurClampMaxX, mBlurClampMaxY);
        bindQuadAttributes(mBlur2dProgram);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, srcTexture);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    }

    /**
     * Sets a viewport that is guaranteed to be a non-empty sub-rect of the
     * current framebuffer: a zero/oversized viewport silently kills a pass.
     */
    private void setViewportClamped(int x, int y, int width, int height, int maxW, int maxH) {
        maxW = Math.max(1, maxW);
        maxH = Math.max(1, maxH);
        x = Math.max(0, Math.min(x, maxW - 1));
        y = Math.max(0, Math.min(y, maxH - 1));
        width = Math.max(1, Math.min(width, maxW - x));
        height = Math.max(1, Math.min(height, maxH - y));
        GLES30.glViewport(x, y, width, height);
    }

    private void bindQuadAttributes(int program) {
        int pos = GLES20.glGetAttribLocation(program, "vPosition");
        int tex = GLES20.glGetAttribLocation(program, "vTexCoord");
        if (pos >= 0) {
            GLES20.glEnableVertexAttribArray(pos);
            GLES20.glVertexAttribPointer(pos, 2, GLES20.GL_FLOAT, false, 4 * 2, pVertex);
        }
        if (tex >= 0) {
            GLES20.glEnableVertexAttribArray(tex);
            GLES20.glVertexAttribPointer(tex, 2, GLES20.GL_FLOAT, false, 4 * 2, pTexCoord);
        }
    }

    private int uTexRotateMatrix;
    private int vPosition;
    private int vTexCoord;
    private int enablePeak;
    private int mirror;
    private int resolution;

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        // The EGL context is fresh: drop any GL resources from the previous one.
        releaseBlurTargets();

        initTex();
        mSTexture = new SurfaceTexture(hTex[0]);
        mSTexture.setOnFrameAvailableListener(this);

        // Program names are context-local; the old ones died with the context.
        mSharpProgram = 0;
        mBlurOesProgram = 0;
        mBlur2dProgram = 0;
        mPanelBlurProgram = 0;
        mEdgeBlurProgram = 0;
        mProgramRetryCountdown = PROGRAM_RETRY_FRAMES;
        ensureGlPrograms();

        mGLInit = true;
        mView.fireOnSurfaceTextureAvailable(mSTexture, 0, 0);
    }

    /**
     * Builds every program that is still missing. Called when the EGL context
     * is created and occasionally retried from the render loop, so a program
     * that failed to build (transient allocation failure, unreadable asset)
     * cannot permanently disable the blur pipeline.
     */
    private void ensureGlPrograms() {
        if (mSharpProgram == 0) {
            String vss_default = loadAsset("shaders/preview/main_vs.glsl");
            String fss_default = loadAsset("shaders/preview/main_fs.glsl");
            mSharpProgram = loadShader(vss_default, fss_default);
            if (mSharpProgram != 0) {
                GLES20.glUseProgram(mSharpProgram);
                uTexRotateMatrix = GLES20.glGetUniformLocation(mSharpProgram, "uTexRotateMatrix");
                GLES20.glUniformMatrix4fv(uTexRotateMatrix, 1, false, mTexRotateMatrix, 0);
                vPosition = GLES20.glGetAttribLocation(mSharpProgram, "vPosition");
                vTexCoord = GLES20.glGetAttribLocation(mSharpProgram, "vTexCoord");
                enablePeak = GLES20.glGetUniformLocation(mSharpProgram, "enablePeak");
                mirror = GLES20.glGetUniformLocation(mSharpProgram, "mirror");
                resolution = GLES20.glGetUniformLocation(mSharpProgram, "resolution");
                uCornerRadius = GLES20.glGetUniformLocation(mSharpProgram, "uCornerRadius");
                uSharpOrigin = GLES20.glGetUniformLocation(mSharpProgram, "uSharpOrigin");
                GLES20.glVertexAttribPointer(vPosition, 2, GLES20.GL_FLOAT, false, 4 * 2, pVertex);
                GLES20.glVertexAttribPointer(vTexCoord, 2, GLES20.GL_FLOAT, false, 4 * 2, pTexCoord);
                GLES20.glEnableVertexAttribArray(vPosition);
                GLES20.glEnableVertexAttribArray(vTexCoord);
                GLES20.glUniform2f(resolution, mViewW, mViewH);
            }
        }

        // Panel blur programs share the preview vertex shader so their texture
        // coordinates line up with the sharp pass (including rotation/mirror).
        if (mBlurOesProgram == 0 || mBlur2dProgram == 0 || mPanelBlurProgram == 0) {
            String vss_quad = loadAsset("shaders/preview/quad_vs.glsl");
            if (mBlurOesProgram == 0) {
                mBlurOesProgram = loadShader(vss_quad, loadAsset("shaders/preview/blur_oes_fs.glsl"));
            }
            if (mBlur2dProgram == 0) {
                mBlur2dProgram = loadShader(vss_quad, loadAsset("shaders/preview/blur2d_fs.glsl"));
            }
            if (mPanelBlurProgram == 0) {
                mPanelBlurProgram = loadShader(vss_quad, loadAsset("shaders/preview/panel_blur_fs.glsl"));
            }
        }

        if (mEdgeBlurProgram == 0) {
            String fss_edge = loadAsset("shaders/preview/edge_blur_fs.glsl");
            mEdgeBlurProgram = loadShader(loadAsset("shaders/preview/quad_vs.glsl"), fss_edge);
            if (mEdgeBlurProgram != 0) {
                uEdgeViewSize = GLES20.glGetUniformLocation(mEdgeBlurProgram, "uViewSize");
                uEdgeSharpOrigin = GLES20.glGetUniformLocation(mEdgeBlurProgram, "uSharpOrigin");
                uEdgeSharpSize = GLES20.glGetUniformLocation(mEdgeBlurProgram, "uSharpSize");
                uEdgeCornerRadius = GLES20.glGetUniformLocation(mEdgeBlurProgram, "uCornerRadius");
                uEdgeScrimColor = GLES20.glGetUniformLocation(mEdgeBlurProgram, "uScrimColor");
                uEdgeScrimAlpha = GLES20.glGetUniformLocation(mEdgeBlurProgram, "uScrimAlpha");
                int scrim = ContextCompat.getColor(mView.getContext(), R.color.cam_panel_scrim);
                mEdgeScrimR = Color.red(scrim) / 255f;
                mEdgeScrimG = Color.green(scrim) / 255f;
                mEdgeScrimB = Color.blue(scrim) / 255f;
                mEdgeScrimA = Color.alpha(scrim) / 255f;
                mEdgeBlurRadiusPx = mView.getResources().getDimension(R.dimen.cam_panel_blur_radius);
            }
        }
    }

    public void onSurfaceChanged(GL10 unused, int width, int height) {
        mViewW = Math.max(1, width);
        mViewH = Math.max(1, height);
        GLES30.glViewport(0, 0, width, height);
        // Blur targets are sized from the surface; let them be recreated lazily.
        releaseBlurTargets();
    }

    private boolean ensureBlurTargets() {
        int w = Math.max(1, Math.round(mViewW * BLUR_SCALE));
        int h = Math.max(1, Math.round(mViewH * BLUR_SCALE));
        if (mBlurTexA != 0 && w == mBlurW && h == mBlurH) {
            return true;
        }
        releaseBlurTargets();
        mBlurW = w;
        mBlurH = h;
        mBlurTexA = createBlurTexture(w, h);
        mBlurTexB = createBlurTexture(w, h);
        mFboA = createBlurFramebuffer(mBlurTexA);
        mFboB = createBlurFramebuffer(mBlurTexB);
        if (mBlurTexA == 0 || mBlurTexB == 0 || mFboA == 0 || mFboB == 0) {
            releaseBlurTargets();
            return false;
        }
        return true;
    }

    private int createBlurTexture(int width, int height) {
        int[] tex = new int[1];
        GLES20.glGenTextures(1, tex, 0);
        if (tex[0] == 0) {
            return 0;
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[0]);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        return tex[0];
    }

    private int createBlurFramebuffer(int texture) {
        int[] fbo = new int[1];
        GLES30.glGenFramebuffers(1, fbo, 0);
        if (fbo[0] == 0) {
            return 0;
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo[0]);
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, texture, 0);
        boolean complete = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
                == GLES30.GL_FRAMEBUFFER_COMPLETE;
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
        if (!complete) {
            GLES30.glDeleteFramebuffers(1, fbo, 0);
            return 0;
        }
        return fbo[0];
    }

    private void releaseBlurTargets() {
        if (mFboA != 0 || mFboB != 0) {
            int[] fbos = new int[]{mFboA, mFboB};
            GLES30.glDeleteFramebuffers(2, fbos, 0);
            mFboA = 0;
            mFboB = 0;
        }
        if (mBlurTexA != 0 || mBlurTexB != 0) {
            int[] texs = new int[]{mBlurTexA, mBlurTexB};
            GLES20.glDeleteTextures(2, texs, 0);
            mBlurTexA = 0;
            mBlurTexB = 0;
        }
        mBlurW = 0;
        mBlurH = 0;
    }

    public SurfaceTexture getmSTexture() {
        return mSTexture;
    }

    private void initTex() {
        hTex = new int[1];
        GLES20.glGenTextures(1, hTex, 0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, hTex[0]);
        // The camera texture is an OES texture: the parameters must be set on the
        // external target it is bound to. Setting them on GL_TEXTURE_2D left the
        // camera texture on the driver defaults (REPEAT wrap, mipmap min filter),
        // which point-sampled the downscaled preview into aliasing ("static")
        // while the multi-tap blur hid it.
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
    }

    public synchronized void onFrameAvailable(SurfaceTexture st) {
        if (mSettleTracking) {
            if (!mSettleCounter.onFrame()) {
                // Freeze: consume the frame on the GL thread (releasing its
                // buffer back to the camera pipeline) without rendering, so
                // the frozen pre-switch frame stays on screen.
                mView.queueEvent(() -> {
                    try {
                        if (mSettleTracking) st.updateTexImage();
                    } catch (Exception ignored) {
                        // Surface gone mid-transition (e.g. paused): drop it.
                    }
                });
                return;
            }
            // Settled: fall through to live rendering of the newest frame.
            mSettleTracking = false;
        }
        mUpdateST = true;
        mView.requestRender();
    }

    private static String GetSupportedVersion() {
        return "#version 300 es";
    }

    /** Reads a shader asset safely; a missing asset must never kill the GL thread. */
    private String loadAsset(String name) {
        try {
            String source = PhotonCamera.getAssetLoader().getString(name);
            if (source != null && !source.isEmpty()) {
                return source;
            }
        } catch (RuntimeException e) {
            // Fall through to the direct AssetManager read below.
        }
        try (java.io.InputStream in = mView.getContext().getAssets().open(name)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toString("UTF-8");
        } catch (java.io.IOException e) {
            android.util.Log.e("PhotonShader", "Could not read shader asset " + name);
            return "";
        }
    }

    private static int loadShader(String vss, String fss) {
        if (vss == null || vss.isEmpty() || fss == null || fss.isEmpty()) {
            Log.e("Shader", "Missing shader source, program not created");
            return 0;
        }
        String SupportedVersion = GetSupportedVersion();
        vss = SupportedVersion + "\n #line 1\n" + vss;
        fss = SupportedVersion + "\n #line 1\n" + fss;
        int vshader = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);
        GLES20.glShaderSource(vshader, vss);
        GLES20.glCompileShader(vshader);
        int[] compiled = new int[1];
        GLES20.glGetShaderiv(vshader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            android.util.Log.e("PhotonShader",
                    "Could not compile vshader: " + GLES20.glGetShaderInfoLog(vshader));
            GLES20.glDeleteShader(vshader);
            vshader = 0;
        }

        int fshader = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
        GLES20.glShaderSource(fshader, fss);
        GLES20.glCompileShader(fshader);
        GLES20.glGetShaderiv(fshader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            android.util.Log.e("PhotonShader",
                    "Could not compile fshader: " + GLES20.glGetShaderInfoLog(fshader));
            GLES20.glDeleteShader(fshader);
            fshader = 0;
        }

        int program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vshader);
        GLES20.glAttachShader(program, fshader);
        GLES20.glLinkProgram(program);
        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (vshader != 0) GLES20.glDeleteShader(vshader);
        if (fshader != 0) GLES20.glDeleteShader(fshader);
        if (linkStatus[0] == 0) {
            android.util.Log.e("PhotonShader",
                    "Could not link program: " + GLES20.glGetProgramInfoLog(program));
            GLES20.glDeleteProgram(program);
            return 0;
        }
        return program;
    }

    public void setMirror(boolean mirrorPreview) {
        mMirrorPreview = mirrorPreview;
    }

    private int getPeakEnabled() {
        int focusPeakSetting = PhotonCamera.getSettings().focusPeak;
        if (focusPeakSetting == 1) {
            return 1; // On
        } else if (focusPeakSetting == 2) {
            // Auto: show peaking when manual focus mode is active OR when focus parameter
            // is selected via UI
            if (mManualModeConsole != null) {
                return (mManualModeConsole.isManualFocusModeActive() || mManualModeConsole.isFocusParameterSelected())
                        ? 1
                        : 0;
            }
            return 0;
        }
        return 0; // Off
    }

    public void setOrientation(int or) {
        android.opengl.Matrix.setRotateM(mTexRotateMatrix, 0, or, 0f, 0f, 1f);
    }

    public void setTransform(@NonNull android.graphics.Matrix matrix) {
        Log.d("MainRenderer", "setTransform: " + matrix + " " + Arrays.toString(mTexRotateMatrix));
        matrix.getValues(mTexRotateMatrix);
    }

    RectF mLastImageRect = new RectF();
    RectF inputRect = new RectF();

    public void scale(int in_width, int in_height, int out_width, int out_height, int rotation) {
        int difw = out_width - in_width;
        int difh = out_height - in_height;

        inputRect.left = (int) (difw / 2);
        inputRect.top = (int) (difh / 2);
        inputRect.right = in_width;
        inputRect.bottom = in_height;
        if (mLastImageRect != inputRect) {
            GLES20.glViewport((int) inputRect.left, (int) inputRect.top, (int) inputRect.width(),
                    (int) inputRect.height());

            mLastImageRect.set(inputRect);
        }

    }
}