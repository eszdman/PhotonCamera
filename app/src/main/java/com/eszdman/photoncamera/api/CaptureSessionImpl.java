package com.eszdman.photoncamera.api;

import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CaptureRequest;
import android.os.Handler;
import android.view.Surface;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

public class CaptureSessionImpl implements ICaptureSession {

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
        iCamera.createCaptureSession(var1,sessionCreated,var3);
    }

    @Override
    public void setRepeatingRequest(CaptureRequest mPreviewRequest, CameraCaptureSession.CaptureCallback mCaptureCallback, Handler mBackgroundHandler) {
        try {
            mCaptureSession.setRepeatingRequest(mPreviewRequest,
                    mCaptureCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void capture(CaptureRequest build, CameraCaptureSession.CaptureCallback mCaptureCallback, Handler mBackgroundHandler) {
        try {
            mCaptureSession.capture(build,
                    mCaptureCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void stopRepeating() {
        try {
            mCaptureSession.stopRepeating();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void captureBurst(ArrayList<CaptureRequest> captures, CameraCaptureSession.CaptureCallback captureCallback, Handler o) {
        try {
            mCaptureSession.captureBurst(captures,captureCallback,o);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void setRepeatingBurst(ArrayList<CaptureRequest> captures, CameraCaptureSession.CaptureCallback captureCallback, Handler o) {
        try {
            mCaptureSession.setRepeatingBurst(captures,captureCallback,o);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void abortCaptures() {
        try {
            mCaptureSession.abortCaptures();
        } catch (CameraAccessException e) {
            e.printStackTrace();
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
            fireOnCofigured();
        }

        @Override
        public void onConfigureFailed(
                @NonNull CameraCaptureSession cameraCaptureSession) {
            fireOnCofiguredFailed();
        }
    };
}
