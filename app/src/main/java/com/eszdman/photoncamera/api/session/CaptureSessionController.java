package com.eszdman.photoncamera.api.session;

import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.MeteringRectangle;
import android.os.Handler;
import android.util.Range;
import android.view.Surface;
import com.eszdman.photoncamera.api.camera.ICamera;
import java.util.ArrayList;
import java.util.List;

public class CaptureSessionController
{
    private List<Surface> surfaceList;
    public CaptureRequest.Builder mPreviewRequestBuilder;
    private ICaptureSession captureSession;
    private ICamera iCamera;
    private List<Surface> previewSurface;
    CameraCaptureSession.CaptureCallback captureCallback;
    private Handler backgroundHandler;

    public CaptureSessionController(ICaptureSession captureSession, ICamera camera, CameraCaptureSession.CaptureCallback captureCallback)
    {
        surfaceList = new ArrayList<>();
        previewSurface = new ArrayList<>();
        this.captureSession = captureSession;
        this.iCamera = camera;
        this.captureCallback = captureCallback;
    }

    public void clear()
    {
        surfaceList.clear();
        previewSurface.clear();
    }

    public void setBackgroundHandler(Handler handler)
    {
        this.backgroundHandler = handler;
    }

    public CaptureSessionController addSurface(Surface surface, boolean forPreview)
    {
        if (!surfaceList.contains(surface)) {
            surfaceList.add(surface);
            if (forPreview && !previewSurface.contains(surface))
                previewSurface.add(surface);
        }
        return this;
    }

    public void createCaptureSession(Handler handler)
    {
        mPreviewRequestBuilder
                = iCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        for (Surface s : previewSurface)
            mPreviewRequestBuilder.addTarget(s);

        captureSession.createCaptureSession(surfaceList, handler);
    }

    public void createZslCaptureSession(Handler handler, int width, int height)
    {
        mPreviewRequestBuilder
                = iCamera.createCaptureRequest(CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG);
        for (Surface s : previewSurface)
            mPreviewRequestBuilder.addTarget(s);

        captureSession.createZslCaptureSession(surfaceList, handler,width,height);
    }

    public CaptureRequest.Builder getPreviewRequestBuilder()
    {
        return mPreviewRequestBuilder;
    }

    public <T> CaptureSessionController set( CaptureRequest.Key<T> key, T value)
    {
        mPreviewRequestBuilder.set(key,value);
        return this;
    }

    public CaptureSessionController setFocusTriggerTo(int val)
    {
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, val);
        return this;
    }

    public CaptureSessionController setAeTriggerTo(int val)
    {
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                val);
        return this;
    }

    public CaptureSessionController setAeMode(int val)
    {
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,val);
        return this;
    }

    public CaptureSessionController setAfMode(int val)
    {
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,val);
        return this;
    }

    public CaptureSessionController setFpsRange(Range range)
    {
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                range);
        return this;
    }

    public CaptureSessionController setAeRegion(MeteringRectangle[] region)
    {
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_REGIONS, region);
        return this;
    }

    public CaptureSessionController setAfRegion(MeteringRectangle[] region)
    {
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, region);
        return this;
    }

    public void applyRepeating()
    {
        captureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), captureCallback,backgroundHandler);
    }

    public void applyRepeatingBurst()
    {
        ArrayList<CaptureRequest> builders = new ArrayList<>();
        builders.add(mPreviewRequestBuilder.build());
        captureSession.setRepeatingBurst(builders, captureCallback,backgroundHandler);
    }

    public void applyOneShot()
    {
        captureSession.capture(mPreviewRequestBuilder.build(), captureCallback,backgroundHandler);
    }
}
