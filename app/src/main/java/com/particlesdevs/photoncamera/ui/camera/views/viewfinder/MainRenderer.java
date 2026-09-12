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
        GLES20.glUniformMatrix4fv(uTexRotateMatrix, 1, false, mTexRotateMatrix, 0);
        int peakEnabled = getPeakEnabled();
        GLES20.glUniform1i(enablePeak, peakEnabled);
        GLES20.glUniform1i(mirror, mMirrorPreview ? 1 : 0);

        GLES20.glVertexAttribPointer(vPosition, 2, GLES20.GL_FLOAT, false, 4 * 2, pVertex);
        GLES20.glVertexAttribPointer(vTexCoord, 2, GLES20.GL_FLOAT, false, 4 * 2, pTexCoord);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        // GLES20.glFlush();
    }

    private int uTexRotateMatrix;
    private int vPosition;
    private int vTexCoord;
    private int enablePeak;
    private int mirror;

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
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