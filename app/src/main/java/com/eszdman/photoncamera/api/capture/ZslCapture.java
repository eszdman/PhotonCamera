package com.eszdman.photoncamera.api.capture;

import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageWriter;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import com.eszdman.photoncamera.AutoFitTextureView;
import com.eszdman.photoncamera.Parameters.FrameNumberSelector;
import com.eszdman.photoncamera.api.CameraController;
import com.eszdman.photoncamera.api.ImageCaptureResultCallback;
import com.eszdman.photoncamera.api.ImageSaver;
import com.eszdman.photoncamera.api.Interface;
import com.eszdman.photoncamera.api.SizeUtils;
import com.eszdman.photoncamera.api.camera.ICamera;
import com.eszdman.photoncamera.api.session.CaptureSessionController;
import com.eszdman.photoncamera.api.session.ICaptureSession;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class ZslCapture extends CapturePipe implements ForwardImageCapture.ImageEvents {

    private final String TAG = ZslCapture.class.getSimpleName();
    Size previewSize;
    Size captureSize;
    Size displaySize;
    private final int targetFormat = ImageFormat.RAW_SENSOR;
    private final int previewFormat = ImageFormat.YUV_420_888;
    private ForwardImageCapture privateImageCapture;
    private ImageSaverCapture yuvImageCapture;
    private ImageSaverCapture rawImageCapture;
    private ImageSaver imageSaver;
    private ImageWriter zslwriter;
    private ICamera iCamera;
    private ICaptureSession iCaptureSession;
    private ImageCaptureResultCallback imageCaptureResultCallback;
    private BlockingQueue<CaptureResult> captureResultBlockingQueue;
    private BlockingQueue<Image> imageBlockingQueue;
    private Surface surface;

    public ZslCapture(ImageSaver imageSaver, CaptureSessionController captureSessionController, ICamera iCamera, ICaptureSession iCaptureSession, ImageCaptureResultCallback imageCaptureResultCallback)
    {
        super(captureSessionController);
        this.imageSaver = imageSaver;
        this.iCamera = iCamera;
        this.iCaptureSession = iCaptureSession;
        this.imageCaptureResultCallback = imageCaptureResultCallback;
        imageBlockingQueue = new ArrayBlockingQueue<>(50);
        captureResultBlockingQueue = new ArrayBlockingQueue<>(50);
    }

    @Override
    public void createCaptureSession(SurfaceTexture surfaceTexture) {
        Log.v(TAG, "createCameraPreviewSession");
        findOutputSizes(CameraController.mCameraCharacteristics);
        if (previewSize == null)
            return;
        try {
            SurfaceTexture texture = surfaceTexture;
            assert texture != null;
            // We configure the size of default buffer to be the size of camera preview we want.
            Log.d("createCameraPreviewSession() Texture", "" + texture);
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());

            // This is the output Surface we need to start preview.
            surface = new Surface(texture);
            // We set up a CaptureRequest.Builder with the output Surface.
            captureSessionController.clear();
            captureSessionController.addSurface(surface,true);

            setSurfaces();
            captureSessionController.createZslCaptureSession(mBackgroundHandler,previewSize.getWidth(),previewSize.getHeight());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void captureStillPicture(float mFocus, AutoFitTextureView surfaceTexture) {
        //iCaptureSession.stopRepeating();
        FrameNumberSelector.getFrames();
        burstCounter.setMax_burst(FrameNumberSelector.frameCount);
        burstCounter.setCurrent_burst(0);
        CaptureResult result = null;
        Image image = null;
        for (int i = 0; i< burstCounter.getMax_burst(); i++) {
            try {
                result = captureResultBlockingQueue.take();
                image = imageBlockingQueue.take();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            CaptureRequest.Builder zsl = iCamera.createReprocessCaptureRequest((TotalCaptureResult) result);
            if (!Interface.getSettings().hdrx)
                zsl.addTarget(yuvImageCapture.getSurface());
            else
                zsl.addTarget(rawImageCapture.getSurface());
            zslwriter.queueInputImage(image);
            iCaptureSession.capture(zsl.build(), imageCaptureResultCallback, mBackgroundHandler);
        }
        //captureSessionController.applyRepeating();
    }

    @Override
    public void findOutputSizes(CameraCharacteristics cameraCharacteristics) {
        StreamConfigurationMap map = null;
        map = cameraCharacteristics.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        previewSize = SizeUtils.getCameraOutputSize(map.getOutputSizes(previewFormat));
        captureSize = SizeUtils.getCameraOutputSize(map.getOutputSizes(targetFormat),previewSize,cameraCharacteristics);
    }

    @Override
    public void createImageReader(int maxImages) {
        maxImages = 50;
        imageBlockingQueue = new ArrayBlockingQueue<>(maxImages);
        captureResultBlockingQueue = new ArrayBlockingQueue<>(maxImages);
        Size max = Collections.max(
                Arrays.asList(CameraController.mCameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.PRIVATE)),
        new CameraController.CompareSizesByArea());

        Size rawmax = Collections.max(
                Arrays.asList(CameraController.mCameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.YUV_420_888)),
        new CameraController.CompareSizesByArea());
        Rect rawmax2 = CameraController.mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        //Size rawmax2 = Collections.max(
        //        Arrays.asList(CameraController.mCameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.RAW_SENSOR)),

        //new CameraController.CompareSizesByArea());
        privateImageCapture = new ForwardImageCapture(max.getWidth(),max.getHeight(),ImageFormat.PRIVATE, maxImages, this);
        yuvImageCapture = new ImageSaverCapture(rawmax.getWidth(), rawmax.getHeight(),
               ImageFormat.YUV_420_888, maxImages, imageSaver);
        rawImageCapture = new ImageSaverCapture(rawmax2.right, rawmax2.bottom,
               ImageFormat.RAW_SENSOR, maxImages, imageSaver);
        add(privateImageCapture);
        add(yuvImageCapture);
        add(rawImageCapture);
    }

    @Override
    public void setSurfaces() {
        captureSessionController.addSurface(privateImageCapture.getSurface(),true);
        captureSessionController.addSurface(yuvImageCapture.getSurface(),false);
        captureSessionController.addSurface(rawImageCapture.getSurface(),false);
    }

    @Override
    public void startCapture() {

    }

    @Override
    public void close() {
        super.close();
        privateImageCapture = null;
        yuvImageCapture = null;
    }

    @Override
    public void setCaptureResult(TotalCaptureResult captureResult) {
        //Log.v(TAG, "setCaptureResult");
        if (captureResultBlockingQueue.remainingCapacity()== 1) {
            try {
                captureResultBlockingQueue.take();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        try {
            captureResultBlockingQueue.put(captureResult);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onImageAvailable(Image img) {
        //Log.v(TAG,"onImageAvailible");
        if (imageBlockingQueue.remainingCapacity() <= 2)
        {
            try {
                Image image = imageBlockingQueue.take();
                image.close();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        try {
            imageBlockingQueue.put(img);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onConfigured() {
        zslwriter = ImageWriter.newInstance(iCaptureSession.getInputSurface(),1);
        Interface.getSettings().applyPrev(captureSessionController.getPreviewRequestBuilder());
        captureSessionController.set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_ZERO_SHUTTER_LAG);
        captureSessionController.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_ZERO_SHUTTER_LAG);
        captureSessionController.setAeMode(CaptureRequest.CONTROL_AE_MODE_ON);
        captureSessionController.setAfMode(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        captureSessionController.applyRepeating();
    }

    @Override
    public void onConfiguredFailed() {

    }

    @Override
    public boolean runOnUiThread() {
        return false;
    }

    @Override
    public void onCaptureStarted() {

    }

    @Override
    public void onCaptureCompleted() {

    }

    @Override
    public void onCaptureSequenceStarted(int burstcount) {

    }

    @Override
    public void onCaptureSequenceCompleted() {

    }

    @Override
    public void onCaptureProgressed() {

    }
}
