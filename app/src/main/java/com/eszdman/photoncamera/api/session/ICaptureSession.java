package com.eszdman.photoncamera.api.session;

import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CaptureRequest;
import android.os.Handler;
import android.view.Surface;

import java.util.ArrayList;
import java.util.List;

public interface ICaptureSession {

    void createCaptureSession(List<Surface> var1, Handler var3);

    void setRepeatingRequest(CaptureRequest mPreviewRequest, CameraCaptureSession.CaptureCallback mCaptureCallback, Handler mBackgroundHandler);

    void capture(CaptureRequest build, CameraCaptureSession.CaptureCallback mCaptureCallback, Handler mBackgroundHandler);

    void stopRepeating();

    void captureBurst(ArrayList<CaptureRequest> captures, CameraCaptureSession.CaptureCallback captureCallback, Handler o);

    void setRepeatingBurst(ArrayList<CaptureRequest> captures, CameraCaptureSession.CaptureCallback captureCallback, Handler o);

    interface CaptureSessionEvents
    {
        void onConfigured();
        void onConfiguredFailed();
    }

    void abortCaptures();
    void setCaptureSessionEventListner(CaptureSessionEvents captureSessionEventListner);
    void close();
}
