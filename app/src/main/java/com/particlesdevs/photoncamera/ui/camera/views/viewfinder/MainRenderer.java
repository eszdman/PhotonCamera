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
import com.particlesdevs.photoncamera.api.CameraMode;
import com.particlesdevs.photoncamera.processing.parameters.NightSceneAnalyzer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
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
    private int meterFramebuffer, meterTexture;
    private boolean meterFailed;
    private long lastMeterTime, lastMeterImageTimestamp;
    private final int[] savedViewport = new int[4];
    private final int[] savedFramebuffer = new int[1];
    private final float[] meterIdentity = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
    private final ByteBuffer meterPixels = ByteBuffer.allocateDirect(
            NightSceneAnalyzer.WIDTH * NightSceneAnalyzer.HEIGHT * 4);

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
        GLES20.glUniformMatrix4fv(uTexRotateMatrix, 1, false, mTexRotateMatrix, 0);
        int peakEnabled = getPeakEnabled();
        GLES20.glUniform1i(enablePeak, peakEnabled);
        GLES20.glUniform1i(mirror, mMirrorPreview ? 1 : 0);

        GLES20.glVertexAttribPointer(vPosition, 2, GLES20.GL_FLOAT, false, 4 * 2, pVertex);
        GLES20.glVertexAttribPointer(vTexCoord, 2, GLES20.GL_FLOAT, false, 4 * 2, pTexCoord);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        meterNightPreview();
        // GLES20.glFlush();
    }

    private int uTexRotateMatrix;
    private int vPosition;
    private int vTexCoord;
    private int enablePeak;
    private int mirror;

    /** Reuses the camera texture, without extra Camera2 streams or full-screen readback. */
    private void meterNightPreview() {
        CaptureController controller = PhotonCamera.getCaptureController();
        if (controller == null) return;
        if (PhotonCamera.getSettings().selectedMode != CameraMode.NIGHT) {
            controller.nightSceneAnalyzer.reset();
            return;
        }
        long now = System.nanoTime();
        long imageTimestamp = mSTexture.getTimestamp();
        if (meterFailed || imageTimestamp == 0 || imageTimestamp == lastMeterImageTimestamp
                || now - lastMeterTime < NightSceneAnalyzer.INTERVAL_NS) return;
        lastMeterTime = now;
        lastMeterImageTimestamp = imageTimestamp;
        GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, savedViewport, 0);
        GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, savedFramebuffer, 0);
        try {
            if (meterFramebuffer == 0) {
                int[] names = new int[1];
                GLES20.glGenTextures(1, names, 0);
                meterTexture = names[0];
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, meterTexture);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
                GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                        NightSceneAnalyzer.WIDTH, NightSceneAnalyzer.HEIGHT, 0,
                        GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
                GLES20.glGenFramebuffers(1, names, 0);
                meterFramebuffer = names[0];
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, meterFramebuffer);
                GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                        GLES20.GL_TEXTURE_2D, meterTexture, 0);
                if (GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) != GLES20.GL_FRAMEBUFFER_COMPLETE)
                    throw new IllegalStateException("Night preview framebuffer unavailable");
            }
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, meterFramebuffer);
            GLES20.glViewport(0, 0, NightSceneAnalyzer.WIDTH, NightSceneAnalyzer.HEIGHT);
            // Stable sensor orientation; exclude focus-peaking and mirror UI effects.
            GLES20.glUniformMatrix4fv(uTexRotateMatrix, 1, false, meterIdentity, 0);
            GLES20.glUniform1i(enablePeak, 0);
            GLES20.glUniform1i(mirror, 0);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            meterPixels.clear();
            GLES20.glReadPixels(0, 0, NightSceneAnalyzer.WIDTH, NightSceneAnalyzer.HEIGHT,
                    GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, meterPixels);
            if (GLES20.glGetError() != GLES20.GL_NO_ERROR)
                throw new IllegalStateException("Night preview readback failed");
            controller.nightSceneAnalyzer.accept(meterPixels, now);
        } catch (RuntimeException e) {
            meterFailed = true;
            controller.nightSceneAnalyzer.reset();
            Log.e("NightSceneAnalyzer", "Preview metering disabled for this GL session", e);
        } finally {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, savedFramebuffer[0]);
            GLES20.glViewport(savedViewport[0], savedViewport[1], savedViewport[2], savedViewport[3]);
            GLES20.glUniformMatrix4fv(uTexRotateMatrix, 1, false, mTexRotateMatrix, 0);
            GLES20.glUniform1i(enablePeak, getPeakEnabled());
            GLES20.glUniform1i(mirror, mMirrorPreview ? 1 : 0);
        }
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        // Names from the previous EGL context are no longer valid.
        meterFramebuffer = meterTexture = 0;
        meterFailed = false;
        lastMeterTime = lastMeterImageTimestamp = 0;
        if (PhotonCamera.getCaptureController() != null)
            PhotonCamera.getCaptureController().nightSceneAnalyzer.reset();
        initTex();
        mSTexture = new SurfaceTexture(hTex[0]);
        mSTexture.setOnFrameAvailableListener(this);

        String vss_default = PhotonCamera.getAssetLoader().getString("shaders/preview/main_vs.glsl");
        String fss_default = PhotonCamera.getAssetLoader().getString("shaders/preview/main_fs.glsl");
        int hProgram = loadShader(vss_default, fss_default);
        GLES20.glUseProgram(hProgram);
        uTexRotateMatrix = GLES20.glGetUniformLocation(hProgram, "uTexRotateMatrix");
        GLES20.glUniformMatrix4fv(uTexRotateMatrix, 1, false, mTexRotateMatrix, 0);
        vPosition = GLES20.glGetAttribLocation(hProgram, "vPosition");
        vTexCoord = GLES20.glGetAttribLocation(hProgram, "vTexCoord");
        enablePeak = GLES20.glGetUniformLocation(hProgram, "enablePeak");
        mirror = GLES20.glGetUniformLocation(hProgram, "mirror");
        GLES20.glVertexAttribPointer(vPosition, 2, GLES20.GL_FLOAT, false, 4 * 2, pVertex);
        GLES20.glVertexAttribPointer(vTexCoord, 2, GLES20.GL_FLOAT, false, 4 * 2, pTexCoord);
        GLES20.glEnableVertexAttribArray(vPosition);
        GLES20.glEnableVertexAttribArray(vTexCoord);
        GLES20.glUniform2f(GLES20.glGetUniformLocation(hProgram, "resolution"), mView.getWidth(), mView.getHeight());
        mGLInit = true;
        mView.fireOnSurfaceTextureAvailable(mSTexture, 0, 0);
    }

    public void onSurfaceChanged(GL10 unused, int width, int height) {
        GLES30.glViewport(0, 0, width, height);
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
