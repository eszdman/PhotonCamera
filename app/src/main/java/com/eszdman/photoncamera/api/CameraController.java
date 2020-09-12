package com.eszdman.photoncamera.api;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.ColorSpaceTransform;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;
import android.util.Range;
import android.util.Rational;
import android.util.Size;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.eszdman.photoncamera.AutoFitTextureView;
import com.eszdman.photoncamera.Parameters.ExposureIndex;
import com.eszdman.photoncamera.Parameters.FrameNumberSelector;
import com.eszdman.photoncamera.Parameters.IsoExpoSelector;
import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.api.camera.CameraImpl;
import com.eszdman.photoncamera.api.camera.ICamera;
import com.eszdman.photoncamera.api.capture.BurstCounter;
import com.eszdman.photoncamera.api.capture.CapturePipe;
import com.eszdman.photoncamera.api.capture.EszdCapturePipe;
import com.eszdman.photoncamera.api.capture.ImageSaverCapture;
import com.eszdman.photoncamera.api.session.CaptureSessionController;
import com.eszdman.photoncamera.api.session.CaptureSessionImpl;
import com.eszdman.photoncamera.api.session.ICaptureSession;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class CameraController implements ICamera.CameraEvents, ICaptureSession.CaptureSessionEvents, ImageCaptureResultCallback.CaptureEvents, ICameraController
{
    private static CameraController cameraController = new CameraController();
    public static CameraController GET()
    {
        return cameraController;
    }

    public interface ControllerEvents
    {
        void onCreateOutPutError(int msg);
        void configureTransform(int width, int height, int pwidth, int pheight);
        void updateScreenLog(CaptureResult result,int width,int height);
        void showToast(String msg);
        void updateTextureViewOrientation(int width, int height);
        void updateTouchtoFocus();
    }

    private static final String TAG = CameraController.class.getSimpleName();

    /**
     * Camera state: Showing camera preview.
     */
    private static final int STATE_PREVIEW = 0;

    /**
     * Camera state: Waiting for the focus to be locked.
     */
    private static final int STATE_WAITING_LOCK = 1;

    /**
     * Camera state: Waiting for the exposure to be precapture state.
     */
    private static final int STATE_WAITING_PRECAPTURE = 2;

    /**
     * Camera state: Waiting for the exposure state to be something other than precapture.
     */
    private static final int STATE_WAITING_NON_PRECAPTURE = 3;

    /**
     * Camera state: Picture was taken.
     */
    private static final int STATE_PICTURE_TAKEN = 4;

    private static final int STATE_CLOSED = 5;



    public static CameraCharacteristics mCameraCharacteristics;
    public static final int rawFormat = ImageFormat.RAW_SENSOR;
    public static final int yuvFormat = ImageFormat.YUV_420_888;
    public static final int prevFormat = ImageFormat.YUV_420_888;
    public static int mTargetFormat = rawFormat;
    public static final int mPreviewTargetFormat = prevFormat;
    public static CaptureResult mPreviewResult;
    public long mPreviewExposuretime;
    public int mPreviewIso;
    public Rational[] mPreviewTemp;
    public ColorSpaceTransform mColorSpaceTransform;
    Range FpsRangeDef;
    Range FpsRangeHigh;
    private float mFocus;
    /**
     * ID of the current {@link CameraDevice}.
     */
    public String[] mCameraIds;
    private ICamera iCamera;
    protected ICaptureSession iCaptureSession;
    /**
     * The {@link android.util.Size} of camera preview.
     */
    private Size mPreviewSize;

    /*An additional thread for running tasks that shouldn't block the UI.*/
    private HandlerThread mBackgroundThread;
    /*A {@link Handler} for running tasks in the background.*/
    public Handler mBackgroundHandler;

    private CapturePipe capturePipe;

    /*{@link CaptureRequest.Builder} for the camera preview*/
    //public CaptureRequest.Builder mPreviewRequestBuilder;
    /**
     * The current state of camera state for taking pictures.
     */
    public int mState = STATE_PREVIEW;
    /**
     * Timer to use with pre-capture sequence to ensure a timely capture if 3A convergence is
     * taking too long.
     */
    private long mCaptureTimer;
    /**
     * Timeout for the pre-capture sequence.
     */
    private static final long PRECAPTURE_TIMEOUT_MS = 1000;

    /**
     * Whether the current camera device supports Flash or not.
     */
    private boolean mFlashSupported;
    public final boolean mFlashEnabled = false;
    /**
     * Orientation of the camera sensor
     */
    public int mSensorOrientation;
    int[] mCameraAfModes;
    public boolean is30Fps = true;
    private ImageCaptureResultCallback imageCaptureResultCallback = new ImageCaptureResultCallback();
    private AutoFitTextureView mTextureView;
    private ControllerEvents eventsListner;
    private CaptureSessionController captureSessionController;
    /**
     * This a callback object for the {@link ImageReader}. "onImageAvailable" will be called when a
     * still image is ready to be saved.
     */
    private ImageSaver imageSaver;
    private BurstCounter burstCounter;

    private CameraController()
    {
        iCamera = new CameraImpl();
        iCamera.setCameraEventsListner(this);
        iCaptureSession = new CaptureSessionImpl(iCamera);
        iCaptureSession.setCaptureSessionEventListner(this);
        imageSaver = new ImageSaver();
        captureSessionController = new CaptureSessionController(iCaptureSession,iCamera,mCaptureCallback);
        capturePipe = new EszdCapturePipe(imageSaver,captureSessionController,iCamera,iCaptureSession,imageCaptureResultCallback);
        burstCounter = new BurstCounter();
    }


    @Override
    public int getPreviewWidth()
    {
        return mPreviewSize.getWidth();
    }
    @Override
    public int getPreviewHeight()
    {
        return mPreviewSize.getHeight();
    }
    @Override
    public CaptureResult getCaptureResult()
    {
        return imageCaptureResultCallback.getResult();
    }
    @Override
    public void setEventsListner(ControllerEvents eventsListner)
    {
        this.eventsListner = eventsListner;
    }
    @Override
    public void setCaptureListner(ImageCaptureResultCallback.CaptureEvents captureListner)
    {
        imageCaptureResultCallback.addEventListner(captureListner);
    }
    @Override
    public void removeCaptureListner(ImageCaptureResultCallback.CaptureEvents captureListner)
    {
        imageCaptureResultCallback.removeEventListner(captureListner);
    }
    @Override
    public void setTextureView(AutoFitTextureView textureView)
    {
        this.mTextureView = textureView;
    }
    @Override
    public void onResume()
    {
        startBackgroundThread();
        setCaptureListner(this);
        iCamera.onResume();
        captureSessionController.setBackgroundHandler(mBackgroundHandler);
    }
    @Override
    public void onPause()
    {
        removeCaptureListner(this);
        closeCamera();
        captureSessionController.setBackgroundHandler(null);
        stopBackgroundThread();
        iCamera.onPause();
    }

    @SuppressLint("MissingPermission")
    @Override
    public void restartCamera() {
        closeCamera();
        openCamera(mTextureView.getWidth(),mTextureView.getHeight());
    }

    /**
     * Opens the camera
     */
    @Override
    public void openCamera(int width, int height) {
        Log.v(TAG,"openCamera WxH:" + width +"x" +height);
        if (ContextCompat.checkSelfPermission(Interface.getMainActivity(), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            //requestCameraPermission();
            return;
        }
        CameraManager manager = (CameraManager) Interface.getMainActivity().getSystemService(Context.CAMERA_SERVICE);
        CameraManager2 manager2 = new CameraManager2(manager);
        mCameraIds = manager2.getCameraIdList();
        if (mPreviewSize == null)
            mPreviewSize = new Size(mTextureView.getWidth(),mTextureView.getHeight());
        setUpCameraOutputs(manager);
        if (eventsListner != null)
            eventsListner.configureTransform(width,height, mPreviewSize.getWidth(), mPreviewSize.getHeight());

        iCamera.openCamera(Interface.getSettings().mCameraID);
    }

    /**
     * Closes the current {@link CameraDevice}.
     */
    @Override
    public void closeCamera() {
        Log.v(TAG, "Close Camera");
        iCaptureSession.close();
        iCamera.closeCamera();
        captureSessionController.clear();
        capturePipe.close();
        mState = STATE_CLOSED;
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    @Override
    public void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
        //mBackgroundHandler.post(imageSaver);
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    @Override
    public void stopBackgroundThread() {
        if(mBackgroundThread == null) return;
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }



    @Override
    public ICaptureSession getiCaptureSession()
    {
        return iCaptureSession;
    }

    @Override
    public CaptureSessionController getCaptureSession() {
        return captureSessionController;
    }

    @Override
    public void onCameraOpen() {
        createCameraPreviewSession();
    }

    @Override
    public void onCameraClose() {

    }

    @Override
    public boolean runOnUiThread() {
        return false;
    }

    @Override
    public void onCaptureStarted() {
        Log.v(TAG, "onCaptureStarted");
    }

    @Override
    public void onCaptureCompleted() {
        Log.v(TAG, "onCaptureCompleted");
    }

    @Override
    public void onCaptureSequenceStarted(int burstcount) {

    }

    @Override
    public void onCaptureSequenceCompleted() {
        Log.v(TAG, "onCaptureSequenceCompleted");
        mTextureView.setAlpha(1f);
        burstCounter.setBurst(false);
        createCameraPreviewSession();
    }

    @Override
    public void onCaptureProgressed() {
        burstCounter.increase();
        Log.v(TAG, "onCaptureProgressed " + burstCounter.getCurrent_burst() + "/" + FrameNumberSelector.frameCount);
        if(Interface.getSettings().selectedMode != Settings.CameraMode.UNLIMITED)
            if (burstCounter.getCurrent_burst() >= FrameNumberSelector.frameCount + 1 || ImageSaver.imageBuffer.size() >= FrameNumberSelector.frameCount) {
                iCaptureSession.abortCaptures();
                mTextureView.setAlpha(1f);
                Log.v(TAG, "startPreview");
                burstCounter.setBurst(false);
                createCameraPreviewSession();
            }
    }

    @Override
    public void onConfigured() {
        try {
            // Flash is automatically enabled when necessary.
            setAutoFlash();
            Interface.getSettings().applyPrev(captureSessionController.getPreviewRequestBuilder());
            // Finally, we start displaying the camera preview.
            if (is30Fps) {
                captureSessionController.setFpsRange(FpsRangeDef);
            } else {
                captureSessionController.setFpsRange(FpsRangeHigh);
            }

            //CameraReflectionApi.set(mPreviewRequest,CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_OFF);
            if (!burstCounter.isBurst()) {
                captureSessionController.applyRepeating();
                unlockFocus();
            } else {
                Log.d(TAG,"Preview, captureBurst");
                capturePipe.startCapture(mBackgroundHandler);
                burstCounter.setBurst(false);
            }
            if (eventsListner != null)
                eventsListner.updateTouchtoFocus();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onConfiguredFailed() {
        if (eventsListner != null)
            eventsListner.showToast("Failed");
    }

    /**
     * Sets up member variables related to camera.
     */
    private void setUpCameraOutputs(CameraManager cameraManager) {
        // manager2.CameraArr(manager);
        try {
            mCameraCharacteristics = cameraManager.getCameraCharacteristics(Interface.getSettings().mCameraID);
            UpdateCameraCharacteristics(Interface.getSettings().mCameraID);

            //Thread thr = new Thread(imageSaver);
            //thr.start();
            mBackgroundHandler.post(imageSaver);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            e.printStackTrace();
            if (eventsListner != null)
                eventsListner.onCreateOutPutError(R.string.camera_error);
        }
    }

    private void UpdateCameraCharacteristics(String cameraId) {
        Log.v(TAG,"UpdateCameraCharacteristics " + cameraId);
        capturePipe.findOutputSizes(mCameraCharacteristics,mTargetFormat,mPreviewTargetFormat);
        int maxjpg = 3;
        if(mTargetFormat == mPreviewTargetFormat) maxjpg = Interface.getSettings().frameCount+3;
        capturePipe.createImageReader(maxjpg);
        // Find out if we need to swap dimension to get the preview size relative to sensor
        // coordinate.
        int displayRotation = Interface.getGravity().getRotation();
        //int displayRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        //noinspection ConstantConditions

        findFpsRange();
        mCameraAfModes = mCameraCharacteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);

        boolean swappedDimensions = isSwappedDimensions(displayRotation);

        mPreviewSize = SizeUtils.findPreviewSize(mCameraCharacteristics.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP), new Size(mTextureView.getWidth(), mTextureView.getHeight()) ,mPreviewSize, swappedDimensions);
        Log.v(TAG,"Optimal PreviewSize: " + mPreviewSize.toString());

        if (eventsListner != null)
            eventsListner.updateTextureViewOrientation(mPreviewSize.getWidth(), mPreviewSize.getHeight());


        // Check if the flash is supported.
        Boolean available = mCameraCharacteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
        mFlashSupported = available == null ? false : available;
        Interface.getCameraUI().onCameraInitialization();
    }

    private boolean isSwappedDimensions(int displayRotation) {
        boolean swappedDimensions = false;
        mSensorOrientation = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        switch (displayRotation) {
            case 0:
            case 180:
                if (mSensorOrientation == 90 || mSensorOrientation == 270) {
                    swappedDimensions = true;
                }
                break;
            case 90:
            case 270:
                if (mSensorOrientation == 0 || mSensorOrientation == 180) {
                    swappedDimensions = true;
                }
                break;
            default:
                Log.e(TAG, "Display rotation is invalid: " + displayRotation);
        }
        return swappedDimensions;
    }

    private void findFpsRange() {
        Range[] ranges = mCameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
        int def = 30;
        int min = 20;
        if(ranges == null) {
            ranges = new Range[1];
            ranges[0] = new Range(15, 30);
        }
        for (Range value : ranges) {
            if ((int) value.getUpper() >= def) {
                FpsRangeDef = value;
                break;
            }
        }
        if(FpsRangeDef == null)
            for (Range range : ranges) {
                if ((int) range.getUpper() >= min) {
                    FpsRangeDef = range;
                    break;
                }
            }
        for (Range range : ranges) {
            if ((int) range.getUpper() > def) {
                FpsRangeDef = range;
                break;
            }
        }
        if(FpsRangeHigh == null) FpsRangeHigh = FpsRangeDef;
    }


    /**
     * Creates a new {@link CameraCaptureSession} for camera preview.
     */
    @SuppressLint("LongLogTag")
    protected void createCameraPreviewSession() {
        capturePipe.createCameraPreviewSession(mTextureView.getSurfaceTexture(),mBackgroundHandler);
    }


    /**
     * Initiate a still image capture.
     */
    protected void takePicture() {
        if (mCameraAfModes.length > 1) lockFocus();
        else {
            mState = STATE_WAITING_NON_PRECAPTURE;
            captureSessionController.applyOneShot();
        }
    }

    /**
     * Lock the focus as the first step for a still image capture.
     */
    private void lockFocus() {
        startTimerLocked();
        // This is how to tell the camera to lock focus.
        captureSessionController.setFocusTriggerTo(CameraMetadata.CONTROL_AF_TRIGGER_START).applyOneShot();

        // Tell #mCaptureCallback to wait for the lock.
        mState = STATE_WAITING_LOCK;
    }

    /**
     * Run the precapture sequence for capturing a still image. This method should be called when
     * we get a response in {@link #mCaptureCallback} from {@link #lockFocus()}.
     */
    private void runPrecaptureSequence() {
        // This is how to tell the camera to trigger.
        captureSessionController.setAeTriggerTo(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START).applyOneShot();
        // Tell #mCaptureCallback to wait for the precapture sequence to be set.
        mState = STATE_WAITING_PRECAPTURE;
    }


    private void captureStillPicture() {
        burstCounter.setBurst(true);
        burstCounter.setCurrent_burst(0);
        capturePipe.captureStillPicture(mTargetFormat,mFocus,mTextureView,mBackgroundHandler,burstCounter);

    }

    /**
     * Unlock the focus. This method should be called when still image capture sequence is
     * finished.
     */
    private void unlockFocus() {
        // Reset the auto-focus trigger
        //mCaptureSession.stopRepeating();
        captureSessionController.setFocusTriggerTo(CameraMetadata.CONTROL_AF_TRIGGER_CANCEL).applyOneShot();
        setAutoFlash();
        //mCaptureSession.capture(mPreviewRequest, mCaptureCallback,
        //        mBackgroundHandler);
        // After this, the camera will go back to the normal state of preview.
        mState = STATE_PREVIEW;
        //mCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback,
        //        mBackgroundHandler);
    }

    private void setAutoFlash() {
        if (mFlashSupported) {
            if (mFlashEnabled)
                captureSessionController.setAeMode(CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH).applyRepeating();
        }
    }



    /**
     * Compares two {@code Size}s based on their areas.
     */
    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }

    /**
     * Start the timer for the pre-capture sequence.
     * <p/>
     * Call this only with { #mCameraStateLock} held.
     */
    private void startTimerLocked() {
        mCaptureTimer = SystemClock.elapsedRealtime();
    }

    /**
     * Check if the timer for the pre-capture sequence has been hit.
     * <p/>
     * Call this only with { #mCameraStateLock} held.
     *
     * @return true if the timeout occurred.
     */
    private boolean hitTimeoutLocked() {
        return (SystemClock.elapsedRealtime() - mCaptureTimer) > PRECAPTURE_TIMEOUT_MS;
    }



    private final CameraCaptureSession.CaptureCallback mCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result) {
            switch (mState) {
                case STATE_PREVIEW: {
                    // We have nothing to do when the camera preview is working normally.
                    //Log.v(TAG, "PREVIEW");
                    break;
                }
                case STATE_WAITING_LOCK: {
                    //Log.v(TAG, "WAITING_LOCK");
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                    // If we haven't finished the pre-capture sequence but have hit our maximum
                    // wait timeout, too bad! Begin capture anyway.
                    if (hitTimeoutLocked()) {
                        Log.w(TAG, "Timed out waiting for pre-capture sequence to complete.");
                        mState = STATE_PICTURE_TAKEN;
                        mPreviewResult = result;
                        captureStillPicture();
                    }
                    if (afState == null) {
                        mState = STATE_PICTURE_TAKEN;
                        mPreviewResult = result;
                        captureStillPicture();
                    } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
                        // CONTROL_AE_STATE can be null on some devices
                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                        if (aeState == null ||
                                aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                            mState = STATE_PICTURE_TAKEN;
                            mPreviewResult = result;
                            captureStillPicture();
                        } else {
                            runPrecaptureSequence();
                        }
                    }
                    break;
                }
                //TODO Check why this wrong
                /*case STATE_WAITING_PRECAPTURE: {
                    Log.v(TAG, "WAITING_PRECAPTURE");
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null ||
                            aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                            aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        mState = STATE_WAITING_NON_PRECAPTURE;
                    }
                    break;
                }*/
                case STATE_WAITING_PRECAPTURE:
                case STATE_WAITING_NON_PRECAPTURE: {
                    //Log.v(TAG, "WAITING_NON_PRECAPTURE");
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        mState = STATE_PICTURE_TAKEN;
                        mPreviewResult = result;
                        captureStillPicture();
                    }
                    break;
                }
            }
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                        @NonNull CaptureRequest request,
                                        @NonNull CaptureResult partialResult) {

            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
            Object exposure = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
            Object iso = result.get(CaptureResult.SENSOR_SENSITIVITY);
            Object focus = result.get(CaptureResult.LENS_FOCUS_DISTANCE);
            Rational[] mtemp = result.get(CaptureResult.SENSOR_NEUTRAL_COLOR_POINT);
            if(exposure != null) mPreviewExposuretime = (long)exposure;
            if(iso != null) mPreviewIso = (int)iso;
            if(focus != null) mFocus = (float)focus;
            mPreviewTemp = mtemp;
            mColorSpaceTransform = result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM);
            process(result);
            if (eventsListner != null)
                eventsListner.updateScreenLog(result,mPreviewSize.getWidth(),mPreviewSize.getHeight());
        }
        //Automatic 60fps preview
        @Override
        public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
            super.onCaptureStarted(session, request, timestamp, frameNumber);
            if(frameNumber % 20 == 19){
                if(ExposureIndex.index() > 8.0){
                    if(!is30Fps) {
                        Log.d(TAG,"Changed preview target 30fps");
                        captureSessionController.setFpsRange(FpsRangeDef).applyRepeating();
                        is30Fps = true;
                    }
                }
                if(ExposureIndex.index()+0.9 < 8.0) {
                    if(is30Fps && Interface.getSettings().fpsPreview && !iCamera.getId().equals("1"))
                    {
                        Log.d(TAG,"Changed preview target 60fps");
                        captureSessionController.setFpsRange(FpsRangeHigh).applyRepeating();
                        is30Fps = false;
                    }

                }
            }
        }
    };
}
