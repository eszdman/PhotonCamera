package com.eszdman.photoncamera.api;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
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
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;
import android.util.Range;
import android.util.Rational;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.eszdman.photoncamera.AutoFitTextureView;
import com.eszdman.photoncamera.Parameters.ExposureIndex;
import com.eszdman.photoncamera.Parameters.FrameNumberSelector;
import com.eszdman.photoncamera.Parameters.IsoExpoSelector;
import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.api.camera.CameraImpl;
import com.eszdman.photoncamera.api.camera.ICamera;
import com.eszdman.photoncamera.api.capture.AbstractImageCapture;
import com.eszdman.photoncamera.api.capture.ImageSaverCapture;
import com.eszdman.photoncamera.api.session.CaptureSessionImpl;
import com.eszdman.photoncamera.api.session.ICaptureSession;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class CameraController implements ICamera.CameraEvents, ICaptureSession.CaptureSessionEvents
{
    private AutoFitTextureView mTextureView;

    public interface ControllerEvents
    {
        void onCreateOutPutError(int msg);
        void configureTransform(int width, int height, int pwidth, int pheight);
        void updateScreenLog(CaptureResult result,int width,int height);
        void showToast(String msg);
        void updateTextureViewOrientation(int width, int height);
        void updateTouchtoFocus();
    }

    private CameraController()
    {
        iCamera = new CameraImpl();
        iCamera.setCameraEventsListner(this);
        iCaptureSession = new CaptureSessionImpl(iCamera);
        iCaptureSession.setCaptureSessionEventListner(this);
        imageSaver = new ImageSaver();
    }

    private static CameraController cameraController = new CameraController();

    public static CameraController GET()
    {
        return cameraController;
    }

    private ControllerEvents eventsListner;

    public void setEventsListner(ControllerEvents eventsListner)
    {
        this.eventsListner = eventsListner;
    }

    public void setTextureView(AutoFitTextureView textureView)
    {
        this.mTextureView = textureView;
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

    /**
     * Max preview width that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_WIDTH = 1920;

    /**
     * Max preview height that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_HEIGHT = 1080;

    public static CameraCharacteristics mCameraCharacteristics;
    public static CaptureResult mCaptureResult;
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

    //public CameraCaptureSession mCaptureSession;

    private ICamera iCamera;
    protected ICaptureSession iCaptureSession;
    /**
     * The {@link android.util.Size} of camera preview.
     */
    private Size mPreviewSize;
    private Size target;

    /*An additional thread for running tasks that shouldn't block the UI.*/
    private HandlerThread mBackgroundThread;
    /*A {@link Handler} for running tasks in the background.*/
    public Handler mBackgroundHandler;
    /*An {@link ImageReader} that handles still image capture.*/
    private ImageSaverCapture yuvImageCapture;
    private ImageSaverCapture rawImageCapture;

    /**
     * This a callback object for the {@link ImageReader}. "onImageAvailable" will be called when a
     * still image is ready to be saved.
     */
    private ImageSaver imageSaver;

    /*{@link CaptureRequest.Builder} for the camera preview*/
    public CaptureRequest.Builder mPreviewRequestBuilder;
    public CaptureRequest mPreviewRequest;
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

    private boolean burst = false;

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

    ArrayList<CaptureRequest> captures;
    CameraCaptureSession.CaptureCallback CaptureCallback;

    public int getPreviewWidth()
    {
        return mPreviewSize.getWidth();
    }

    public int getPreviewHeight()
    {
        return mPreviewSize.getHeight();
    }

    public void onResume()
    {
        startBackgroundThread();
        iCamera.onResume();
    }

    public void onPause()
    {
        closeCamera();
        stopBackgroundThread();
        iCamera.onPause();
    }

    /**
     * Given {@code choices} of {@code Size}s supported by a camera, choose the smallest one that
     * is at least as large as the respective texture view size, and that is at most as large as the
     * respective max size, and whose aspect ratio matches with the specified value. If such size
     * doesn't exist, choose the largest one that is at most as large as the respective max size,
     * and whose aspect ratio matches with the specified value.
     *
     * @param choices           The list of sizes that the camera supports for the intended output
     *                          class
     * @param textureViewWidth  The width of the texture view relative to sensor coordinate
     * @param textureViewHeight The height of the texture view relative to sensor coordinate
     * @param maxWidth          The maximum width that can be chosen
     * @param maxHeight         The maximum height that can be chosen
     * @param aspectRatio       The aspect ratio
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    private static Size chooseOptimalSize(Size[] choices, int textureViewWidth,
                                          int textureViewHeight, int maxWidth, int maxHeight, Size aspectRatio) {

        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
                    option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= textureViewWidth &&
                        option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    private Size getCameraOutputSize(Size[] in) {
        Arrays.sort(in, new CompareSizesByArea());
        List<Size> sizes = new ArrayList<>(Arrays.asList(in));
        int s = sizes.size() - 1;
        if (sizes.get(s).getWidth() * sizes.get(s).getHeight() <= 40 * 1000000) {
            target = sizes.get(s);
            return target;
        }
        else {
            if(sizes.size()>1) {
                target = sizes.get(s - 1);
                return target;
            }
        }
        return null;
    }

    private void mul(Rect in, double k) {
        in.bottom *= k;
        in.left *= k;
        in.right *= k;
        in.top *= k;
    }

    private Size getCameraOutputSize(Size[] in, Size mPreviewSize) {
        if(in == null) return mPreviewSize;
        Arrays.sort(in, new CompareSizesByArea());
        List<Size> sizes = new ArrayList<>(Arrays.asList(in));
        int s = sizes.size() - 1;
        if (sizes.get(s).getWidth() * sizes.get(s).getHeight() <= 40 * 1000000 || Interface.getSettings().QuadBayer){
            target = sizes.get(s);
            if(Interface.getSettings().QuadBayer) {
                Rect pre = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE);
                if(pre == null) return target;
                Rect act = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
                if(act == null) return target;
                double k = (double) (target.getHeight()) / act.bottom;
                mul(pre, k);
                mul(act, k);
                CameraReflectionApi.set(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE, act);
                CameraReflectionApi.set(CameraCharacteristics.SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE, pre);
            }
            return target;
        }
        else {
            if(sizes.size()> 1 ) {
                target = sizes.get(s - 1);
                return target;
            }
        }
        return mPreviewSize;
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
        //Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);

        StreamConfigurationMap map = null;
        if (mCameraCharacteristics != null) {
            map = mCameraCharacteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        }
        if (map == null) {
            return;
        }
        Size preview = getCameraOutputSize(map.getOutputSizes(mPreviewTargetFormat));
        Size target = getCameraOutputSize(map.getOutputSizes(mTargetFormat),preview);
        int maxjpg = 3;
        if(mTargetFormat == mPreviewTargetFormat) maxjpg = Interface.getSettings().frameCount+3;
        yuvImageCapture = new ImageSaverCapture(preview.getWidth(),preview.getHeight(),mPreviewTargetFormat, maxjpg, imageSaver);
        rawImageCapture = new ImageSaverCapture(target.getWidth(), target.getHeight(),
                mTargetFormat, Interface.getSettings().frameCount + 3, imageSaver);
        // Find out if we need to swap dimension to get the preview size relative to sensor
        // coordinate.
        int displayRotation = Interface.getGravity().getRotation();
        //int displayRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        //noinspection ConstantConditions
        mSensorOrientation = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
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
        boolean swappedDimensions = false;
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

        mCameraAfModes = mCameraCharacteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
        Point displaySize = new Point();
        Interface.getMainActivity().getWindowManager().getDefaultDisplay().getSize(displaySize);
        int rotatedPreviewWidth = mPreviewSize.getWidth();
        int rotatedPreviewHeight = mPreviewSize.getHeight();
        int maxPreviewWidth = displaySize.x;
        int maxPreviewHeight = displaySize.y;

        if (swappedDimensions) {
            rotatedPreviewWidth =  mPreviewSize.getHeight();
            rotatedPreviewHeight = mPreviewSize.getWidth();;
            //noinspection SuspiciousNameCombination
            maxPreviewWidth = displaySize.y;
            //noinspection SuspiciousNameCombination
            maxPreviewHeight = displaySize.x;
        }

        if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
            maxPreviewWidth = MAX_PREVIEW_WIDTH;
        }

        if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
            maxPreviewHeight = MAX_PREVIEW_HEIGHT;
        }

        // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
        // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
        // garbage capture data.
        mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth,
                maxPreviewHeight, target);
        Log.v(TAG,"Optimal PreviewSize: " + mPreviewSize.toString());

        if (eventsListner != null)
            eventsListner.updateTextureViewOrientation(mPreviewSize.getWidth(), mPreviewSize.getHeight());


        // Check if the flash is supported.
        Boolean available = mCameraCharacteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
        mFlashSupported = available == null ? false : available;
        Interface.getCameraUI().onCameraInitialization();
    }

    @SuppressLint("MissingPermission")
    public void restartCamera() {
        closeCamera();
        openCamera(mTextureView.getWidth(),mTextureView.getHeight());
    }

    /**
     * Opens the camera
     */
    protected void openCamera(int width, int height) {
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
    public void closeCamera() {
        Log.v(TAG, "Close Camera");
        iCaptureSession.close();
        iCamera.closeCamera();
        if (yuvImageCapture != null)
        {
            yuvImageCapture.close();
            rawImageCapture.close();
            yuvImageCapture = null;
            rawImageCapture = null;
        }
        mState = STATE_CLOSED;
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    public void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
        //mBackgroundHandler.post(imageSaver);
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
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

    /**
     * Creates a new {@link CameraCaptureSession} for camera preview.
     */
    @SuppressLint("LongLogTag")
    protected void createCameraPreviewSession() {
        Log.v(TAG, "createCameraPreviewSession");
        if (mPreviewSize == null)
            return;
        try {
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;
            // We configure the size of default buffer to be the size of camera preview we want.
            Log.d("createCameraPreviewSession() mTextureView", "" + mTextureView);
            Log.d("createCameraPreviewSession() Texture", "" + texture);
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

            // This is the output Surface we need to start preview.
            Surface surface = new Surface(texture);
            // We set up a CaptureRequest.Builder with the output Surface.
            mPreviewRequestBuilder
                    = iCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(surface);

            // Here, we create a CameraCaptureSession for camera preview.
            List<Surface> surfaces = Arrays.asList(surface, yuvImageCapture.getSurface());
            if(burst){
                surfaces = Arrays.asList(yuvImageCapture.getSurface(),rawImageCapture.getSurface());
            }
            if(mTargetFormat == mPreviewTargetFormat){
                surfaces = Arrays.asList(surface, yuvImageCapture.getSurface());
            }
            iCaptureSession.createCaptureSession(surfaces, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void rebuildPreviewBuilder(){
        iCaptureSession.setRepeatingRequest(mPreviewRequest,
                mCaptureCallback, mBackgroundHandler);
    }


    public void rebuildPreviewBuilderOneShot(){
        iCaptureSession.capture(mPreviewRequestBuilder.build(),
                mCaptureCallback, mBackgroundHandler);
    }

    /**
     * Initiate a still image capture.
     */
    protected void takePicture() {
        if (mCameraAfModes.length > 1) lockFocus();
        else {
            mState = STATE_WAITING_NON_PRECAPTURE;
            iCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
        }
    }

    /**
     * Lock the focus as the first step for a still image capture.
     */
    private void lockFocus() {
        startTimerLocked();
        // This is how to tell the camera to lock focus.
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                CameraMetadata.CONTROL_AF_TRIGGER_START);
        // Tell #mCaptureCallback to wait for the lock.
        mState = STATE_WAITING_LOCK;
        iCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                mBackgroundHandler);
    }

    /**
     * Run the precapture sequence for capturing a still image. This method should be called when
     * we get a response in {@link #mCaptureCallback} from {@link #lockFocus()}.
     */
    private void runPrecaptureSequence() {
        // This is how to tell the camera to trigger.
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
        // Tell #mCaptureCallback to wait for the precapture sequence to be set.
        mState = STATE_WAITING_PRECAPTURE;
        iCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                mBackgroundHandler);
    }


    private void captureStillPicture() {

        // This is the CaptureRequest.Builder that we use to take a picture.
        final CaptureRequest.Builder captureBuilder =
                iCamera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
        iCaptureSession.stopRepeating();
        if(mTargetFormat != mPreviewTargetFormat) captureBuilder.addTarget(rawImageCapture.getSurface());
        else captureBuilder.addTarget(yuvImageCapture.getSurface());
        Interface.getSettings().applyRes(captureBuilder);
        //captureBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);
        //captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_OFF);
        Log.d(TAG,"Focus:"+mFocus);
        //captureBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE,mFocus);
        captureBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_CANCEL);
        captureBuilder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON);
        for(int i =0; i<3;i++){
            Log.d(TAG,"Temperature:"+mPreviewTemp[i]);
        }
        Log.d(TAG,"CaptureBuilderStarted!");
        //setAutoFlash(captureBuilder);
        //int rotation = Interface.getGravity().getCameraRotation();//activity.getWindowManager().getDefaultDisplay().getRotation();
        captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, Interface.getGravity().getCameraRotation());
        captures = new ArrayList<>();
        FrameNumberSelector.getFrames();
        Interface.getCameraUI().lightcycle.setMax(FrameNumberSelector.frameCount);
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
        final int[] burstcount = {0, 0, FrameNumberSelector.frameCount};
        Log.d(TAG,"CaptureStarted!");
        Interface.getCameraUI().lightcycle.setAlpha(1.0f);
        mTextureView.setAlpha(0.5f);
        MediaPlayer burstPlayer = MediaPlayer.create(Interface.getMainActivity(),R.raw.sound_burst);
        CaptureCallback
                = new CameraCaptureSession.CaptureCallback() {
            @Override
            public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                           @NonNull CaptureRequest request,
                                           @NonNull TotalCaptureResult result) {
                Interface.getCameraUI().lightcycle.setProgress(Interface.getCameraUI().lightcycle.getProgress() + 1);
                burstPlayer.start();
                Log.v(TAG,"Completed!");
                mCaptureResult = result;
            }

            @Override
            public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
                burstPlayer.seekTo(0);
                Log.v(TAG,"FrameCaptureStarted! FrameNumber:"+frameNumber);
                super.onCaptureStarted(session, request, timestamp, frameNumber);
            }

            @Override
            public void onCaptureSequenceCompleted(@NonNull CameraCaptureSession session, int sequenceId, long frameNumber) {
                Log.d(TAG,"SequenceCompleted");
                try {
                    Interface.getCameraUI().lightcycle.setAlpha(0f);
                    Interface.getCameraUI().lightcycle.setProgress(0);
                    mTextureView.setAlpha(1f);
                } catch (Exception e){
                    e.printStackTrace();
                }
                //unlockFocus();
                createCameraPreviewSession();
                super.onCaptureSequenceCompleted(session, sequenceId, frameNumber);
            }

            @Override
            public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
                burstcount[1]++;
                if(Interface.getSettings().selectedMode != Settings.CameraMode.UNLIMITED)
                    if (burstcount[1] >= burstcount[2] + 1 || ImageSaver.imageBuffer.size() >= burstcount[2]) {
                        iCaptureSession.abortCaptures();
                        Interface.getCameraUI().lightcycle.setAlpha(0f);
                        Interface.getCameraUI().lightcycle.setProgress(0);
                        mTextureView.setAlpha(1f);
                        createCameraPreviewSession();
                    }
                super.onCaptureProgressed(session, request, partialResult);
            }
        };

        //mCaptureSession.setRepeatingBurst(captures, CaptureCallback, null);
        burst = true;
        createCameraPreviewSession();
        //mCaptureSession.captureBurst(captures, CaptureCallback, null);
    }

    /**
     * Unlock the focus. This method should be called when still image capture sequence is
     * finished.
     */
    private void unlockFocus() {
        // Reset the auto-focus trigger
        //mCaptureSession.stopRepeating();
        CameraReflectionApi.set(mPreviewRequest,CaptureRequest.CONTROL_AF_TRIGGER,CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
        setAutoFlash();
        //mCaptureSession.capture(mPreviewRequest, mCaptureCallback,
        //        mBackgroundHandler);
        // After this, the camera will go back to the normal state of preview.
        mState = STATE_PREVIEW;
        rebuildPreviewBuilder();
        //mCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback,
        //        mBackgroundHandler);
    }

    public void setAutoFlash() {
        if (mFlashSupported) {
            if (mFlashEnabled)
                CameraReflectionApi.set(mPreviewRequest,CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
        }
    }

    @Override
    public void onCameraOpen() {
        createCameraPreviewSession();
    }

    @Override
    public void onCameraClose() {

    }

    @Override
    public void onConfigured() {
        try {
            // Auto focus should be continuous for camera preview.
            //mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            // Flash is automatically enabled when necessary.
            setAutoFlash();
            Interface.getSettings().applyPrev(mPreviewRequestBuilder);

            //lightcycle.setVisibility(View.INVISIBLE);
            // Finally, we start displaying the camera preview.
            if (is30Fps) {
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                        FpsRangeDef);
            } else {
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                        FpsRangeHigh);
            }
            mPreviewRequest = mPreviewRequestBuilder.build();

            //CameraReflectionApi.set(mPreviewRequest,CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_OFF);
            if (!burst) {
                iCaptureSession.setRepeatingRequest(mPreviewRequest,
                        mCaptureCallback, mBackgroundHandler);
                unlockFocus();
            } else {
                Log.d(TAG,"Preview, captureBurst");
                if(Interface.getSettings().selectedMode != Settings.CameraMode.UNLIMITED) iCaptureSession.captureBurst(captures, CaptureCallback, null);
                else iCaptureSession.setRepeatingBurst(captures, CaptureCallback, null);
                burst = false;
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

    public ICaptureSession getiCaptureSession()
    {
        return iCaptureSession;
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
                        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,FpsRangeDef);
                        mPreviewRequest = mPreviewRequestBuilder.build();
                        rebuildPreviewBuilder();
                        is30Fps = true;
                    }
                }
                if(ExposureIndex.index()+0.9 < 8.0) {
                    if(is30Fps && Interface.getSettings().fpsPreview && !iCamera.getId().equals("1"))
                    {
                        Log.d(TAG,"Changed preview target 60fps");
                        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,FpsRangeHigh);
                        mPreviewRequest = mPreviewRequestBuilder.build();
                        rebuildPreviewBuilder();
                        is30Fps = false;
                    }

                }
            }
        }
    };
}
