package com.eszdman.photoncamera.api.session;

import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CaptureRequest;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;

import com.eszdman.photoncamera.api.camera.ICamera;

import java.util.ArrayList;
import java.util.List;

public class CaptureSessionImpl implements ICaptureSession {

    private static final String TAG = CaptureSessionImpl.class.getSimpleName();
    private ICamera iCamera;
    /**
     * A {@link CameraCaptureSession } for camera preview.
     */
    private CameraCaptureSession mCaptureSession;

    private CaptureSessionEvents captureSessionEventsListner;

    public CaptureSessionImpl(ICamera iCamera)
    {
        this.iCamera = iCamera;
    }

    @Override
    public void createCaptureSession(List<Surface> var1, Handler var3) {
        Log.v(TAG,"createCaptureSession");
        iCamera.createCaptureSession(var1,sessionCreated,var3);
    }

    @Override
    public void createZslCaptureSession(List<Surface> var1, Handler var3,int width, int height) {
        Log.v(TAG,"createZslCaptureSession");
        iCamera.createReprocessCaptureSession(var1,sessionCreated,var3,width,height);
    }

    @Override
    public void setRepeatingRequest(CaptureRequest mPreviewRequest, CameraCaptureSession.CaptureCallback mCaptureCallback, Handler mBackgroundHandler) {
        Log.v(TAG,"setRepeatingRequest");
        try {
            mCaptureSession.setRepeatingRequest(mPreviewRequest,
                    mCaptureCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void capture(CaptureRequest build, CameraCaptureSession.CaptureCallback mCaptureCallback, Handler mBackgroundHandler) {
        Log.v(TAG,"capture");
        try {
            mCaptureSession.capture(build,
                    mCaptureCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void stopRepeating() {
        Log.v(TAG,"stopRepeating");
        try {
            mCaptureSession.stopRepeating();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void captureBurst(ArrayList<CaptureRequest> captures, CameraCaptureSession.CaptureCallback captureCallback, Handler o) {
        Log.v(TAG,"captureBurst");
        try {
            mCaptureSession.captureBurst(captures,captureCallback,o);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void setRepeatingBurst(ArrayList<CaptureRequest> captures, CameraCaptureSession.CaptureCallback captureCallback, Handler o) {
        Log.v(TAG,"setRepeatingBurst");
        try {
            mCaptureSession.setRepeatingBurst(captures,captureCallback,o);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void abortCaptures() {
        Log.v(TAG,"abortCaptures");
        try {
            mCaptureSession.abortCaptures();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        catch (IllegalStateException ex)
        {
            ex.printStackTrace();
        }
    }

    @Override
    public void setCaptureSessionEventListner(CaptureSessionEvents captureSessionEventListner) {
        this.captureSessionEventsListner = captureSessionEventListner;
    }

    @Override
    public void close() {
        if (mCaptureSession != null) {
            mCaptureSession.close();
            mCaptureSession = null;
        }
    }

    private void fireOnCofigured()
    {
        if (captureSessionEventsListner != null)
            captureSessionEventsListner.onConfigured();
    }

    private void fireOnCofiguredFailed()
    {
        if (captureSessionEventsListner != null)
            captureSessionEventsListner.onConfiguredFailed();
    }

    private CameraCaptureSession.StateCallback sessionCreated = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
            // When the session is ready, we start displaying the preview.
            mCaptureSession = cameraCaptureSession;
            Log.d(TAG, "Session is reprocessable:" +mCaptureSession.isReprocessable());
            fireOnCofigured();
        }

        @Override
        public void onConfigureFailed(
                @NonNull CameraCaptureSession cameraCaptureSession) {
            Log.v(TAG, "captureSession config failed");
            fireOnCofiguredFailed();
        }

        @Override
        public void onClosed(@NonNull CameraCaptureSession session) {
            super.onClosed(session);
            Log.v(TAG, "captureSession got closed");
        }
    };

    @Override
    public Surface getInputSurface() {
        return mCaptureSession.getInputSurface();
    }
}
