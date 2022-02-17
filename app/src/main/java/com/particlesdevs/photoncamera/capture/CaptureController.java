package com.particlesdevs.photoncamera.capture;
/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
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
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.CamcorderProfile;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.util.Range;
import android.util.Rational;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Display;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.hunter.library.debug.HunterDebug;
import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.api.Camera2ApiAutoFix;
import com.particlesdevs.photoncamera.api.CameraEventsListener;
import com.particlesdevs.photoncamera.api.CameraManager2;
import com.particlesdevs.photoncamera.api.CameraMode;
import com.particlesdevs.photoncamera.api.CameraReflectionApi;
import com.particlesdevs.photoncamera.api.Settings;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.control.GyroBurst;
import com.particlesdevs.photoncamera.debugclient.DebugSender;
import com.particlesdevs.photoncamera.manual.ParamController;
import com.particlesdevs.photoncamera.processing.ImageSaver;
import com.particlesdevs.photoncamera.processing.parameters.ExposureIndex;
import com.particlesdevs.photoncamera.processing.parameters.FrameNumberSelector;
import com.particlesdevs.photoncamera.processing.parameters.IsoExpoSelector;
import com.particlesdevs.photoncamera.processing.parameters.ResolutionSolution;
import com.particlesdevs.photoncamera.settings.PreferenceKeys;
import com.particlesdevs.photoncamera.ui.camera.CameraFragment;
import com.particlesdevs.photoncamera.ui.camera.viewmodel.TimerFrameCountViewModel;
import com.particlesdevs.photoncamera.ui.camera.views.viewfinder.AutoFitPreviewView;
import com.particlesdevs.photoncamera.util.log.Logger;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static android.hardware.camera2.CameraMetadata.CONTROL_AE_MODE_ON;
import static android.hardware.camera2.CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE;
import static android.hardware.camera2.CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_VIDEO;
import static android.hardware.camera2.CameraMetadata.CONTROL_AF_TRIGGER_IDLE;
import static android.hardware.camera2.CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON;
import static android.hardware.camera2.CameraMetadata.FLASH_MODE_TORCH;
import static android.hardware.camera2.CaptureRequest.CONTROL_AE_MODE;
import static android.hardware.camera2.CaptureRequest.CONTROL_AE_REGIONS;
import static android.hardware.camera2.CaptureRequest.CONTROL_AF_MODE;
import static android.hardware.camera2.CaptureRequest.CONTROL_AF_REGIONS;
import static android.hardware.camera2.CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE;
import static android.hardware.camera2.CaptureRequest.FLASH_MODE;

/**
 * Class responsible for image capture and sending images for subsequent processing
 * <p>
 * All relevant events are notified to cameraEventsListener
 * <p>
 * Constructor {@link CaptureController#CaptureController(Activity, ExecutorService, CameraEventsListener)}
 */
public class CaptureController implements MediaRecorder.OnInfoListener {
    public static final int RAW_FORMAT = ImageFormat.RAW_SENSOR;
    public static final int YUV_FORMAT = ImageFormat.YUV_420_888;
    public static final int PREVIEW_FORMAT = ImageFormat.YUV_420_888;
    private static final String TAG = CaptureController.class.getSimpleName();
    public List<Future<?>> taskResults = new ArrayList<>();
    private final ExecutorService processExecutor;
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
    /**
     * Timeout for the pre-capture sequence.
     */
    private static final long PRECAPTURE_TIMEOUT_MS = 100;
    private static final int SENSOR_ORIENTATION_DEFAULT_DEGREES = 90;
    private static final int SENSOR_ORIENTATION_INVERSE_DEGREES = 270;
    /**
     * Conversion from screen rotation to JPEG orientation.
     */
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    private static final SparseIntArray DEFAULT_ORIENTATIONS = new SparseIntArray();
    private static final SparseIntArray INVERSE_ORIENTATIONS = new SparseIntArray();

    private Map<String, CameraCharacteristics> mCameraCharacteristicsMap = new HashMap<>();
    public static CameraCharacteristics mCameraCharacteristics;
    public static CaptureResult mCaptureResult;

    public static CaptureResult mPreviewCaptureResult;
    public static CaptureRequest mPreviewCaptureRequest;
    public static int mPreviewTargetFormat = PREVIEW_FORMAT;
    public boolean isDualSession = true;
    private static int mTargetFormat = RAW_FORMAT;
    private final ParamController paramController;

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    static {
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_0, 90);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_90, 0);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_180, 270);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    static {
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_0, 270);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_90, 180);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_180, 90);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_270, 0);
    }

    public final boolean mFlashEnabled = false;
    private CameraEventsListener cameraEventsListener;
    /**
     * A {@link Semaphore} to prevent the app from exiting before closing the camera.
     */
    private final Semaphore mCameraOpenCloseLock = new Semaphore(1);
    private CameraManager mCameraManager;
    private CameraManager2 mCameraManager2;
    private Activity activity;
    public long mPreviewExposureTime;
    /**
     * ID of the current {@link CameraDevice}.
     */
    public int mPreviewIso;
    public Rational[] mPreviewTemp;
    public ColorSpaceTransform mColorSpaceTransform;
    /**
     * A reference to the opened {@link CameraDevice}.
     */
    public CameraDevice mCameraDevice;
    /*A {@link Handler} for running tasks in the background.*/
    public Handler mBackgroundHandler;
    /*An {@link ImageReader} that handles still image capture.*/
    public ImageReader mImageReaderPreview;
    public ImageReader mImageReaderRaw;
    /*{@link CaptureRequest.Builder} for the camera preview*/
    public CaptureRequest.Builder mPreviewRequestBuilder;
    public CaptureRequest mPreviewInputRequest;
    /**
     * The current state of camera state for taking pictures.
     */
    public int mState = STATE_PREVIEW;
    /**
     * Orientation of the camera sensor
     */
    public int mSensorOrientation;
    public int cameraRotation;
    public boolean is30Fps = true;
    public boolean onUnlimited = false;
    public boolean unlimitedStarted = false;
    public boolean mFlashed = false;
    public ArrayList<GyroBurst> BurstShakiness;
    /**
     * This a callback object for the {@link ImageReader}. "onImageAvailable" will be called when a
     * still image is ready to be saved.
     */
    public ImageSaver mImageSaver;
    private final ImageReader.OnImageAvailableListener mOnYuvImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            //mImageSaver.mImage = reader.acquireNextImage();
            //mImageSaver.initProcess(reader);
//            Message msg = new Message();
//            msg.obj = reader;
//            mImageSaver.processingHandler.sendMessage(msg);
            processExecutor.execute(() -> mImageSaver.initProcess(reader));
        }
    };
    private final ImageReader.OnImageAvailableListener mOnRawImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            //dequeueAndSaveImage(mRawResultQueue, mRawImageReader);
            //mImageSaver.mImage = reader.acquireNextImage();
//            Message msg = new Message();
//            msg.obj = reader;
//            mImageSaver.processingHandler.sendMessage(msg);
            if (onUnlimited && !unlimitedStarted) {
                return;
            }

            //This code creates single frame bugs
            //taskResults.removeIf(Future::isDone); //remove already completed results
            //Future<?> result = processExecutor.submit(() -> mImageSaver.initProcess(reader));
            //taskResults.add(result);
            AsyncTask.execute(() -> {
                mImageSaver.initProcess(reader);
            });
        }

    };
    private Range<Integer> FpsRangeDef;
    private Range<Integer> FpsRangeHigh;
    private int[] mCameraAfModes;
    private int mPreviewWidth;
    private int mPreviewHeight;
    private ArrayList<CaptureRequest> captures;
    private CameraCaptureSession.CaptureCallback CaptureCallback;
    private File vid = null;
    public int mMeasuredFrameCnt;
    public static boolean isProcessing;
    /**
     * An {@link AutoFitPreviewView} for camera preview.
     */
    private AutoFitPreviewView mTextureView;
    /**
     * A {@link CameraCaptureSession } for camera preview.
     */
    private CameraCaptureSession mCaptureSession;
    /**
     * MediaRecorder
     */
    private MediaRecorder mMediaRecorder;
    /**
     * Whether the app is recording video now
     */
    public boolean mIsRecordingVideo;
    private Size target;
    private float mFocus;
    public int mPreviewAFMode;
    public int mPreviewAEMode;
    public MeteringRectangle[] mPreviewMeteringAF;
    public MeteringRectangle[] mPreviewMeteringAE;
    /**
     * The {@link Size} of camera preview.
     */
    public Size mPreviewSize;
    /*An additional thread for running tasks that shouldn't block the UI.*/
    private HandlerThread mBackgroundThread;
    /**
     * Timer to use with pre-capture sequence to ensure a timely capture if 3A convergence is
     * taking too long.
     */
    private long mCaptureTimer;
    /**
     * Whether the current camera device supports Flash or not.
     */
    private boolean mFlashSupported;
    /**
     * Creates a new {@link CameraCaptureSession} for camera preview.
     */
    public static boolean burst = false;
    /**
     * A {@link CameraCaptureSession.CaptureCallback} that handles events related to JPEG capture.
     */
    public ProcessCallbacks debugCallback = new ProcessCallbacks();
    private final CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result) {
            debugCallback.process();
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
                        captureStillPicture();
                    }
                    if (afState == null) {
                        mState = STATE_PICTURE_TAKEN;
                        captureStillPicture();
                    } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
                        // CONTROL_AE_STATE can be null on some devices
                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                        if (aeState == null ||
                                aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                            mState = STATE_PICTURE_TAKEN;
                            captureStillPicture();
                        } else {
                            runPreCaptureSequence();
                        }
                    }
                    break;
                }
                case STATE_WAITING_PRECAPTURE: {
                    Log.v(TAG, "WAITING_PRECAPTURE");
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null ||
                            aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                            aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        mState = STATE_WAITING_NON_PRECAPTURE;
                    }
                    if (paramController.isManualMode())
                        mState = STATE_WAITING_NON_PRECAPTURE;
                    break;
                }
                case STATE_WAITING_NON_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        mState = STATE_PICTURE_TAKEN;
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
            Rational[] mTemp = result.get(CaptureResult.SENSOR_NEUTRAL_COLOR_POINT);
            if (exposure != null) mPreviewExposureTime = (long) exposure;
            if (iso != null) mPreviewIso = (int) iso;
            if (focus != null) mFocus = (float) focus;
            mPreviewTemp = mTemp;
            if (mTemp != null) mPreviewTemp = mTemp;
            if (mPreviewTemp == null) {
                mPreviewTemp = new Rational[3];
                for (int i = 0; i < mPreviewTemp.length; i++)
                    mPreviewTemp[i] = new Rational(101, 100);
            }
            mColorSpaceTransform = result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM);
            Integer state = result.get(CaptureResult.FLASH_STATE);
            mFlashed = state != null && state == CaptureResult.FLASH_STATE_PARTIAL || state == CaptureResult.FLASH_STATE_FIRED;
            mPreviewCaptureResult = result;
            mPreviewCaptureRequest = request;
            process(result);
            cameraEventsListener.onPreviewCaptureCompleted(result);
        }

        //Automatic 60fps preview
        @Override
        public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
            super.onCaptureStarted(session, request, timestamp, frameNumber);
            if (frameNumber % 20 == 19) {
                if (ExposureIndex.index() > 8.0) {
                    if (!is30Fps) {
                        Log.d(TAG, "Changed preview target 30fps");
                        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, FpsRangeDef);
                        try {
                            mCaptureSession.stopRepeating();
                        } catch (CameraAccessException e) {
                            e.printStackTrace();
                        }
                        rebuildPreviewBuilder();
                        is30Fps = true;
                    }
                }
                if (ExposureIndex.index() + 0.9 < 8.0) {
                    if (is30Fps && PhotonCamera.getSettings().fpsPreview && !mCameraDevice.getId().equals("1")) {
                        Log.d(TAG, "Changed preview target 60fps");
                        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, FpsRangeHigh);
                        try {
                            mCaptureSession.stopRepeating();
                        } catch (CameraAccessException e) {
                            e.printStackTrace();
                        }
                        rebuildPreviewBuilder();
                        is30Fps = false;
                    }

                }
            }
        }
    };
    /**
     * {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its state.
     */
    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            // This method is called when the camera is opened.  We start camera preview here.
            mCameraOpenCloseLock.release();
            mCameraDevice = cameraDevice;
            createCameraPreviewSession(false);
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            cameraEventsListener.onFatalError("CameraDevice.StateCallback : onError() : cameraDevice = [" + cameraDevice + "], error = [" + error + "]");
        }
    };
    /**
     * {@link TextureView.SurfaceTextureListener} handles several lifecycle events on a
     * {@link TextureView}.
     */
    public final TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture texture, int width, int height) {
            Size optimal = getPreviewOutputSize(mTextureView.getDisplay(),
                    mCameraCharacteristicsMap.get(PhotonCamera.getSettings().mCameraID),
                    PhotonCamera.getSettings().selectedMode);
            openCamera(optimal.getWidth(), optimal.getHeight());
        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture texture, int width, int height) {
            Log.d(TAG, " CHANGED SIZE:" + width + ' ' + height);
            configureTransform(width, height);
        }

        @Override

        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture texture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture texture) {
        }

    };
    @HunterDebug
    public CaptureController(Activity activity, ExecutorService processExecutor, CameraEventsListener cameraEventsListener) {
        this.activity = activity;
        this.cameraEventsListener = cameraEventsListener;
        this.mTextureView = activity.findViewById(R.id.texture);
        this.mCameraManager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        this.mCameraManager2 = new CameraManager2(mCameraManager, PhotonCamera.getInstance(activity).getSettingsManager());
        this.processExecutor = processExecutor;
        this.paramController = new ParamController(this);

        this.fillInCameraCharateristics();
    }

    /**
     * Fills in {@link CaptureController#mCameraCharacteristicsMap} that is used in
     * {@link CaptureController#UpdateCameraCharacteristics}.
     */
    private void fillInCameraCharateristics() {
        try {
            String[] cameraIds = mCameraManager2.getCameraIdList();
            for (String cameraId : cameraIds) {
                mCameraCharacteristicsMap.put(cameraId, mCameraManager.getCameraCharacteristics(cameraId));
            }
        } catch (CameraAccessException cameraAccessException) {
            // Should not be possible to get here but anyway
            cameraAccessException.printStackTrace();
            showToast("Failed to fetch camera characteristics: " + cameraAccessException.getLocalizedMessage());
        }

    }

    public ParamController getParamController() {
        return paramController;
    }

    public static int getTargetFormat() {
        return mTargetFormat;
    }

    public static void setTargetFormat(int targetFormat) {
        mTargetFormat = targetFormat;
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
        if (sizes.get(s).getWidth() * sizes.get(s).getHeight() <= ResolutionSolution.highRes) {
            target = sizes.get(s);
            return target;
        } else {
            if (sizes.size() > 1) {
                target = sizes.get(s - 1);
                return target;
            }
        }
        return null;
    }

    private Size getCameraOutputSize(Size[] in, Size mPreviewSize) {
        if (in == null) return mPreviewSize;
        Arrays.sort(in, new CompareSizesByArea());
        List<Size> sizes = new ArrayList<>(Arrays.asList(in));
        int s = sizes.size() - 1;
        if (sizes.get(s).getWidth() * sizes.get(s).getHeight() <= ResolutionSolution.highRes || PhotonCamera.getSettings().QuadBayer) {
            target = sizes.get(s);
            if (PhotonCamera.getSettings().QuadBayer) {
                Rect pre = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE);
                if (pre == null) return target;
                Rect act = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
                if (act == null) return target;
                double k = (double) (target.getHeight()) / act.bottom;
                mul(pre, k);
                mul(act, k);
                CameraReflectionApi.set(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE, act);
                CameraReflectionApi.set(CameraCharacteristics.SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE, pre);
            }
            return target;
        } else {
            if (sizes.size() > 1) {
                target = sizes.get(s - 1);
                return target;
            }
        }
        return mPreviewSize;
    }

    /**
     * Sets up member variables related to camera.
     *
     * @param width  The width of available size for camera preview
     * @param height The height of available size for camera preview
     */
    private void setUpCameraOutputs(int width, int height) {
        try {
            mPreviewWidth = width;
            mPreviewHeight = height;
            UpdateCameraCharacteristics(PhotonCamera.getSettings().mCameraID);
            //Thread thr = new Thread(mImageSaver);
            //thr.start();
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            e.printStackTrace();
            cameraEventsListener.onError(R.string.camera_error);
        }
    }

    /**
     * Closes the current {@link CameraDevice}.
     */
    public void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            if (null != mCaptureSession) {
                mCaptureSession.close();
                mCaptureSession = null;
            }
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (null != mImageReaderPreview) {
                if (!isProcessing) {
                    mImageReaderPreview.close();
                    mImageReaderPreview = null;
                }
                if (!isProcessing) {
                    mImageReaderRaw.close();
                    mImageReaderRaw = null;
                }
            }
            if (null != mMediaRecorder) {
                mMediaRecorder.release();
                mMediaRecorder = null;
            }
            mState = STATE_CLOSED;
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    public void startBackgroundThread() {
        if (mBackgroundThread == null) {
            mBackgroundThread = new HandlerThread("CameraBackground");
            mBackgroundThread.start();
            mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
            Log.d(TAG, "startBackgroundThread() called from \"" + Thread.currentThread().getName() + "\" Thread");
        }
        //mBackgroundHandler.post(mImageSaver);
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    public void stopBackgroundThread() {
        if (mBackgroundThread == null)
            return;
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
            Log.d(TAG, "stopBackgroundThread() called from \"" + Thread.currentThread().getName() + "\" Thread");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

//    public void rebuildPreview() {
//        try {
////            mCaptureSession.stopRepeating();
//            mCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback, mBackgroundHandler);
//        } catch (CameraAccessException e) {
//            e.printStackTrace();
//        }
//    }

    public void rebuildPreviewBuilder() {
        if(burst) return;
        try {
//            mCaptureSession.stopRepeating();
            mCaptureSession.setRepeatingRequest(mPreviewInputRequest = mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);
        } catch (IllegalStateException | IllegalArgumentException | NullPointerException e) {
            Logger.warnShort(TAG, "Cannot rebuildPreviewBuilder()!", e);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void rebuildPreviewBuilderOneShot() {
        if(burst) return;
        try {
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);
        } catch (IllegalStateException | IllegalArgumentException | NullPointerException e) {
            Logger.warnShort(TAG, "Cannot rebuildPreviewBuilderOneShot()!", e);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Configures the necessary {@link android.graphics.Matrix} transformation to `mTextureView`.
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of `mTextureView` is fixed.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private void configureTransform(int viewWidth, int viewHeight) {
        if (null == mTextureView || null == mPreviewSize) {
            return;
        }
        int rotation = PhotonCamera.getGravity().getRotation();//activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        /*
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }*/
        mTextureView.setTransform(matrix);
    }
    @HunterDebug
    @SuppressLint("MissingPermission")
    public void restartCamera() {
        CameraFragment.mSelectedMode = PhotonCamera.getSettings().selectedMode;
        try {
            mCameraOpenCloseLock.acquire();
            if (mIsRecordingVideo) {
                this.VideoEnd();
            }

            if (mCaptureSession != null) {
                mCaptureSession.close();
                mCaptureSession = null;
            }
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (null != mImageReaderPreview) {
                if (!isProcessing) {
                    mImageReaderPreview.close();
                    mImageReaderPreview = null;
                }
                if (!isProcessing) {
                    mImageReaderRaw.close();
                    mImageReaderRaw = null;
                }
            }
            if (null != mMediaRecorder) {
                mMediaRecorder.release();
                mMediaRecorder = null;
            }
            if (null != mPreviewRequestBuilder) {
                mPreviewRequestBuilder = null;
            }
            stopBackgroundThread();
            UpdateCameraCharacteristics(PhotonCamera.getSettings().mCameraID);
            cameraEventsListener.onCameraRestarted();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Interrupted while trying to lock camera restarting.", e);
        } finally {
            try {
                mCameraOpenCloseLock.release();
            } catch (Exception ignored) {
                showToast("Failed to release camera");
            }
        }

        StreamConfigurationMap map = this.mCameraCharacteristicsMap.get(PhotonCamera.getSettings().mCameraID).get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

        if (map == null) return;
        Size preview = getCameraOutputSize(map.getOutputSizes(mPreviewTargetFormat));
        Size target = getCameraOutputSize(map.getOutputSizes(mTargetFormat), preview);
        int max = 3;
        if (mTargetFormat == mPreviewTargetFormat) max = PhotonCamera.getSettings().frameCount + 3;
        //largest = target;
        mImageReaderPreview = ImageReader.newInstance(target.getWidth(), target.getHeight(),
                mPreviewTargetFormat, /*maxImages*/max);
        mImageReaderPreview.setOnImageAvailableListener(
                mOnYuvImageAvailableListener, mBackgroundHandler);

        mImageReaderRaw = ImageReader.newInstance(target.getWidth(), target.getHeight(),
                mTargetFormat, PhotonCamera.getSettings().frameCount + 3);
        mImageReaderRaw.setOnImageAvailableListener(
                mOnRawImageAvailableListener, mBackgroundHandler);
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            this.mCameraManager.openCamera(PhotonCamera.getSettings().mCameraID, mStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to restart camera.", e);
        }
        //stopBackgroundThread();
        UpdateCameraCharacteristics(PhotonCamera.getSettings().mCameraID);
        startBackgroundThread();

        Size optimal = getPreviewOutputSize(mTextureView.getDisplay(), mCameraCharacteristics, CameraFragment.mSelectedMode);

        setUpCameraOutputs(optimal.getWidth(), optimal.getHeight());
        configureTransform(optimal.getWidth(), optimal.getHeight());
    }

    private static Size getTextureOutputSize(
            Display display,
            CameraMode targetMode
    ) {
        Size aspectRatio;
        if (targetMode == CameraMode.VIDEO) {
            aspectRatio = new Size(9, 16);
        } else {
            aspectRatio = new Size(3, 4);
        }
        Point displayPoint = new Point();
        display.getRealSize(displayPoint);
        int shortSide = Math.min(displayPoint.x, displayPoint.y);
        int longSide = shortSide * aspectRatio.getHeight() / aspectRatio.getWidth();

        return new Size(longSide, shortSide);
    }

    private static Size getPreviewOutputSize(
            Display display,
            CameraCharacteristics characteristics,
            CameraMode targetMode
    ) {
        Size aspectRatio = null;
        if (targetMode == CameraMode.VIDEO) {
            aspectRatio = new Size(9, 16);
        } else {
            aspectRatio = new Size(3, 4);
        }
        Point displayPoint = new Point();
        display.getRealSize(displayPoint);
        int shortSide = Math.min(displayPoint.x, displayPoint.y);
        int longSide = shortSide / aspectRatio.getWidth() * aspectRatio.getHeight();


        // If image format is provided, use it to determine supported sizes; else use target class
        StreamConfigurationMap config = characteristics.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

        Size[] allSizes = config.getOutputSizes(SurfaceTexture.class);

        Size retsize = null;
        for (Size size : allSizes) {
            int sizeShort = Math.min(size.getHeight(), size.getWidth());
            int sizeLong = Math.max(size.getHeight(), size.getWidth());
            if (sizeLong % aspectRatio.getHeight() == 0 &&
                    sizeShort == aspectRatio.getWidth() * sizeLong / aspectRatio.getHeight() &&
                    sizeShort * sizeLong <= ResolutionSolution.previewRes) {
                retsize = new Size(sizeShort, sizeLong);
                break;
            }
            /*if (sizeShort <= shortSide && sizeLong <= longSide) {
                retsize = new Size(sizeShort, sizeLong);
                break;
            }*/
        }
        if (retsize == null) {
            retsize = new Size(800, 600);
        }
        return retsize;
    }

    /**
     * Lock the focus as the first step for a still image capture.
     */
    private void lockFocus() {
        if(burst) return;
        startTimerLocked();
        // This is how to tell the camera to lock focus.
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                CameraMetadata.CONTROL_AF_TRIGGER_START);
        // Tell #mCaptureCallback to wait for the lock.
        mState = STATE_WAITING_LOCK;
        try {
            mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to start camera preview because it couldn't access camera", e);
        } catch (IllegalStateException e) {
            Log.e(TAG, "Failed to start camera preview.", e);
        }
    }

    /**
     * Run the precapture sequence for capturing a still image. This method should be called when
     * we get a response in {@link #mCaptureCallback} from {@link #lockFocus()}.
     */
    private void runPreCaptureSequence() {
        if(burst) return;
        try {
            // This is how to tell the camera to trigger.
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            // Tell #mCaptureCallback to wait for the precapture sequence to be set.
            mState = STATE_WAITING_PRECAPTURE;
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException | IllegalStateException e) {
            e.printStackTrace();
        }
    }

    /**
     * Opens the camera specified by {@link Settings#mCameraID}.
     */
    @HunterDebug
    public void openCamera(int width, int height) {
        CameraFragment.mSelectedMode = PhotonCamera.getSettings().selectedMode;
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            //requestCameraPermission();
            return;
        }
        AsyncTask.execute(()->{
            mMediaRecorder = new MediaRecorder();
            cameraEventsListener.onOpenCamera(this.mCameraManager);
        });
        setUpCameraOutputs(width, height);
        configureTransform(width, height);
        try {
            if (!mCameraOpenCloseLock.tryAcquire(1000, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            this.mCameraManager.openCamera(PhotonCamera.getSettings().mCameraID, mStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }

    }
    @HunterDebug
    public void UpdateCameraCharacteristics(String cameraId) {
        PhotonCamera.getSpecificSensor().selectSpecifics(Integer.parseInt(cameraId));
        CameraCharacteristics characteristics = this.mCameraCharacteristicsMap.get(cameraId);
        mCameraCharacteristics = characteristics;
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
        Size target = getCameraOutputSize(map.getOutputSizes(mTargetFormat), preview);
        int maxjpg = 3;
        if (mTargetFormat == mPreviewTargetFormat)
            maxjpg = PhotonCamera.getSettings().frameCount + 3;
        mImageReaderPreview = ImageReader.newInstance(preview.getWidth(), preview.getHeight(), mPreviewTargetFormat, maxjpg);
        mImageReaderPreview.setOnImageAvailableListener(mOnYuvImageAvailableListener, mBackgroundHandler);

        mImageReaderRaw = ImageReader.newInstance(target.getWidth(), target.getHeight(), mTargetFormat, PhotonCamera.getSettings().frameCount + 3);
        mImageReaderRaw.setOnImageAvailableListener(mOnRawImageAvailableListener, mBackgroundHandler);
        // Find out if we need to swap dimension to get the preview size relative to sensor
        // coordinate.
        int displayRotation = PhotonCamera.getGravity().getRotation();
        mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        Range<Integer>[] ranges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);

        if (ranges == null) {
            Log.d(TAG, "No available FPS ranges.");
            assert false;
            ranges[0] = new Range<>(30, 30);
        } else {
            Log.d(TAG, "Available FPS ranges is:" + Arrays.toString(ranges) + ".");

            // Get FPS range for 60 FPS preview
            for (Range<Integer> range : ranges) {
                if (range.getUpper() >= 60) {
                    FpsRangeHigh = new Range<>(range.getLower(), range.getUpper());
                    break;
                } else {
                    FpsRangeHigh = range;
                }
            }

            // Get FPS range for non-60 FPS preview
            for (Range<Integer> range : ranges) {
                if (range.getUpper() == 30) {
                    FpsRangeDef = range;
                    break;
                }
            }

            Log.d(TAG, "In 60 FPS - " + FpsRangeHigh + ".");
            Log.d(TAG, "In 30 FPS - " + FpsRangeDef + ".");
        }

        /*boolean swappedDimensions = false;
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
        }*/

        mCameraAfModes = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);

        /*Point displaySize = new Point();
        activity.getWindowManager().getDefaultDisplay().getSize(displaySize);
        int rotatedPreviewWidth = mPreviewWidth;
        int rotatedPreviewHeight = mPreviewHeight;

        mPreviewWidth = Math.max(rotatedPreviewHeight, rotatedPreviewWidth);
        mPreviewHeight = Math.min(rotatedPreviewHeight, rotatedPreviewWidth);*/




        /*mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth*2,
                maxPreviewHeight*2, target);*/
        //mPreviewSize = new Size(mPreviewWidth, mPreviewHeight);


        // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
        //        // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
        //        // garbage capture data.
        mPreviewSize = getTextureOutputSize(mTextureView.getDisplay(), PhotonCamera.getSettings().selectedMode);
        activity.runOnUiThread(() -> {
            mTextureView.setAspectRatio(
                    mPreviewSize.getHeight(), mPreviewSize.getWidth());
            mTextureView.cameraSize = new Point(mPreviewSize.getHeight(), mPreviewSize.getWidth());
            if (PhotonCamera.getSettings().DebugData)
                showToast("preview:" + new Point(mPreviewWidth, mPreviewHeight));
        });

        // We fit the aspect ratio of TextureView to the size of preview we picked.
        /*
        int orientation = activity.getResources().getConfiguration().orientation;

        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            mTextureView.setAspectRatio(
                    mPreviewSize.getWidth(), mPreviewSize.getHeight());
            mTextureView.cameraSize = new Point(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        } else {
            mTextureView.setAspectRatio(
                    mPreviewSize.getHeight(), mPreviewSize.getWidth());
            mTextureView.cameraSize = new Point(mPreviewSize.getHeight(), mPreviewSize.getWidth());
        }*/


        // Check if the flash is supported.
        Boolean available = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
        mFlashSupported = available != null && available;
        Camera2ApiAutoFix.Init();
        if (mMediaRecorder == null) {
            mMediaRecorder = new MediaRecorder();
//            setUpMediaRecorder();
        }

        activity.runOnUiThread(() -> cameraEventsListener.onCharacteristicsUpdated(characteristics));

    }
    Surface surface;
    @HunterDebug
    public void createCameraPreviewSession(boolean isBurstSession) {
        try {
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;
            // We configure the size of default buffer to be the size of camera preview we want.
            Log.d(TAG, "createCameraPreviewSession() mTextureView:" + mTextureView);
            Log.d(TAG, "createCameraPreviewSession() Texture:" + texture);
            Log.d(TAG, "previewSize:" + mTextureView.cameraSize);

            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

            // This is the output Surface we need to start preview.
            if(surface == null)
                surface = new Surface(texture);
            // We set up a CaptureRequest.Builder with the output Surface.
            mPreviewRequestBuilder = null;
            if (mIsRecordingVideo) {
                mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            } else {
                mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            }
            mPreviewRequestBuilder.addTarget(surface);
            mPreviewMeteringAF = mPreviewRequestBuilder.get(CONTROL_AF_REGIONS);
            mPreviewAFMode = mPreviewRequestBuilder.get(CONTROL_AF_MODE);
            if (mIsRecordingVideo) {
                mPreviewRequestBuilder.set(CONTROL_AF_MODE, CONTROL_AF_MODE_CONTINUOUS_VIDEO);
                mPreviewAFMode = CONTROL_AF_MODE_CONTINUOUS_VIDEO;
                if (PreferenceKeys.isEisPhotoOn()) {
                    mPreviewRequestBuilder.set(CONTROL_VIDEO_STABILIZATION_MODE, CONTROL_VIDEO_STABILIZATION_MODE_ON);
                }
            }
            mPreviewMeteringAE = mPreviewRequestBuilder.get(CONTROL_AE_REGIONS);
            mPreviewAEMode = mPreviewRequestBuilder.get(CONTROL_AE_MODE);

            // Here, we create a CameraCaptureSession for camera preview.

            List<Surface> surfaces = Arrays.asList(surface, mImageReaderPreview.getSurface());
            if (isDualSession) {
                if (isBurstSession) {
                    surfaces = Arrays.asList(mImageReaderPreview.getSurface(), mImageReaderRaw.getSurface());
                }
                if (mTargetFormat == mPreviewTargetFormat) {
                    surfaces = Arrays.asList(surface, mImageReaderPreview.getSurface());
                }
            } else {
                surfaces = Arrays.asList(surface,
                        mImageReaderPreview.getSurface(), mImageReaderRaw.getSurface());
            }
            if (mIsRecordingVideo) {
                setUpMediaRecorder();
                surfaces = Arrays.asList(surface, mMediaRecorder.getSurface());
                mPreviewRequestBuilder.addTarget(mMediaRecorder.getSurface());
            }
            CameraCaptureSession.StateCallback stateCallback = new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    // The camera is already closed
                    if (null == mCameraDevice) {
                        return;
                    }
                    // When the session is ready, we start displaying the preview.
                    mCaptureSession = cameraCaptureSession;
                    try {
                        // Auto focus should be continuous for camera preview.
                        //mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                        // Flash is automatically enabled when necessary.
                        resetPreviewAEMode();
                        Camera2ApiAutoFix.applyPrev(mPreviewRequestBuilder);
                        // Finally, we start displaying the camera preview.
                        if (!PreferenceKeys.isFpsPreviewOn()) {
                            Log.d(TAG, "60 FPS is disabled. Current FPS is " + FpsRangeDef + ".");
                            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, FpsRangeDef);
                        } else {
                            Log.d(TAG, "60 FPS is enabled. Current FPS is " + FpsRangeHigh + ".");
                            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, FpsRangeHigh);
                        }
                        mPreviewInputRequest = mPreviewRequestBuilder.build();
                        if (isBurstSession && isDualSession) {
                            switch (CameraFragment.mSelectedMode) {
                                case NIGHT:
                                case PHOTO:
                                case MOTION:
                                    mCaptureSession.captureBurst(captures, CaptureCallback, null);
                                    break;
                                case UNLIMITED:
                                    mCaptureSession.setRepeatingBurst(captures, CaptureCallback, null);
                                    break;
                            }
                        } else {
                            //if(mSelectedMode != CameraMode.VIDEO)
                            mCaptureSession.setRepeatingRequest(mPreviewInputRequest,
                                    mCaptureCallback, mBackgroundHandler);
                            unlockFocus();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    if (mIsRecordingVideo)
                        activity.runOnUiThread(() -> {
                            // Start recording
                            mMediaRecorder.start();
                        });
                }

                @Override
                public void onConfigureFailed(
                        @NonNull CameraCaptureSession cameraCaptureSession) {
                    showToast("Session onConfigureFailed");
                }
            };
            ArrayList<OutputConfiguration> outputConfigurations = new ArrayList<>();
            for (Surface surfacei : surfaces) {
                outputConfigurations.add(new OutputConfiguration(surfacei));
            }
            if (mIsRecordingVideo) {
                //InputConfiguration inputConfiguration = new InputConfiguration(mImageReaderPreview.getWidth(),mImageReaderPreview.getHeight(),ImageFormat.YUV_420_888);
                //CameraReflectionApi.createCustomCaptureSession(mCameraDevice,inputConfiguration,outputConfigurations,61444,stateCallback,null);
                mCameraDevice.createCaptureSession(surfaces, stateCallback, null);
            } else {
                mCameraDevice.createCaptureSession(surfaces, stateCallback, null);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showToast(String msg) {
        if (activity != null) {
            new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show());
        }
    }

    /**
     * Initiate a still image capture.
     */
    public void takePicture() {
        if (mCameraAfModes.length > 1) lockFocus();
        else {
            try {
                mState = STATE_WAITING_NON_PRECAPTURE;
                mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback,
                        mBackgroundHandler);
            } catch (CameraAccessException e) {
                Log.e(TAG, "Failed to start camera preview because it couldn't access camera", e);
            } catch (IllegalStateException e) {
                Log.e(TAG, "Failed to start camera preview.", e);
            }
        }
    }

    /**
     * Unlock the focus. This method should be called when still image capture sequence is
     * finished.
     */
    private void unlockFocus() {
        // Reset the auto-focus trigger
        //mCaptureSession.stopRepeating();
        CameraReflectionApi.set(mPreviewInputRequest, CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
        //mCaptureSession.capture(mPreviewRequest, mCaptureCallback,
        //        mBackgroundHandler);
        // After this, the camera will go back to the normal state of preview.
        mState = STATE_PREVIEW;
        rebuildPreviewBuilder();
        //mCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback,
        //        mBackgroundHandler);
    }
    public CaptureRequest.Builder getDebugCaptureRequestBuilder(){
        final CaptureRequest.Builder captureBuilder;
        try {
            captureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            if (mTargetFormat != mPreviewTargetFormat)
                captureBuilder.addTarget(mImageReaderRaw.getSurface());
            else
                captureBuilder.addTarget(mImageReaderPreview.getSurface());
            return captureBuilder;
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        return null;
    }
    private void debugCapture(CaptureRequest.Builder builder){
        try {
            if (null == mCameraDevice) {
                return;
            }
            Camera2ApiAutoFix.applyEnergySaving();
            captures = new ArrayList<>();

            int frameCount = 1;
            cameraEventsListener.onFrameCountSet(frameCount);

            captures.add(builder.build());


            Log.d(TAG, "FrameCount:" + frameCount);

            Log.d(TAG, "CaptureStarted!");

            final long[] baseFrameNumber = {0};
            final int[] maxFrameCount = {frameCount};

            cameraEventsListener.onCaptureStillPictureStarted("CaptureStarted!");
            mMeasuredFrameCnt = 0;
            mImageSaver = new ImageSaver(cameraEventsListener);
            mImageSaver.implementation = new DebugSender(cameraEventsListener);

            cameraEventsListener.onBurstPrepared(null);
            this.CaptureCallback = new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureStarted(@NonNull CameraCaptureSession session,
                                             @NonNull CaptureRequest request,
                                             long timestamp,
                                             long frameNumber) {

                    if (baseFrameNumber[0] == 0) {
                        baseFrameNumber[0] = frameNumber - 1L;
                        Log.v("BurstCounter", "CaptureStarted with FirstFrameNumber:" + frameNumber);
                    } else {
                        Log.v("BurstCounter", "CaptureStarted:" + frameNumber);
                    }
                    cameraEventsListener.onFrameCaptureStarted(null);
                }

                @Override
                public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request,
                                                @NonNull CaptureResult partialResult) {
                }

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {

                    int frameCount = (int) (result.getFrameNumber() - baseFrameNumber[0]);
                    Log.v("BurstCounter", "CaptureCompleted! FrameCount:" + frameCount);
                    long frametime = 100;
                    Object time = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
                    if(time != null) frametime = (long)time;
                    cameraEventsListener.onFrameCaptureCompleted(
                            new TimerFrameCountViewModel.FrameCntTime(frameCount, maxFrameCount[0], frametime));
                    mCaptureResult = result;
                }

                @Override
                public void onCaptureSequenceCompleted(@NonNull CameraCaptureSession session,
                                                       int sequenceId,
                                                       long lastFrameNumber) {

                    int finalFrameCount = (int) (lastFrameNumber - baseFrameNumber[0]);
                    Log.v("BurstCounter", "CaptureSequenceCompleted! FrameCount:" + finalFrameCount);
                    Log.v("BurstCounter", "CaptureSequenceCompleted! LastFrameNumber:" + lastFrameNumber);
                    Log.d(TAG, "SequenceCompleted");
                    mMeasuredFrameCnt = finalFrameCount;
                    cameraEventsListener.onCaptureSequenceCompleted(null);
                    burst = false;
                    //Surface texture related
                    activity.runOnUiThread(() -> UpdateCameraCharacteristics(PhotonCamera.getSettings().mCameraID));
                    if (!isDualSession)
                        unlockFocus();
                    else
                        createCameraPreviewSession(false);
                    taskResults.removeIf(Future::isDone); //remove already completed results
                    Future<?> result = processExecutor.submit(() -> mImageSaver.runRaw(mCameraCharacteristics, mCaptureResult, BurstShakiness, cameraRotation));
                    taskResults.add(result);
                }
            };
            burst = true;
            Camera2ApiAutoFix.ApplyBurst();
            if (isDualSession)
                createCameraPreviewSession(true);
            else {
                mCaptureSession.captureBurst(captures, CaptureCallback, null);
            }

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void runDebug(CaptureRequest.Builder builder){
        activity.runOnUiThread(() -> debugCapture(builder));
    }

    private void captureStillPicture() {
        try {
            if (null == mCameraDevice) {
                return;
            }
            // This is the CaptureRequest.Builder that we use to take a picture.
            final CaptureRequest.Builder captureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            float focus = mFocus;
            //this.mCaptureSession.stopRepeating();
            if (mTargetFormat != mPreviewTargetFormat)
                captureBuilder.addTarget(mImageReaderRaw.getSurface());
            else
                captureBuilder.addTarget(mImageReaderPreview.getSurface());
            Camera2ApiAutoFix.applyEnergySaving();
            cameraRotation = PhotonCamera.getGravity().getCameraRotation(mSensorOrientation);

            //captureBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);
            //setCaptureAEMode(captureBuilder);
            if (mFlashed) captureBuilder.set(FLASH_MODE, FLASH_MODE_TORCH);
            Log.d(TAG, "Focus:" + focus);
            captureBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_CANCEL);
            int[] stabilizationModes = mCameraCharacteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION);
            if (stabilizationModes != null && stabilizationModes.length > 1) {
                Log.d(TAG, "LENS_OPTICAL_STABILIZATION_MODE");
//                captureBuilder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF);//Fix ois bugs for preview and burst
                captureBuilder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON);//Fix ois bugs for preview and burst
            }
            for (int i = 0; i < 3; i++) {
                Log.d(TAG, "Temperature:" + mPreviewTemp[i]);
            }
            Log.d(TAG, "CaptureBuilderStarted!");
            //setAutoFlash(captureBuilder);
            //int rotation = Interface.getGravity().getCameraRotation();//activity.getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, PhotonCamera.getGravity().getCameraRotation(mSensorOrientation));

            captures = new ArrayList<>();
            BurstShakiness = new ArrayList<>();

            int frameCount = FrameNumberSelector.getFrames();
            //if (frameCount == 1) frameCount++;
            cameraEventsListener.onFrameCountSet(frameCount);
            Log.d(TAG, "HDRFact1:" + paramController.isManualMode() + " HDRFact2:" + PhotonCamera.getSettings().alignAlgorithm);
            //IsoExpoSelector.HDR = (!manualParamModel.isManualMode()) && (PhotonCamera.getSettings().alignAlgorithm == 0);
            //IsoExpoSelector.HDR = (PhotonCamera.getSettings().alignAlgorithm == 1);
            IsoExpoSelector.HDR = false;
            Log.d(TAG, "HDR:" + IsoExpoSelector.HDR);

            if (!(focus == 0.0 && Build.DEVICE.equalsIgnoreCase("samsung"))) {
                //captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CONTROL_AF_MODE_OFF);
                captureBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CONTROL_AF_TRIGGER_IDLE);
                captureBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, focus);
            }



            /*mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
            if (focus != 0.0)
                mPreviewRequestBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, focus);
            rebuildPreviewBuilder();*/

            IsoExpoSelector.useTripod = PhotonCamera.getGyro().getTripod();
            long[] times = new long[frameCount];
            for (int i = 0; i < frameCount; i++) {
                IsoExpoSelector.setExpo(captureBuilder, i, this);
                times[i] = IsoExpoSelector.lastSelectedExposure;
                captures.add(captureBuilder.build());
            }
            if (frameCount == -1) {
                for (int i = 0; i < IsoExpoSelector.patternSize; i++) {
                    IsoExpoSelector.setExpo(captureBuilder, i, this);
                    captures.add(captureBuilder.build());
                }
            } else
                PhotonCamera.getGyro().PrepareGyroBurst(times, BurstShakiness);
            double frametime = ExposureIndex.time2sec(IsoExpoSelector.GenerateExpoPair(-1, this).exposure);
            //img
            Log.d(TAG, "FrameCount:" + frameCount);
//            final int[] burstcount = {0, 0, frameCount};
            Log.d(TAG, "CaptureStarted!");

            final long[] baseFrameNumber = {0};
            final int[] maxFrameCount = {frameCount};

            cameraEventsListener.onCaptureStillPictureStarted("CaptureStarted!");
            mMeasuredFrameCnt = 0;
            mImageSaver = new ImageSaver(cameraEventsListener);

            cameraEventsListener.onBurstPrepared(null);
            this.CaptureCallback = new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureStarted(@NonNull CameraCaptureSession session,
                                             @NonNull CaptureRequest request,
                                             long timestamp,
                                             long frameNumber) {

                    if (baseFrameNumber[0] == 0) {
                        baseFrameNumber[0] = frameNumber - 1L;
                        Log.v("BurstCounter", "CaptureStarted with FirstFrameNumber:" + frameNumber);
                    } else {
                        Log.v("BurstCounter", "CaptureStarted:" + frameNumber);
                    }
                    cameraEventsListener.onFrameCaptureStarted(null);
                    if (maxFrameCount[0] != -1) PhotonCamera.getGyro().CaptureGyroBurst();
                }

                @Override
                public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request,
                                                @NonNull CaptureResult partialResult) {
                }

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {

                    int frameCount = (int) (result.getFrameNumber() - baseFrameNumber[0]);
                    Log.v("BurstCounter", "CaptureCompleted! FrameCount:" + frameCount);
                    cameraEventsListener.onFrameCaptureCompleted(
                            new TimerFrameCountViewModel.FrameCntTime(frameCount, maxFrameCount[0], frametime));

                    if (onUnlimited && !unlimitedStarted) {
                        mImageSaver.unlimitedStart(mCameraCharacteristics, result, cameraRotation);
                        unlimitedStarted = true;
                    }
                    mCaptureResult = result;
                }

                @Override
                public void onCaptureSequenceCompleted(@NonNull CameraCaptureSession session,
                                                       int sequenceId,
                                                       long lastFrameNumber) {

                    int finalFrameCount = (int) (lastFrameNumber - baseFrameNumber[0]);
                    Log.v("BurstCounter", "CaptureSequenceCompleted! FrameCount:" + finalFrameCount);
                    Log.v("BurstCounter", "CaptureSequenceCompleted! LastFrameNumber:" + lastFrameNumber);
                    Log.d(TAG, "SequenceCompleted");
                    mMeasuredFrameCnt = finalFrameCount;
                    cameraEventsListener.onCaptureSequenceCompleted(null);
                    burst = false;
                    if (maxFrameCount[0] != -1) PhotonCamera.getGyro().CompleteGyroBurst();
                    //unlockFocus();
                    //Surface texture related
                    activity.runOnUiThread(() -> UpdateCameraCharacteristics(PhotonCamera.getSettings().mCameraID));
                    if (!isDualSession)
                        unlockFocus();
                    else
                        createCameraPreviewSession(false);
                    PhotonCamera.getGyro().CompleteSequence();

                    taskResults.removeIf(Future::isDone); //remove already completed results
                    if (PhotonCamera.getSettings().selectedMode != CameraMode.UNLIMITED) {
                        Future<?> result = processExecutor.submit(() -> mImageSaver.runRaw(mCameraCharacteristics, mCaptureResult, BurstShakiness, cameraRotation));
                        taskResults.add(result);
                    }
                }
            };

            //mCaptureSession.setRepeatingBurst(captures, CaptureCallback, null);
            burst = true;
            Camera2ApiAutoFix.ApplyBurst();
            if (isDualSession)
                createCameraPreviewSession(true);
            else {
                switch (PhotonCamera.getSettings().selectedMode) {
                    case NIGHT:
                    case UNLIMITED:
                        mCaptureSession.setRepeatingBurst(captures, CaptureCallback, null);
                        break;
                    case PHOTO:
                    case MOTION:
                        mCaptureSession.captureBurst(captures, CaptureCallback, null);
                        break;
                }
            }

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void abortCaptures() {
        try {
            mCaptureSession.abortCaptures();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void reset3Aparams() {
        setAEMode(mPreviewRequestBuilder, PreferenceKeys.getAeMode());
//        setAFMode(mPreviewRequestBuilder, PreferenceKeys.getAfMode());
        setAFMode(mPreviewRequestBuilder, CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        rebuildPreviewBuilder();
    }

    public void setPreviewAEModeRebuild(int aeMode) {
        setAEMode(mPreviewRequestBuilder, aeMode);
        rebuildPreviewBuilder();
    }

    public void resetPreviewAEMode() {
        setAEMode(mPreviewRequestBuilder, PreferenceKeys.getAeMode());
    }

    /**
     * @param requestBuilder CaptureRequest.Builder
     * @param aeMode         possible values = 0, 1, 2, 3
     */
    private void setAEMode(CaptureRequest.Builder requestBuilder, int aeMode) {
        if (requestBuilder != null) {
            if (mFlashSupported) {
                requestBuilder.set(CONTROL_AE_MODE, Math.max(aeMode, 1));//here AE_MODE will never be OFF(0)

                //if PreferenceKeys.getAeMode() returns zero, we set the FLASH_MODE_TORCH instead of setting AE_MODE to OFF(0)
                requestBuilder.set(CaptureRequest.FLASH_MODE,
                        aeMode == 0 ? CaptureRequest.FLASH_MODE_TORCH : CaptureRequest.FLASH_MODE_OFF);
            } else {
                requestBuilder.set(CONTROL_AE_MODE, CONTROL_AE_MODE_ON);
                requestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
            }
        }
    }

    private void setAFMode(CaptureRequest.Builder builder, int afMode) {
        if (builder != null) {
            builder.set(CaptureRequest.CONTROL_AF_REGIONS, builder.get(CONTROL_AF_REGIONS));
            builder.set(CaptureRequest.CONTROL_AE_REGIONS, builder.get(CONTROL_AE_REGIONS));
            builder.set(CaptureRequest.CONTROL_AF_MODE, afMode);
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

    public void callUnlimitedEnd() {
        onUnlimited = false;
        mImageSaver.unlimitedEnd();
        abortCaptures();
        createCameraPreviewSession(false);
        unlimitedStarted = false;
    }

    public void callUnlimitedStart() {
        onUnlimited = true;
        takePicture();
    }

    public void VideoEnd() {
        mIsRecordingVideo = false;
        stopRecordingVideo();
    }

    public void VideoStart() {
        mIsRecordingVideo = true;
        createCameraPreviewSession(false);
    }

    private void setUpMediaRecorder() {
        mMediaRecorder.reset();
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        CamcorderProfile profile = CamcorderProfile.get(CamcorderProfile.QUALITY_2160P);
        mMediaRecorder.setVideoFrameRate(profile.videoFrameRate);
        mMediaRecorder.setVideoSize(profile.videoFrameWidth, profile.videoFrameHeight);
        mMediaRecorder.setVideoEncodingBitRate(profile.videoBitRate);
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mMediaRecorder.setAudioEncodingBitRate(profile.audioBitRate);
        mMediaRecorder.setAudioSamplingRate(profile.audioSampleRate);
        mMediaRecorder.setOnInfoListener(this);
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        switch (mSensorOrientation) {
            case SENSOR_ORIENTATION_DEFAULT_DEGREES:
                mMediaRecorder.setOrientationHint(DEFAULT_ORIENTATIONS.get(rotation));
                break;
            case SENSOR_ORIENTATION_INVERSE_DEGREES:
                mMediaRecorder.setOrientationHint(INVERSE_ORIENTATIONS.get(rotation));
                break;
        }
        Date currentDate = new Date();
        DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
        String dateText = dateFormat.format(currentDate);
        File dir = new File(Environment.getExternalStorageDirectory() + "//DCIM//Camera//");
        vid = new File(dir.getAbsolutePath(), "VID_" + dateText + ".mp4");
        try {
            vid.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
        mMediaRecorder.setOutputFile(vid.getAbsolutePath());
        try {
            mMediaRecorder.prepare();
            Log.d(TAG, "video record start");

        } catch (Exception e) {
            Log.d(TAG, "video record failed");
        }
    }

    private void stopRecordingVideo() {
        mIsRecordingVideo = false;

        try {
            mMediaRecorder.stop();
        } catch (Exception stopFailure) {
            stopFailure.printStackTrace();
            Toast.makeText(activity.getApplicationContext(), "Failed to stop recording", Toast.LENGTH_SHORT).show();
            if (vid.delete()) {
                Toast.makeText(activity.getApplicationContext(), "Video file has been removed", Toast.LENGTH_SHORT).show();
            }
        }
        mMediaRecorder.reset();
        cameraEventsListener.onRequestTriggerMediaScanner(Uri.fromFile(vid));
        createCameraPreviewSession(false);
    }

    @Override
    public void onInfo(MediaRecorder mr, int what, int extra) {
        if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
            Log.v(TAG, "Maximum Duration Reached, Call stopRecordingVideo()");
            stopRecordingVideo();
        }
    }

    private void mul(Rect in, double k) {
        in.bottom *= k;
        in.left *= k;
        in.right *= k;
        in.top *= k;
    }

    @Override
    protected void finalize() throws Throwable {
        activity = null;
        cameraEventsListener = null;
        mCameraManager = null;
        mTextureView = null;
        super.finalize();
    }
    @HunterDebug
    public void resumeCamera() {
        AsyncTask.execute(() -> {
            if (mTextureView == null)
                mTextureView = new AutoFitPreviewView(activity);
            if (mTextureView.isAvailable()) {
                Size optimal = getPreviewOutputSize(mTextureView.getDisplay(),
                        mCameraCharacteristicsMap.get(PhotonCamera.getSettings().mCameraID),
                        PhotonCamera.getSettings().selectedMode);
                openCamera(optimal.getWidth(), optimal.getHeight());
            } else {
                mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
            }
        });
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

    public static class CameraProperties {
        private final Float minFocal = mCameraCharacteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);
        private final Float maxFocal = mCameraCharacteristics.get(CameraCharacteristics.LENS_INFO_HYPERFOCAL_DISTANCE);
        public Range<Float> focusRange = (!(minFocal == null || maxFocal == null || minFocal == 0.0f)) ? new Range<>(Math.min(minFocal, maxFocal), Math.max(minFocal, maxFocal)) : null;
        public Range<Integer> isoRange = new Range<>(IsoExpoSelector.getISOLOWExt(), IsoExpoSelector.getISOHIGHExt());
        public Range<Long> expRange = new Range<>(IsoExpoSelector.getEXPLOW(), IsoExpoSelector.getEXPHIGH());
        private final float evStep = mCameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP).floatValue();
        public Range<Float> evRange = new Range<>((mCameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE).getLower() * evStep),
                (mCameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE).getUpper() * evStep));

        public CameraProperties() {
            logIt();
        }

        private void logIt() {
            String lens = PhotonCamera.getSettings().mCameraID;
            Log.d(TAG, "focusRange(" + lens + ") : " + (focusRange == null ? "Fixed [" + maxFocal + "]" : focusRange.toString()));
            Log.d(TAG, "isoRange(" + lens + ") : " + isoRange.toString());
            Log.d(TAG, "expRange(" + lens + ") : " + expRange.toString());
            Log.d(TAG, "evCompRange(" + lens + ") : " + evRange.toString());
        }

    }
}