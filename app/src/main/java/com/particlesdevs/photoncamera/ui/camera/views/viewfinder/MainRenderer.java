package com.particlesdevs.photoncamera.ui.camera.views.viewfinder;

import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import com.particlesdevs.photoncamera.util.Log;

import androidx.annotation.NonNull;

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

    /** Downscale factor of the blur render targets relative to the surface. */
    private static final float BLUR_SCALE = 0.25f;

    private int mSharpProgram;
    private int mBlurOesProgram;
    private int mBlur2dProgram;
    private int mPanelBlurProgram;

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
        // GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        synchronized (this) {
            if (mUpdateST) {
                mSTexture.updateTexImage();
                mUpdateST = false;
            }
        }

        // Sharp preview.
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glUseProgram(mSharpProgram);
        GLES20.glUniformMatrix4fv(uTexRotateMatrix, 1, false, mTexRotateMatrix, 0);
        GLES20.glUniform1i(enablePeak, getPeakEnabled());
        GLES20.glUniform1i(mirror, mMirrorPreview ? 1 : 0);
        GLES20.glUniform2f(resolution, mViewW, mViewH);
        bindQuadAttributes(mSharpProgram);
        // The blur passes bind 2D textures to unit 0; re-bind the camera texture.
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, hTex[0]);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        // GLES20.glFlush();

        // Live frosted-glass backdrops behind the visible camera panels.
        List<PanelBlurSpec> blur = mPanelBlurSpecs;
        if (blur != null && !blur.isEmpty()) {
            drawPanelBlur(blur);
        }
    }

    /**
     * Renders one downsampled, separable-blurred copy of the preview and then
     * composites it over the sharp frame once per visible panel, each masked to
     * its own rounded/circular region. Runs entirely on the GL thread.
     */
    private void drawPanelBlur(List<PanelBlurSpec> specs) {
        if (mBlurOesProgram == 0 || mBlur2dProgram == 0 || mPanelBlurProgram == 0) {
            return;
        }
        if (specs.isEmpty()) {
            return;
        }
        float blurRadius = specs.get(0).blurRadius;
        if (!ensureBlurTargets()) {
            return;
        }
        // Screen-space kernel: the camera pass offsets in view pixels, the FBO
        // passes in FBO pixels. All quads are drawn with an identity vertex
        // transform, so both axes blur with exactly the same screen radius.
        float fboOffsetX = blurRadius * (float) mBlurW / (float) Math.max(1, mViewW);
        float fboOffsetY = blurRadius * (float) mBlurH / (float) Math.max(1, mViewH);

        // Pass A: horizontal blur of the camera texture into FBO A.
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, mFboA);
        GLES30.glViewport(0, 0, mBlurW, mBlurH);
        GLES20.glUseProgram(mBlurOesProgram);
        GLES20.glUniform2f(GLES20.glGetUniformLocation(mBlurOesProgram, "uViewSize"), mViewW, mViewH);
        GLES20.glUniform2f(GLES20.glGetUniformLocation(mBlurOesProgram, "uFboSize"), mBlurW, mBlurH);
        GLES20.glUniform2f(GLES20.glGetUniformLocation(mBlurOesProgram, "uOffsetPx"), blurRadius, 0f);
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

        // Composite: one masked draw per visible panel.
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
        GLES30.glViewport(0, 0, mViewW, mViewH);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glUseProgram(mPanelBlurProgram);
        GLES20.glUniform2f(GLES20.glGetUniformLocation(mPanelBlurProgram, "uViewSize"), mViewW, mViewH);
        int centerLoc = GLES20.glGetUniformLocation(mPanelBlurProgram, "uCenter");
        int halfSizeLoc = GLES20.glGetUniformLocation(mPanelBlurProgram, "uHalfSize");
        int angleLoc = GLES20.glGetUniformLocation(mPanelBlurProgram, "uAngle");
        int radiusLoc = GLES20.glGetUniformLocation(mPanelBlurProgram, "uRadius");
        int alphaLoc = GLES20.glGetUniformLocation(mPanelBlurProgram, "uAlpha");
        int clipCenterLoc = GLES20.glGetUniformLocation(mPanelBlurProgram, "uClipCenter");
        int clipHalfSizeLoc = GLES20.glGetUniformLocation(mPanelBlurProgram, "uClipHalfSize");
        int domeLoc = GLES20.glGetUniformLocation(mPanelBlurProgram, "uDome");
        bindQuadAttributes(mPanelBlurProgram);
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
        GLES30.glViewport(0, 0, mBlurW, mBlurH);
        GLES20.glUseProgram(mBlur2dProgram);
        GLES20.glUniform2f(GLES20.glGetUniformLocation(mBlur2dProgram, "uFboSize"), mBlurW, mBlurH);
        GLES20.glUniform2f(GLES20.glGetUniformLocation(mBlur2dProgram, "uOffsetPx"), offsetX, offsetY);
        bindQuadAttributes(mBlur2dProgram);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, srcTexture);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
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

        String vss_default = PhotonCamera.getAssetLoader().getString("shaders/preview/main_vs.glsl");
        String fss_default = PhotonCamera.getAssetLoader().getString("shaders/preview/main_fs.glsl");
        mSharpProgram = loadShader(vss_default, fss_default);
        GLES20.glUseProgram(mSharpProgram);
        uTexRotateMatrix = GLES20.glGetUniformLocation(mSharpProgram, "uTexRotateMatrix");
        GLES20.glUniformMatrix4fv(uTexRotateMatrix, 1, false, mTexRotateMatrix, 0);
        vPosition = GLES20.glGetAttribLocation(mSharpProgram, "vPosition");
        vTexCoord = GLES20.glGetAttribLocation(mSharpProgram, "vTexCoord");
        enablePeak = GLES20.glGetUniformLocation(mSharpProgram, "enablePeak");
        mirror = GLES20.glGetUniformLocation(mSharpProgram, "mirror");
        resolution = GLES20.glGetUniformLocation(mSharpProgram, "resolution");
        GLES20.glVertexAttribPointer(vPosition, 2, GLES20.GL_FLOAT, false, 4 * 2, pVertex);
        GLES20.glVertexAttribPointer(vTexCoord, 2, GLES20.GL_FLOAT, false, 4 * 2, pTexCoord);
        GLES20.glEnableVertexAttribArray(vPosition);
        GLES20.glEnableVertexAttribArray(vTexCoord);
        GLES20.glUniform2f(resolution, mViewW, mViewH);

        // Panel blur programs share the preview vertex shader so their texture
        // coordinates line up with the sharp pass (including rotation/mirror).
        String vss_quad = PhotonCamera.getAssetLoader().getString("shaders/preview/quad_vs.glsl");
        String fss_blur_oes = PhotonCamera.getAssetLoader().getString("shaders/preview/blur_oes_fs.glsl");
        String fss_blur_2d = PhotonCamera.getAssetLoader().getString("shaders/preview/blur2d_fs.glsl");
        String fss_panel = PhotonCamera.getAssetLoader().getString("shaders/preview/panel_blur_fs.glsl");
        mBlurOesProgram = loadShader(vss_quad, fss_blur_oes);
        mBlur2dProgram = loadShader(vss_quad, fss_blur_2d);
        mPanelBlurProgram = loadShader(vss_quad, fss_panel);

        mGLInit = true;
        mView.fireOnSurfaceTextureAvailable(mSTexture, 0, 0);
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
        GLES20.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
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

    private static int loadShader(String vss, String fss) {
        String SupportedVersion = GetSupportedVersion();
        vss = SupportedVersion + "\n #line 1\n" + vss;
        fss = SupportedVersion + "\n #line 1\n" + fss;
        int vshader = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);
        GLES20.glShaderSource(vshader, vss);
        GLES20.glCompileShader(vshader);
        int[] compiled = new int[1];
        GLES20.glGetShaderiv(vshader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            Log.e("Shader", "Could not compile vshader");
            Log.v("Shader", "Could not compile vshader:" + GLES20.glGetShaderInfoLog(vshader));
            GLES20.glDeleteShader(vshader);
            vshader = 0;
        }

        int fshader = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
        GLES20.glShaderSource(fshader, fss);
        GLES20.glCompileShader(fshader);
        GLES20.glGetShaderiv(fshader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            Log.e("Shader", "Could not compile fshader");
            Log.v("Shader", "Could not compile fshader:" + GLES20.glGetShaderInfoLog(fshader));
            GLES20.glDeleteShader(fshader);
            fshader = 0;
        }

        int program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vshader);
        GLES20.glAttachShader(program, fshader);
        GLES20.glLinkProgram(program);

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