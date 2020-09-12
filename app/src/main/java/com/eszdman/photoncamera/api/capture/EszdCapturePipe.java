package com.eszdman.photoncamera.api.capture;

import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Handler;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import com.eszdman.photoncamera.AutoFitTextureView;
import com.eszdman.photoncamera.Parameters.FrameNumberSelector;
import com.eszdman.photoncamera.Parameters.IsoExpoSelector;
import com.eszdman.photoncamera.api.CameraController;
import com.eszdman.photoncamera.api.ImageCaptureResultCallback;
import com.eszdman.photoncamera.api.ImageSaver;
import com.eszdman.photoncamera.api.Interface;
import com.eszdman.photoncamera.api.Settings;
import com.eszdman.photoncamera.api.SizeUtils;
import com.eszdman.photoncamera.api.camera.ICamera;
import com.eszdman.photoncamera.api.session.CaptureSessionController;
import com.eszdman.photoncamera.api.session.ICaptureSession;

import java.util.ArrayList;


public class EszdCapturePipe extends CapturePipe {

    private final String TAG = EszdCapturePipe.class.getSimpleName();

    Size previewSize;
    Size captureSize;
    private int targetFormat;
    private int previewFormat;
    private ImageSaverCapture yuvImageCapture;
    private ImageSaverCapture rawImageCapture;
    private ImageSaver imageSaver;
    private ICamera iCamera;
    private ICaptureSession iCaptureSession;
    private ArrayList<CaptureRequest> captures;
    private ImageCaptureResultCallback imageCaptureResultCallback;

    public EszdCapturePipe(ImageSaver imageSaver, CaptureSessionController captureSessionController, ICamera iCamera, ICaptureSession iCaptureSession, ImageCaptureResultCallback imageCaptureResultCallback)
    {
        super(captureSessionController);
        this.imageSaver = imageSaver;
        this.iCamera = iCamera;
        this.iCaptureSession = iCaptureSession;
        this.imageCaptureResultCallback = imageCaptureResultCallback;
    }

    @Override
    public void createCameraPreviewSession(SurfaceTexture surfaceTexture, Handler mBackgroundHandler) {
        Log.v(TAG, "createCameraPreviewSession");
        if (previewSize == null)
            return;
        try {
            SurfaceTexture texture = surfaceTexture;
            assert texture != null;
            // We configure the size of default buffer to be the size of camera preview we want.
            Log.d("createCameraPreviewSession() Texture", "" + texture);
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());

            // This is the output Surface we need to start preview.
            Surface surface = new Surface(texture);
            // We set up a CaptureRequest.Builder with the output Surface.
            captureSessionController.clear();
            captureSessionController.addSurface(surface,true);

            // Here, we create a CameraCaptureSession for camera preview.
            /*if(burst){
                captureSessionController.addSurface(yuvImageCapture.getSurface(),false)
                        .addSurface(rawImageCapture.getSurface(),false);
            }
            if(mTargetFormat == mPreviewTargetFormat){
                captureSessionController.addSurface(yuvImageCapture.getSurface(),false);
            }*/
            setSurfaces();
            captureSessionController.createCaptureSession(mBackgroundHandler);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void findOutputSizes(CameraCharacteristics cameraCharacteristics, int targetFormat, int previewFormat) {
        StreamConfigurationMap map = null;
            map = cameraCharacteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        previewSize =  SizeUtils.getCameraOutputSize(map.getOutputSizes(previewFormat));
        captureSize = SizeUtils.getCameraOutputSize(map.getOutputSizes(targetFormat),previewSize,cameraCharacteristics);
        this.targetFormat = targetFormat;
        this.previewFormat = previewFormat;
    }

    @Override
    public void createImageReader(int maximages) {
        yuvImageCapture = new ImageSaverCapture(previewSize.getWidth(),previewSize.getHeight(),previewFormat, maximages, imageSaver);
        rawImageCapture = new ImageSaverCapture(captureSize.getWidth(), captureSize.getHeight(),
                targetFormat, Interface.getSettings().frameCount + 3, imageSaver);
        add(yuvImageCapture);
        add(rawImageCapture);
    }

    @Override
    public void close() {
        super.close();
        yuvImageCapture = null;
        rawImageCapture = null;
    }

    @Override
    public void setSurfaces() {
        captureSessionController.addSurface(yuvImageCapture.getSurface(),false);
        captureSessionController.addSurface(rawImageCapture.getSurface(),false);
    }

    @Override
    public void startCapture(Handler mBackgroundHandler) {
        if(Interface.getSettings().selectedMode != Settings.CameraMode.UNLIMITED)
            iCaptureSession.captureBurst(captures, imageCaptureResultCallback, mBackgroundHandler);
        else
            iCaptureSession.setRepeatingBurst(captures, imageCaptureResultCallback, mBackgroundHandler);
    }

    @Override
    public void captureStillPicture(int mTargetFormat, float mFocus, AutoFitTextureView surfaceTexture, Handler mBackgroundHandler, BurstCounter burstCounter) {

        // This is the CaptureRequest.Builder that we use to take a picture.
        final CaptureRequest.Builder captureBuilder =
                iCamera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
        iCaptureSession.stopRepeating();
        if(mTargetFormat != previewFormat) captureBuilder.addTarget(getAbstractImageCapture(1).getSurface());
        else captureBuilder.addTarget(getAbstractImageCapture(0).getSurface());
        Interface.getSettings().applyRes(captureBuilder);
        //captureBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);
        //captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_OFF);
        Log.d(TAG,"Focus:"+mFocus);
        //captureBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE,mFocus);
        captureBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_CANCEL);
        captureBuilder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON);

        Log.d(TAG,"CaptureBuilderStarted!");
        //setAutoFlash(captureBuilder);
        //int rotation = Interface.getGravity().getCameraRotation();//activity.getWindowManager().getDefaultDisplay().getRotation();
        captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, Interface.getGravity().getCameraRotation());
        captures = new ArrayList<>();
        FrameNumberSelector.getFrames();
        IsoExpoSelector.HDR = false;//Force HDR for tests
        captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_OFF);
        captureBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE,mFocus);
        IsoExpoSelector.useTripod = Interface.getSensors().getShakeness() < 5;
        for (int i = 0; i < FrameNumberSelector.frameCount; i++) {
            IsoExpoSelector.setExpo(captureBuilder, i);
            captures.add(captureBuilder.build());
        }
        if(FrameNumberSelector.frameCount == -1){
            IsoExpoSelector.setExpo(captureBuilder, 0);
            captures.add(captureBuilder.build());
        }
        //img
        Log.d(TAG,"FrameCount:"+FrameNumberSelector.frameCount);
        burstCounter.setBurst(true);
        burstCounter.setCurrent_burst(0);
        burstCounter.setMax_burst(FrameNumberSelector.frameCount);
        Log.d(TAG,"CaptureStarted!");
        surfaceTexture.setAlpha(0.5f);

        //mCaptureSession.setRepeatingBurst(captures, CaptureCallback, null);
        imageCaptureResultCallback.fireOnCaptureSquenceStarted(FrameNumberSelector.frameCount);
        createCameraPreviewSession(surfaceTexture.getSurfaceTexture(),mBackgroundHandler);
        //mCaptureSession.captureBurst(captures, CaptureCallback, null);
    }
}
