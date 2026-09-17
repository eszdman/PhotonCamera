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
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
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
import android.hardware.camera2.params.SessionConfiguration;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.hardware.camera2.params.RggbChannelVector;
import android.media.CamcorderProfile;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;

import com.particlesdevs.photoncamera.util.Allocator;
import com.particlesdevs.photoncamera.util.Log;
import android.util.Range;
import android.util.Rational;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Display;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.api.Camera2ApiAutoFix;
import com.particlesdevs.photoncamera.api.CameraEventsListener;
import com.particlesdevs.photoncamera.api.CameraManager2;
import com.particlesdevs.photoncamera.processing.render.SpecificSettingSensor;
import com.particlesdevs.photoncamera.api.CameraMode;
import com.particlesdevs.photoncamera.api.CameraReflectionApi;
import com.particlesdevs.photoncamera.api.Settings;
import com.particlesdevs.photoncamera.api.VendorTagUtils;
import com.particlesdevs.photoncamera.api.LogicalCameraResolver;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.circularbarlib.util.Motion;
import com.particlesdevs.photoncamera.circularbarlib.api.ManualModeConsole;
import com.particlesdevs.photoncamera.control.GyroBurst;
import com.particlesdevs.photoncamera.control.TouchFocus;
import com.particlesdevs.photoncamera.debugclient.DebugSender;
import com.particlesdevs.photoncamera.manual.ParamController;
import com.particlesdevs.photoncamera.processing.ImageSaver;
import com.particlesdevs.photoncamera.processing.parameters.ExposureIndex;
import com.particlesdevs.photoncamera.processing.parameters.FrameNumberSelector;
import com.particlesdevs.photoncamera.processing.parameters.IsoExpoSelector;
import com.particlesdevs.photoncamera.processing.parameters.ResolutionSolution;
import com.particlesdevs.photoncamera.processing.parameters.ColorTemperatureConverter;
import com.particlesdevs.photoncamera.settings.PreferenceKeys;
import com.particlesdevs.photoncamera.settings.SensorConfigInjector;
import com.particlesdevs.photoncamera.settings.annotations.SensorConfig;
import com.particlesdevs.photoncamera.ui.camera.CameraFragment;
import com.particlesdevs.photoncamera.ui.camera.data.CameraLensData;
import com.particlesdevs.photoncamera.ui.camera.viewmodel.TimerFrameCountViewModel;
import com.particlesdevs.photoncamera.ui.camera.views.viewfinder.AutoFitPreviewView;
import com.particlesdevs.photoncamera.ui.camera.views.viewfinder.GLPreview;
import com.particlesdevs.photoncamera.ui.camera.views.viewfinder.ViewfinderFrameView;
import com.particlesdevs.photoncamera.util.log.Logger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

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
import java.util.Objects;
import android.media.Image;
import java.util.ArrayDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import com.particlesdevs.photoncamera.processing.ImageFrame;
import com.particlesdevs.photoncamera.processing.ImageSaverSelector;
import com.particlesdevs.photoncamera.processing.SaverImplementation;

import static android.hardware.camera2.CameraMetadata.CONTROL_AE_MODE_ON;
import static android.hardware.camera2.CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_VIDEO;
import static android.hardware.camera2.CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON;
import static android.hardware.camera2.CameraMetadata.FLASH_MODE_TORCH;
import static android.hardware.camera2.CaptureRequest.COLOR_CORRECTION_MODE;
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

    private boolean useMaximumResolutionKey = false;

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

    private Map<String, CameraCharacteristics> mCameraCharacteristicsMap = new HashMap<>();
    public static CameraCharacteristics mCameraCharacteristics;
    public static CaptureResult mCaptureResult;
    public static CaptureRequest mCaptureRequest;

    public static CaptureResult mPreviewCaptureResult;
    public static CaptureRequest mPreviewCaptureRequest;
    public static int mPreviewTargetFormat = ImageFormat.JPEG;
    public boolean isDualSession = false;

    @SensorConfig(title = "Session Type",
            defaultValue = 0, min = 0, max = 65535, step = 0,
            description = "Camera capture session type (0 = regular)")
    public int sessionType = 0;

    @SensorConfig(
            title = "OIS Mode",
            description = "Controls optical stabilization. Auto mode disables OIS on a tripod and in Unlimited to prevent drift",
            entries = {"On", "Auto", "Off"},
            entryValues = {"0", "1", "2"},
            defaultValue = 0
    )
    public int oisMode = 0;

    @SensorConfig(
            title = "Exposure Balance",
            description = "Shift balance between shutter speed and ISO. Photo and Night modes only",
            entries = {
                    "0.25x (Max SNR Bias)",
                    "0.35x (High SNR Bias)",
                    "0.50x (Medium SNR Bias)",
                    "0.71x (Slight SNR Bias)",
                    "1.00x (Balanced / Default)",
                    "1.41x (Slight Speed Bias)",
                    "2.00x (Medium Speed Bias)",
                    "2.83x (High Speed Bias)",
                    "4.00x (Max Speed Bias)"
            },
            entryValues = {"0.25", "0.35", "0.5", "0.71", "1.0", "1.41", "2.0", "2.83", "4.0"},
            defaultValue = 1.0f
    )
    public float exposureBalanceMultiplier = 1.0f;

    @SensorConfig(
            title = "ISO Limit",
            description = "Limit the maximum sensitivity allowed",
            entries = {"400", "800", "1600", "3200", "6400", "12800", "Max Analog ISO / 4", "Max Analog ISO / 2", "Max Analog ISO", "Sensor Max ISO"},
            entryValues = {"400", "800", "1600", "3200", "6400", "12800", "-4", "-3", "-2", "-1"},
            defaultValue = -1
    )
    public int exposureBalanceIsoLimit = -1;

    @SensorConfig(
            title = "Shutter Limit",
            description = "Limit the maximum exposure duration allowed",
            entries = {
                    "1/500", "1/250", "1/125", "1/90", "1/60", "1/45", "1/30", "1/20", 
                    "1/15", "1/10", "1/8", "1/6", "1/4", 
                    "1/3", "1/2", "1.0", "2.0", "Auto Safe (Lens Reciprocal)", "Sensor Max Time"
            },
            entryValues = {
                    "0.002", "0.004", "0.008", "0.0111", "0.0167", "0.0222", "0.0333", "0.05", 
                    "0.0667", "0.1", "0.125", "0.1667", "0.25", 
                    "0.3333", "0.5", "1.0", "2.0", "-2", "-1"
            },
            defaultValue = -1.0f
    )
    public float exposureBalanceShutterLimit = -1.0f;

    private static int mTargetFormat = RAW_FORMAT;
    private ManualModeConsole manualModeConsole;
    private final ParamController paramController;
    public TouchFocus mTouchFocus;

    public final boolean mFlashEnabled = false;
    private CameraEventsListener cameraEventsListener;
    /**
     * A {@link Semaphore} to prevent the app from exiting before closing the camera.
     */
    private final Semaphore mCameraOpenCloseLock = new Semaphore(1);
    /**
     * Guards {@link #openCamera(int, int)} against duplicate concurrent opens.
     * Held from the moment an open is requested until the device is closed
     * again (see {@link #closeCamera()} and the {@link CameraDevice} callbacks).
     */
    private final AtomicBoolean mCameraOpening = new AtomicBoolean(false);
    /**
     * Serializes lens-switch close/open cycles and coalesces the bursts of
     * requests produced by a fast pinch sweeping across lens thresholds. Only
     * one cycle may be in flight; requests made meanwhile update a pending
     * target consumed when the cycle settles.
     */
    private final LensSwitchScheduler lensSwitchScheduler = new LensSwitchScheduler();
    /** Main-thread handler for deferred opens, settle delays and retries. */
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    /**
     * Minimum time between closing a camera device and opening the next one.
     * Some vendor HALs crash the camera service when a device is reopened
     * immediately after close, which is exactly what a fast pinch produces.
     * Only back-to-back switches pay this delay; an isolated switch after idle
     * opens immediately.
     */
    private static final long LENS_SWITCH_SETTLE_MS = 250L;
    /** Poll interval while waiting for an in-flight cold open before switching. */
    private static final long CYCLE_WAIT_MS = 50L;
    /** Give up on starting a switch if the camera stays busy this long. */
    private static final long CYCLE_WAIT_TIMEOUT_MS = 2000L;
    /** Base backoff for retrying a failed open (300/600/1200 ms). */
    private static final long OPEN_RETRY_BASE_MS = 300L;
    /** Base backoff for reopening after an unexpected device disconnect. */
    private static final long DISCONNECT_RETRY_BASE_MS = 500L;
    /** Automatic recoveries after unexpected disconnects before giving up. */
    private static final int MAX_DISCONNECT_RECOVERIES = 2;
    /** Timestamp ({@link SystemClock#elapsedRealtime()}) of the last device close. */
    private volatile long lastCameraCloseMs = 0L;
    /** Invalidation token for in-flight {@link CameraDevice.StateCallback}s. */
    private final AtomicInteger openToken = new AtomicInteger(0);
    private boolean cycleStartScheduled = false;
    private long cycleStartRequestedMs = 0L;
    private final AtomicInteger disconnectRecoveries = new AtomicInteger(0);
    /**
     * True only while the camera fragment is foregrounded (between
     * {@link #resumeCamera()} and the next {@link #closeCamera()}). Open
     * requests and onOpened() deliveries that arrive after backgrounding are
     * dropped so a hidden app never grabs or holds the camera device.
     */
    private volatile boolean isCameraResumed = false;
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
    /** Digital zoom source of truth; drives both the preview and the captured crop. */
    public final ZoomController zoomController = new ZoomController();
    /** True when the camera is (re)opening because of a zoom-driven lens switch. */
    private boolean zoomDrivenLensSwitch = false;
    /**
     * The current state of camera state for taking pictures.
     */
    public int mState = STATE_PREVIEW;
    /**
     * Orientation of the camera sensor
     */
    public int mSensorOrientation;
    public int cameraRotation;
    public boolean onUnlimited = false;
    public boolean unlimitedStarted = false;
    public boolean mFlashed = false;
    public ArrayList<GyroBurst> BurstShakiness;
    /**
     * This a callback object for the {@link ImageReader}. "onImageAvailable" will be called when a
     * still image is ready to be saved.
     */
    public ImageSaver mImageSaver;
    public HashMap<Long, Double> mExposures = new HashMap<>();

    private final ArrayDeque<Image> mZslRingBuffer = new ArrayDeque<>();
    private final Object mZslBufferLock = new Object();
    private volatile boolean mZslCapturing = false;

    public interface RawFrameCallback {
        void onRawFrameAvailable(@NonNull Image image, CaptureResult result);
    }
    private volatile RawFrameCallback mPendingRawMeteringCallback = null;

    private final ImageReader.OnImageAvailableListener mOnYuvImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            //mImageSaver.mImage = reader.acquireNextImage();
            //mImageSaver.initProcess(reader);
//            Message msg = new Message();
//            msg.obj = reader;
//            mImageSaver.processingHandler.sendMessage(msg);
            //processExecutor.execute(() -> mImageSaver.initProcess(reader));
            mImageSaver.initProcess(reader);
        }
    };
    private final ImageReader.OnImageAvailableListener mOnRawImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            RawFrameCallback cb = mPendingRawMeteringCallback;
            if (cb != null) {
                mPendingRawMeteringCallback = null;
                Image img = reader.acquireNextImage();
                if (img != null) {
                    cb.onRawFrameAvailable(img, mPreviewCaptureResult);
                }
                return;
            }
            //dequeueAndSaveImage(mRawResultQueue, mRawImageReader);
            //mImageSaver.mImage = reader.acquireNextImage();
//            Message msg = new Message();
//            msg.obj = reader;
//            mImageSaver.processingHandler.sendMessage(msg);
            if (isZslMode()) {
                Image img = reader.acquireNextImage();
                if (img == null) return;
                if (mZslCapturing) {
                    img.close();
                    return;
                }
                synchronized (mZslBufferLock) {
                    mZslRingBuffer.addLast(img);
                    int maxFrames = Math.min(PhotonCamera.getSettings().frameCount, 37);
                    while (mZslRingBuffer.size() > maxFrames) {
                        Image old = mZslRingBuffer.pollFirst();
                        if (old != null) old.close();
                    }
                }
                return;
            }
            if (onUnlimited && !unlimitedStarted) {
                return;
            }

            //This code creates single frame bugs
            //taskResults.removeIf(Future::isDone); //remove already completed results
            //Future<?> result = processExecutor.submit(() -> mImageSaver.initProcess(reader));
            //taskResults.add(result);
            if(PhotonCamera.getSettings().frameCount != 1) {
                //taskResults.removeIf(Future::isDone); //remove already completed results
                //Future<?> result = processExecutor.submit(() -> mImageSaver.initProcess(reader));
                //taskResults.add(result);
                //processExecutor.execute(() -> mImageSaver.initProcess(reader));
                mImageSaver.initProcess(reader);
                //mBackgroundHandler.post(() -> mImageSaver.initProcess(reader));
                //AsyncTask.execute(() -> mImageSaver.initProcess(reader));
            }
            else {
                mBackgroundHandler.post(() -> mImageSaver.initProcess(reader));
                //mImageSaver.initProcess(reader);
                //processExecutor.execute(() -> mImageSaver.initProcess(reader));
            }
        }

    };
    private Range<Integer> FpsRangeAuto;
    /** Last seen CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES copy, for range validation. */
    private Range<Integer>[] mAvailableFpsRanges;
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
    private GLPreview mTextureView;
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
    /** True when the current MediaRecorder setup requested 10-bit HDR (Main10). */
    private boolean mVideoHdrActive = false;
    /** Configured video+audio bitrates and frame rate of the current recording setup. */
    private int mVideoBitrateBps = 0;
    private int mAudioBitrateBps = 0;
    /** Audio source used by the current setup + one-shot MIC retry override. */
    private int mAudioSourceUsed = MediaRecorder.AudioSource.CAMCORDER;
    private int mAudioSourceRetry = -1;
    private int mVideoFrameRate = 30;
    /** Actual size passed to setVideoSize() for the current recording setup. */
    private android.util.Size mVideoSize;
    /** Recording start (elapsedRealtime) + UI ticker for the REC badge. */
    private long mVideoRecordStartMs = 0;
    private android.os.Handler mVideoRecTickHandler;
    private Runnable mVideoRecTickRunnable;
    private Size target;
    public float mFocus;
    public int mPreviewAFMode;
    public int mPreviewAEMode;
    public MeteringRectangle[] mPreviewMeteringAF;
    public MeteringRectangle[] mPreviewMeteringAE;
    private MeteringRectangle[] mInitialMeteringAF;
    private MeteringRectangle[] mInitialMeteringAE;
    /**
     * The {@link Size} of camera preview.
     */
    public Size mPreviewSize;
    public Size mBufferSize;
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
                case STATE_PREVIEW:
                    previewProcess();
                    break;
                case STATE_WAITING_LOCK:
                    waitingLockProcess(result);
                    break;
                case STATE_WAITING_PRECAPTURE:
                    waitingPrecaptureProcess(result);
                    break;
                case STATE_WAITING_NON_PRECAPTURE:
                    waitingNonPrecaptureProcess(result);
                    break;
            }
        }

        private void previewProcess() {
            // We have nothing to do when the camera preview is working normally.
            //Log.v(TAG, "PREVIEW");
        }

        private void waitingLockProcess(CaptureResult result) {
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
        }

        private void waitingPrecaptureProcess(CaptureResult result) {
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
        }

        private void waitingNonPrecaptureProcess(CaptureResult result) {
            // CONTROL_AE_STATE can be null on some devices
            Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
            if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                mState = STATE_PICTURE_TAKEN;
                captureStillPicture();
            }
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                        @NonNull CaptureRequest request,
                                        @NonNull CaptureResult partialResult) {

            process(partialResult);
            if (mTouchFocus != null) {
                mTouchFocus.onCaptureResult(partialResult);
            }
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

            RggbChannelVector halGains = result.get(CaptureResult.COLOR_CORRECTION_GAINS);
            ColorSpaceTransform halTransform = result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM);
            Integer halAwb = result.get(CaptureResult.CONTROL_AWB_MODE);
            Integer halColorMode = result.get(CaptureResult.COLOR_CORRECTION_MODE);
            Rational[] halNeutralPoint = result.get(CaptureResult.SENSOR_NEUTRAL_COLOR_POINT);

            /** Spawning dozens of temp arrays and strings 60 fps puts a lot of pressure on the Android GC, 
                and on weaker chips it can cause viewfinder hiccups over time.
                
            int estimatedKelvin = ColorTemperatureConverter.neutralPointToKelvin(halNeutralPoint);

            if (paramController == null || paramController.WB == 0) {
                Log.d("WB_COMPARE_DEBUG", "[AUTO AWB] Scene Estimated K=" + estimatedKelvin
                        + " | GAINS=" + halGains
                        + " | TRANSFORM=" + halTransform
                        + " | NEUTRAL_POINT=" + Arrays.toString(halNeutralPoint));
            } else {
                Log.d("WB_COMPARE_DEBUG", "[MANUAL WB Target=" + paramController.WB + "K] (HAL Measured K=" + estimatedKelvin + ")"
                        + " | GAINS=" + halGains
                        + " | TRANSFORM=" + halTransform
                        + " | NEUTRAL_POINT=" + Arrays.toString(halNeutralPoint));
            }
            */

            VendorTagUtils.resultSessionApply(result, getTunablePhysicalId());
            process(result);
            if (mTouchFocus != null) {
                mTouchFocus.onCaptureResult(result);
            }
            cameraEventsListener.onPreviewCaptureCompleted(result);
            if(PreferenceKeys.getAfMode() == CaptureRequest.CONTROL_AF_MODE_AUTO && !burst && (mTouchFocus == null || !mTouchFocus.isTouchFocus)
                    && PhotonCamera.getSettings().selectedMode != CameraMode.VIDEO) {
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
                rebuildPreviewBuilderOneShot();
            }
        }

    };
    /**
     * Creates a {@link CameraDevice.StateCallback} bound to the {@link #openToken}
     * it was issued with. Callbacks of a superseded open are ignored (apart from
     * releasing the open/close permit they hold), so a slow device can never
     * clobber the state of a newer one.
     */
    private CameraDevice.StateCallback makeStateCallback(final int token) {
        return new CameraDevice.StateCallback() {

            @Override
            public void onOpened(@NonNull CameraDevice cameraDevice) {
                handleCameraOpened(cameraDevice, token);
            }

            @Override
            public void onDisconnected(@NonNull CameraDevice cameraDevice) {
                handleCameraDisconnected(cameraDevice, token);
            }

            @Override
            public void onError(@NonNull CameraDevice cameraDevice, int error) {
                handleCameraError(cameraDevice, token, error);
            }
        };
    }

    private void handleCameraOpened(@NonNull CameraDevice cameraDevice, int token) {
        mCameraOpenCloseLock.release();
        if (token != openToken.get() || !isCameraResumed) {
            // The app was backgrounded (or the open was superseded) while this
            // open was in flight; a hidden/stale open must not hold the device.
            Log.d(TAG, "onOpened(): stale or backgrounded open, closing device");
            cameraDevice.close();
            return;
        }
        mCameraOpening.set(false);
        // A newer lens was queued while this open was in flight: drop this
        // device without building a preview session and switch right away.
        LensSwitchScheduler.Request superseding = lensSwitchScheduler.pollSuperseding();
        if (superseding != null) {
            Log.d(TAG, "onOpened(): superseded by " + superseding.cameraId + ", reopening");
            cameraDevice.close();
            runOnMain(() -> runLensSwitchCycle(superseding));
            return;
        }
        disconnectRecoveries.set(0);
        mCameraDevice = cameraDevice;
        mImageSaver = new ImageSaver(cameraEventsListener);
        createCameraPreviewSession(false);
    }

    private void handleCameraDisconnected(@NonNull CameraDevice cameraDevice, int token) {
        mCameraOpenCloseLock.release();
        cameraDevice.close();
        if (token != openToken.get()) return;
        mCameraOpening.set(false);
        if (mCameraDevice == cameraDevice) mCameraDevice = null;
        if (!isCameraResumed) return;
        if (lensSwitchScheduler.isActive()) {
            // The device dropped while a lens-switch cycle owned it: fail the
            // cycle so it retries instead of leaving the pipeline active
            // forever with no session.
            Log.w(TAG, "onDisconnected(): in-flight lens switch dropped, retrying");
            handleOpenFailure(token);
        } else {
            Log.w(TAG, "onDisconnected(): camera device dropped, scheduling recovery");
            scheduleCameraRecovery();
        }
    }

    private void handleCameraError(@NonNull CameraDevice cameraDevice, int token, int error) {
        mCameraOpenCloseLock.release();
        cameraDevice.close();
        if (token != openToken.get()) return;
        mCameraOpening.set(false);
        if (mCameraDevice == cameraDevice) mCameraDevice = null;
        Log.e(TAG, "onError(): cameraDevice = [" + cameraDevice + "], error = [" + error + "]");
        if (!isCameraResumed) return;
        if (lensSwitchScheduler.isActive()) {
            // The failure belongs to an in-flight lens-switch open: retry it.
            handleOpenFailure(token);
        } else {
            // The active preview device failed; reopen it with backoff.
            scheduleCameraRecovery();
        }
    }
    /**
     * {@link TextureView.SurfaceTextureListener} handles several lifecycle events on a
     * {@link TextureView}.
     */
    public final TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture texture, int width, int height) {
            // The availability callback is delivered through the main-thread
            // handler; if the GL surface was recreated in the meantime, verify
            // against an already active texture before dropping the request.
            SurfaceTexture currentTexture = (mTextureView != null) ? mTextureView.getSurfaceTexture() : null;
            if (currentTexture != null && texture != currentTexture) {
                Log.d(TAG, "onSurfaceTextureAvailable(): stale texture ignored");
                return;
            }
            try {
                String curID = PhotonCamera.getSettings().mCameraID;
                parseCameraIds(curID);

                Log.d(TAG, "ID:" + mCameraCharacteristicsMap.get(physicalID));
                // list available characteristics ids
                for (String id : mCameraCharacteristicsMap.keySet()) {
                    Log.d(TAG, "Available camera ID: " + id);
                }
                CameraCharacteristics chars = mCameraCharacteristicsMap.get(physicalID);
                if (chars == null) {
                    Log.e(TAG, "No characteristics for physicalID=" + physicalID
                            + " (mCameraID=" + PhotonCamera.getSettings().mCameraID + "). Falling back to first available.");
                    if (!mCameraCharacteristicsMap.isEmpty()) {
                        Map.Entry<String, CameraCharacteristics> first = mCameraCharacteristicsMap.entrySet().iterator().next();
                        physicalID = first.getKey();
                        logicalID = physicalID;
                        PhotonCamera.getSettings().mCameraID = physicalID;
                        chars = first.getValue();
                    } else {
                        showToast("No cameras available");
                        return;
                    }
                }
                Size optimal = getPreviewOutputSize(getSafeDisplay(), chars,
                        PhotonCamera.getSettings().selectedMode);
                openCamera(optimal.getWidth(), optimal.getHeight());
            } catch (Exception e){
                Log.e(TAG,Log.getStackTraceString(e));
                showToast("Error onSurfaceTextureAvailable:"+e.getLocalizedMessage());
            }
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
    public CaptureController(Activity activity, ExecutorService processExecutor, CameraEventsListener cameraEventsListener) {
        if(PhotonCamera.getSettings().previewFormat != 0) {
            mPreviewTargetFormat = PhotonCamera.getSettings().previewFormat;
        } else {
            mPreviewTargetFormat = ImageFormat.JPEG;
        }
        this.activity = activity;
        this.cameraEventsListener = cameraEventsListener;
        this.mTextureView = activity.findViewById(R.id.texture);
        this.mCameraManager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        this.mCameraManager2 = new CameraManager2(mCameraManager, PhotonCamera.getInstance(activity).getSettingsManager());
        PreferenceKeys.addIds(mCameraManager2.getCameraIdList());

        this.processExecutor = processExecutor;
        this.paramController = new ParamController(this);

        this.fillInCameraCharacteristics();
    }

    /**
     * Fills in {@link CaptureController#mCameraCharacteristicsMap} that is used in
     * {@link CaptureController#UpdateCameraCharacteristics}.
     */
    private void fillInCameraCharacteristics() {
        try {
            String[] cameraIds = mCameraManager2.getCameraIdList();
            for (String cameraId : cameraIds) {
                String physicalID = cameraId;
                if(cameraId.contains("-")){
                    physicalID = cameraId.split("-")[1];
                }
                mCameraCharacteristicsMap.put(physicalID, mCameraManager.getCameraCharacteristics(physicalID));
            }
        } catch (CameraAccessException cameraAccessException) {
            // Should not be possible to get here but anyway
            cameraAccessException.printStackTrace();
            showToast("Failed to fetch camera characteristics: " + cameraAccessException.getLocalizedMessage());
        }

    }

    public ManualModeConsole getManualModeConsole() {
        return manualModeConsole;
    }

    public void setManualModeConsole(ManualModeConsole manualModeConsole) {
        this.manualModeConsole = manualModeConsole;
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
        int targetWidth = aspectRatio.getWidth();
        int targetHeight = aspectRatio.getHeight();
        for (Size option : choices) {
            int width = option.getWidth();
            int height = option.getHeight();
            boolean isAspectRatioMatching = (height * targetWidth == width * targetHeight);

            if (width <= maxWidth && height <= maxHeight && isAspectRatioMatching) {
                if (width >= textureViewWidth && height >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        // Pick the smallest of those big enough.
        // If there is no one big enough, pick the largest of those not big enough.
        if (!bigEnough.isEmpty()) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (!notBigEnough.isEmpty()) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    private Size getCameraOutputSize(Size[] sizes) {
        if (sizes != null) {
            if (sizes.length > 0) {
                Arrays.sort(sizes, new CompareSizesByArea());

                int largestSizeIdx = sizes.length - 1;
                int largestSizeArea = sizes[largestSizeIdx].getWidth() * sizes[largestSizeIdx].getHeight();

                if (largestSizeArea <= ResolutionSolution.highRes) {
                    target = sizes[largestSizeIdx];
                    return target;
                } else if (sizes.length > 1) {
                    target = sizes[largestSizeIdx - 1];
                    return target;
                }
            }
        }
        return null;
    }

    /**
     * For test method {@link CaptureController#getCameraOutputSize(Size[])}
     */
    @TestOnly
    private static Size getCameraOutputSizeTest(Size[] sizes) {
        if (sizes != null) {
            if (sizes.length > 0) {
                Arrays.sort(sizes, new CompareSizesByArea());

                int largestSizeIdx = sizes.length - 1;
                int largestSizeArea = sizes[largestSizeIdx].getWidth() * sizes[largestSizeIdx].getHeight();

                if (largestSizeArea <= ResolutionSolution.highRes) {
                    return sizes[largestSizeIdx];
                } else if (sizes.length > 1) {
                    return sizes[largestSizeIdx - 1];
                }
            }
        }
        return null;
    }

    private Size getCameraOutputSize(Size[] sizes, Size previewSize) {
        if (sizes == null || sizes.length == 0) return previewSize;

        Arrays.sort(sizes, new CompareSizesByArea());
        int largestSizeIdx = sizes.length - 1;
        int largestSizeArea = sizes[largestSizeIdx].getWidth() * sizes[largestSizeIdx].getHeight();

        if (PhotonCamera.getSettings().QuadBayer) {
            target = sizes[largestSizeIdx];
            Rect preCorrectionActiveArraySize = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE);
            Rect activeArraySize = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
            if (preCorrectionActiveArraySize != null && activeArraySize != null) {
                double k = (double) (target.getHeight()) / activeArraySize.bottom;
                mul(preCorrectionActiveArraySize, k);
                mul(activeArraySize, k);
                CameraReflectionApi.set(mCameraCharacteristics, CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE, activeArraySize);
                CameraReflectionApi.set(mCameraCharacteristics, CameraCharacteristics.SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE, preCorrectionActiveArraySize);
            }
            return target;
        }

        int[] preferred = PhotonCamera.getSpecificSensor().selectedSensorSpecifics.preferredResolution;
        if (preferred != null && preferred.length >= 2) {
            for (Size size : sizes) {
                if (size.getWidth() == preferred[0] && size.getHeight() == preferred[1]) {
                    target = size;
                    return target;
                }
            }
        }

        if (largestSizeArea <= ResolutionSolution.highRes) {
            target = sizes[largestSizeIdx];
            return target;
        } else if (sizes.length > 1) {
            target = sizes[largestSizeIdx - 1];
            return target;
        }
        return previewSize;
    }

    /**
     * For test method {@link CaptureController#getCameraOutputSize(Size[], Size)}
     */
    @TestOnly
    private static Size getCameraOutputSizeTest(Size[] sizes, Size previewSize) {
        if (sizes == null || sizes.length == 0) return previewSize;

        Size temp = null;

        Arrays.sort(sizes, new CompareSizesByArea());
        int largestSizeIdx = sizes.length - 1;
        int largestSizeArea = sizes[largestSizeIdx].getWidth() * sizes[largestSizeIdx].getHeight();

        if (largestSizeArea <= ResolutionSolution.highRes || PhotonCamera.getSettings().QuadBayer) {
            temp = sizes[largestSizeIdx];
            if (PhotonCamera.getSettings().QuadBayer) {
                Rect preCorrectionActiveArraySize = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE);
                Rect activeArraySize = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);

                if (preCorrectionActiveArraySize != null && activeArraySize != null) {
                    double k = (double) (temp.getHeight()) / activeArraySize.bottom;
                    mulForTest(preCorrectionActiveArraySize, k);
                    mulForTest(activeArraySize, k);
                    CameraReflectionApi.set(mCameraCharacteristics, CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE, activeArraySize);
                    CameraReflectionApi.set(mCameraCharacteristics, CameraCharacteristics.SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE, preCorrectionActiveArraySize);
                }
            }
            return temp;
        } else if (sizes.length > 1) {
            temp = sizes[largestSizeIdx - 1];
            return temp;
        }
        return previewSize;
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
            String curID = PhotonCamera.getSettings().mCameraID;
            parseCameraIds(curID);

            UpdateCameraCharacteristics(physicalID);
            //Thread thr = new Thread(mImageSaver);
            //thr.start();
        } catch (Exception e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            Log.e(TAG, Log.getStackTraceString(e));
            showToast(activity.getString(R.string.camera_error));
            //cameraEventsListener.onError(R.string.camera_error);
        }
    }

    /**
     * Closes the current {@link CameraDevice}.
     */
    public void closeCamera() {
        mCameraOpening.set(false);
        isCameraResumed = false;
        cancelLogicalZoom();
        mLogicalRenderRatio = 0f;
        // Cancel any queued/delayed lens switch and invalidate in-flight
        // callbacks so a pending open cannot resurrect the camera while paused.
        lensSwitchScheduler.cancel();
        cycleStartScheduled = false;
        cycleStartRequestedMs = 0L;
        disconnectRecoveries.set(0);
        openToken.incrementAndGet();
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
            }
            if (null != mImageReaderRaw) {
                if (!isProcessing) {
                    mImageReaderRaw.close();
                    mImageReaderRaw = null;
                }
            }
            if (null != mMediaRecorder) {
                mMediaRecorder.release();
                mMediaRecorder = null;
            }
            if (surface != null) {
                surface.release();
                surface = null;
            }
            mState = STATE_CLOSED;
        } catch (InterruptedException e) {
            Log.e(TAG, Log.getStackTraceString(e));
            Thread.currentThread().interrupt();
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
            Log.e(TAG, Log.getStackTraceString(e));
        }
    }

//    public void rebuildPreview() {
//        try {
////            mCaptureSession.stopRepeating();
//            mCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback, mBackgroundHandler);
//        } catch (CameraAccessException e) {
//            Log.e(TAG, Log.getStackTraceString(e));
//        }
//    }

    private Range<Integer> getSelectedFpsRange() {
        Range<Integer> wanted;
        switch (PhotonCamera.getSettings().fpsMode) {
            case 1: wanted = new Range<>(24, 24); break;
            case 2: wanted = new Range<>(30, 30); break;
            case 3: wanted = new Range<>(60, 60); break;
            default: wanted = null; break;
        }
        if (wanted == null) {
            return FpsRangeAuto != null ? FpsRangeAuto : new Range<>(14, 30);
        }
        // Fixed singletons must exist on the HAL; otherwise fall back instead
        // of failing session configuration.
        try {
            if (mAvailableFpsRanges != null) {
                for (Range<Integer> r : mAvailableFpsRanges) {
                    if (wanted.equals(r)) return wanted;
                }
                // Prefer any range with the same upper bound (same frame rate).
                for (Range<Integer> r : mAvailableFpsRanges) {
                    if (r != null && wanted.getUpper().equals(r.getUpper())) return r;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "fps range validation failed", e);
        }
        Log.w(TAG, "fps range " + wanted + " unsupported, falling back to auto");
        return FpsRangeAuto != null ? FpsRangeAuto : new Range<>(14, 30);
    }
    
    public void rebuildPreviewBuilder() {
        if(burst) return;
        if (mPreviewRequestBuilder == null || mCaptureSession == null) {
            return;
        }
        try {
//            mCaptureSession.stopRepeating();
            mCaptureSession.setRepeatingRequest(mPreviewInputRequest = mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);
        } catch (IllegalStateException | IllegalArgumentException | NullPointerException e) {
            Logger.warnShort(TAG, "Cannot rebuildPreviewBuilder()!", e);
        } catch (CameraAccessException e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
    }

     public void rebuildPreviewBuilderOneShot() {
        if(burst) return;
        if (mPreviewRequestBuilder == null || mCaptureSession == null) {
            return;
        }
        try {
            Log.d(TAG, "rebuildPreviewBuilderOneShot: " + mCaptureSession + " " + mPreviewRequestBuilder + " " + mCaptureCallback + " " + mBackgroundHandler);
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);
        } catch (IllegalStateException | IllegalArgumentException | NullPointerException e) {
            Logger.warnShort(TAG, "Cannot rebuildPreviewBuilderOneShot()!", e);
        } catch (CameraAccessException e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
    }

    /**
     * Applies the current zoom ratio to a capture request builder so the
     * viewfinder (and any replay of the request) matches the crop that will be
     * taken out of the stored RAW buffer. Uses a scaled sensor crop region
     * because a {@code SCALER_CROP_REGION} in active-array coordinates is the
     * widest-compatible representation.
     *
     * @param builder the request builder to modify (preview or still capture)
     */
    public void applyZoom(CaptureRequest.Builder builder) {
        applyZoom(builder, true);
    }

    public void applyZoom(CaptureRequest.Builder builder, boolean isPreview) {
        if (builder == null) return;
        if (!isPreview) {
            return;
        }
        CameraCharacteristics chars = mCameraCharacteristics;
        if (chars == null) return;
        Rect activeArray = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        if (activeArray == null) return;
        try {
            if (isVideoLogicalActive()
                    && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Member switching rides CONTROL_ZOOM_RATIO on the open
                // logical device: identity crop, continuous ratio. A digital
                // crop here would double-zoom.
                applyZoomLogical(builder, zoomController.getZoomRatio());
                return;
            }
            // SCALER_CROP_REGION is used for all API levels because it supports a
            // pinch focal point (CONTROL_ZOOM_RATIO always zooms centred) AND its
            // width/height are scaled by the same factor, so it preserves the
            // active-array aspect ratio. CONTROL_ZOOM_RATIO is also set on API 30+
            // so the HUD can read it back consistently.
            // The crop region uses the DIGITAL zoom (always >= 1.0); the effective
            // zoom is a combination of the physical lens and this digital crop.
            builder.set(CaptureRequest.SCALER_CROP_REGION, zoomController.computeSensorCrop(activeArray));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, zoomController.getDigitalZoom());
            }
        } catch (IllegalArgumentException e) {
            Log.d(TAG, "applyZoom: key not supported, skipping. " + e.getLocalizedMessage());
        }
    }

    /**
     * Logical-mode zoom apply with an explicit ratio (used by the smooth-zoom
     * animator for intermediate values). Identity crop; the ratio carries the
     * zoom. Also records the last submitted ratio for animation seeding.
     */
    private void applyZoomLogical(CaptureRequest.Builder builder, float ratio) {
        if (builder == null) return;
        CameraCharacteristics chars = mCameraCharacteristics;
        if (chars == null) return;
        Rect activeArray = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        if (activeArray == null) return;
        try {
            builder.set(CaptureRequest.SCALER_CROP_REGION, new Rect(activeArray));
            try {
                android.util.Range<Float> range =
                        LogicalCameraResolver.getLogicalZoomRatioRange(
                                activity, PreferenceKeys.getVideoLogicalId());
                if (range != null) {
                    ratio = Math.max(range.getLower(), Math.min(range.getUpper(), ratio));
                }
            } catch (Exception ignored) {
            }
            builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, ratio);
            mLogicalRenderRatio = ratio;
        } catch (IllegalArgumentException e) {
            Log.d(TAG, "applyZoomLogical: key not supported, skipping. " + e.getLocalizedMessage());
        }
    }

    /**
     * Updates the effective zoom (and pinch focus point), then re-submits the
     * repeating preview request so the change is applied live. If the target
     * crosses a physical-lens threshold, the lens switch is triggered and the
     * preview update is deferred until the new lens reopens. The pinch gesture
     * uses the sticky path.
     *
     * @param ratio  target effective zoom ratio (may be &lt; 1.0 for ultra-wide)
     * @param focusX normalized pinch focus X in [0,1]
     * @param focusY normalized pinch focus Y in [0,1]
     */
    public void setZoom(float ratio, float focusX, float focusY) {
        setZoom(ratio, focusX, focusY, true);
    }

    /**
     * Updates the effective zoom with explicit control over lens stickiness,
     * then re-submits the repeating preview request so the change is applied
     * live. If the target crosses a physical-lens threshold, the lens switch
     * is triggered and the preview update is deferred until the new lens
     * reopens.
     *
     * @param ratio  target effective zoom ratio (may be &lt; 1.0 for ultra-wide)
     * @param focusX normalized focus X in [0,1]
     * @param focusY normalized focus Y in [0,1]
     * @param sticky true for detent snap + hysteresis (pinch), false for smooth zoom (slider).
     *               Stickiness is always bypassed in video mode.
     */
    public void setZoom(float ratio, float focusX, float focusY, boolean sticky) {
        cancelLogicalZoom();
        String switchTo = zoomController.setTargetZoom(ratio, focusX, focusY, sticky && !isVideoMode());
        if (switchTo != null) {
            requestLensSwitch(switchTo);
            return;
        }
        if (mPreviewRequestBuilder != null) {
            applyZoom(mPreviewRequestBuilder);
            rebuildPreviewBuilder();
        }
    }

    /** Effective zoom (what the indicator and pinch gesture operate on). */
    public float getZoomRatio() {
        return zoomController.getZoomRatio();
    }

    /** Minimum effective zoom of the active facing's lens set. */
    public float getMinZoom() {
        return zoomController.getMinZoom();
    }

    /** Maximum effective zoom of the active facing's lens set. */
    public float getMaxZoom() {
        return zoomController.getMaxZoom();
    }

    /** True when zoom is locked to the current lens (no auto lens-switch on zoom). */
    public boolean isLensSwitchLocked() {
        return zoomController.isLensSwitchLocked();
    }

    public void setLensSwitchLocked(boolean locked) {
        zoomController.setLensSwitchLocked(locked);
    }

    public void resetZoom() {
        cancelLogicalZoom();
        zoomController.resetToActiveLensNative();
        if (mPreviewRequestBuilder != null) {
            applyZoom(mPreviewRequestBuilder);
            rebuildPreviewBuilder();
        }
    }

    /**
     * Builds the physical-lens model for the zoom controller from the lens data
     * map, restricted to a single facing, and marks the currently open lens.
     *
     * @param lensDataMap  cameraId -> CameraLensData (all facing lens metadata)
     * @param activeFacing the LENS_FACING_* of the currently open camera
     */
    public void configureZoomLenses(Map<String, CameraLensData> lensDataMap, int activeFacing) {
        cancelLogicalZoom();
        mLogicalRenderRatio = 0f;
        mLogicalMemberPhysical = null;
        mActiveLogicalMemberId = null;
        List<ZoomController.LensEntry> entries = new ArrayList<>();
        if (lensDataMap != null) {
            for (Map.Entry<String, CameraLensData> e : lensDataMap.entrySet()) {
                CameraLensData lens = e.getValue();
                if (lens.getFacing() != activeFacing) continue;
                entries.add(new ZoomController.LensEntry(
                        e.getKey(), lens.getZoomFactor(), readMaxDigitalZoom(e.getKey())));
            }
        }
        zoomController.setLenses(entries);
        zoomController.setActiveLens(PhotonCamera.getSettings().mCameraID);
    }

    /**
     * Applies the In-Sensor Zoom (ISZ) CaptureRequest key when the currently
     * active camera id is an ISZ virtual lens. The key makes the physical sensor
     * perform the zoom in-sensor; the zoom ratio is informational only (it only
     * composes the displayed zoom factor) so no crop is applied here.
     *
     * @param builder    the request builder being configured (preview or capture)
     * @param physicalID the physical camera id for the session
     */
    private void applyIszIfActive(CaptureRequest.Builder builder, String physicalID) {
        try {
            String cameraId = PhotonCamera.getSettings().mCameraID;
            if (builder == null || cameraId == null || !CameraManager2.isIszVirtual(cameraId)) return;
            int sensorId = -1;
            try {
                sensorId = Integer.parseInt(physicalID);
            } catch (NumberFormatException ignored) {
                return;
            }
            SpecificSettingSensor isz = PhotonCamera.getSpecificSensor().getIszForSensor(sensorId);
            if (isz == null || isz.iszKey == null) return;
            // Apply a copy so the shared SensorSpecifics config is not mutated.
            VendorTagUtils.TunableKey key = new VendorTagUtils.TunableKey(
                    isz.iszKey.type, isz.iszKey.name, VendorTagUtils.TunableKey.classForValueType(isz.iszKey.valueType), isz.iszKey.parseValue());
            Log.d(TAG, "Applying ISZ key " + key.name + " = " + key.value + " (" + key.valueType + ") for sensor " + sensorId);
            VendorTagUtils.applyTunableKeys(builder, Collections.singletonList(key), physicalID);
        } catch (Exception e) {
            Log.d(TAG, "applyIszIfActive: " + Log.getStackTraceString(e));
        }
    }

    /** Reads the max digital zoom for a (possibly composite) camera id. */
    private float readMaxDigitalZoom(String cameraId) {
        String physical = cameraId;
        if (cameraId != null && cameraId.contains("-")) {
            physical = cameraId.split("-")[1];
        }
        CameraCharacteristics chars = mCameraCharacteristicsMap.get(physical);
        if (chars == null) return 1f;
        Float max = chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
        return max != null && max > 0f ? Math.max(max, 20f) : 20f;
    }

    /** Handles a physical lens switch requested by the zoom controller. */
    private void requestLensSwitch(String cameraId) {
        cancelLogicalZoom();
        if (cameraId != null && LogicalCameraResolver.isMemberId(cameraId) && isVideoLogicalActive()) {
            applyLogicalMemberSwitch(cameraId);
            return;
        }
        Log.d(TAG, "requestLensSwitch -> " + cameraId);
        enqueueLensSwitch(cameraId, true);
    }

    /**
     * Masks the physical sensor's readout-mode switch when changing onto an
     * ISZ virtual lens: the presented frame stays frozen while newly arrived
     * frames are latched-and-dropped on the GL thread until the sensor has
     * settled, then live rendering resumes on its own. The pipeline is never
     * gated, so this mask cannot stall or wedge the preview; a tracking flag
     * left over with no frames is inert and reset by the next arm.
     *
     * <p>Arms only for genuine lens changes onto a virtual lens (zoom-driven
     * or manual). Everything else leaves any in-flight tracking to finish (or
     * reset) on its own.
     */
    private void armOrCancelIszTransition() {
        String targetId = PhotonCamera.getSettings().mCameraID;
        boolean switchingLens = zoomDrivenLensSwitch
                || (targetId != null && !targetId.equals(zoomController.getActiveLensId()));
        if (targetId != null && CameraManager2.isIszVirtual(targetId) && switchingLens && mTextureView != null) {
            mTextureView.beginPreviewSettleTracking();
        }
    }

    public boolean isZoomDrivenLensSwitch() {
        return zoomDrivenLensSwitch;
    }

    public void clearZoomDrivenLensSwitch() {
        zoomDrivenLensSwitch = false;
    }

    /**
     * Configures the necessary {@link Matrix} transformation to `mTextureView`.
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
        //mTextureView.setTransform(matrix);
        mTextureView.setOrientation(mSensorOrientation+90);
        updatePreviewMirror();
    }

    private void updatePreviewMirror() {
        if (mTextureView == null || mCameraCharacteristics == null) {
            return;
        }
        Integer facing = mCameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
        boolean mirror = facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT;
        mTextureView.setMirror(mirror);
    }

    private ArrayList<Size> getAllTargets(){
        CameraCharacteristics characteristics =  this.mCameraCharacteristicsMap.get(physicalID);
        StreamConfigurationMap map = characteristics.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        ArrayList<Size> allTargets = new ArrayList<>();

        Size[] targetSizes = map.getOutputSizes(mTargetFormat);
        if(targetSizes != null)
            allTargets.addAll(Arrays.asList(targetSizes));
        if(PhotonCamera.getSettings().QuadBayer) {
            useMaximumResolutionKey = false;
            int[] capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
            for (int capability : capabilities) {
                if (capability == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_ULTRA_HIGH_RESOLUTION_SENSOR) {
                    Size arraySize = null;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        arraySize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE_MAXIMUM_RESOLUTION);
                    }
                    if(arraySize != null) {
                        useMaximumResolutionKey = true;
                        allTargets.add(arraySize);
                    }
                }
            }
            if(!useMaximumResolutionKey) {
                Size[] highResSizes = map.getHighResolutionOutputSizes(mTargetFormat);
                // Extend targetSizes with high resolution sizes
                if (highResSizes != null && highResSizes.length > 0) {
                    allTargets.addAll(Arrays.asList(highResSizes));
                }
                var keys = CameraReflectionApi.getCameraCharacteristicsKeys(characteristics, null, true);
                for (Object keyObj : keys) {
                    try {
                        if (keyObj instanceof CameraCharacteristics.Key<?>) {
                            CameraCharacteristics.Key<?> key = (CameraCharacteristics.Key<?>) keyObj;
                            if (key.getName().contains("StreamConfigurations")) {
                                Object res = characteristics.get(key);
                                int[] vals = (int[]) res;
                                for (int i = 0; i < vals.length; i += 4) {
                                    int format = vals[i];
                                    int width = vals[i + 1];
                                    int height = vals[i + 2];
                                    if (format == mTargetFormat) {
                                        allTargets.add(new Size(width, height));
                                        Log.d(TAG, "Added custom resolution(" + key.getName() + "):" + width + " " + height);
                                    }
                                }
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        return allTargets;
    }
    /**
     * Requests a camera restart (manual lens pill/flip, settings or mode
     * change). Restarts are serialized and coalesced with zoom-driven lens
     * switches so a change made mid-pinch can never overlap an in-flight
     * close/open cycle.
     */
    public void restartCamera() {
        Log.d(TAG, "restartCamera() called from \"" + Thread.currentThread().getName() + "\" Thread");
        cancelLogicalZoom();
        enqueueLensSwitch(PhotonCamera.getSettings().mCameraID, false);
    }

    /**
     * Queues a camera (re)start. Only one close/open cycle runs at a time;
     * requests arriving mid-cycle update a pending target (last request wins).
     *
     * @param cameraId   target (possibly composite) camera id
     * @param zoomDriven true for a zoom-driven lens switch; false for a manual
     *                   restart (which must re-run the setup path even when
     *                   the lens id is unchanged)
     */
    private void enqueueLensSwitch(String cameraId, boolean zoomDriven) {
        if (cameraId == null) return;
        cancelLogicalZoom();
        if (lensSwitchScheduler.request(cameraId, zoomDriven)) {
            scheduleCycleStart();
        }
    }

    private void runOnMain(Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) r.run();
        else mMainHandler.post(r);
    }

    /**
     * Starts a queued cycle once no other open is in flight. A cold open issued
     * by {@link #openCamera(int, int)} temporarily owns the device slot; the
     * cycle waits (briefly) for it to complete instead of opening a second
     * device on top of it, which is what crashes the camera service.
     */
    private void scheduleCycleStart() {
        runOnMain(() -> {
            if (!isCameraResumed) {
                lensSwitchScheduler.cancel();
                return;
            }
            if (mCameraOpening.get()) {
                if (cycleStartRequestedMs == 0L) {
                    cycleStartRequestedMs = SystemClock.elapsedRealtime();
                }
                if (SystemClock.elapsedRealtime() - cycleStartRequestedMs > CYCLE_WAIT_TIMEOUT_MS) {
                    // The in-flight open is taking too long. Stop polling but
                    // keep the request queued: if the open ever completes, its
                    // callback promotes the request; if it fails, the error
                    // path does. This avoids dropping a pinch and leaving the
                    // zoom state anchored to a lens that never opened.
                    Log.w(TAG, "scheduleCycleStart(): in-flight open still busy, deferring to its callback");
                    cycleStartRequestedMs = 0L;
                    cycleStartScheduled = false;
                    return;
                }
                if (!cycleStartScheduled) {
                    cycleStartScheduled = true;
                    mMainHandler.postDelayed(() -> {
                        cycleStartScheduled = false;
                        scheduleCycleStart();
                    }, CYCLE_WAIT_MS);
                }
                return;
            }
            cycleStartScheduled = false;
            cycleStartRequestedMs = 0L;
            LensSwitchScheduler.Request req = lensSwitchScheduler.beginNext();
            if (req != null) runLensSwitchCycle(req);
        });
    }

    /**
     * Closes the current session/device and schedules the open of {@code req}
     * after the HAL settle delay. Runs on the main thread; only one such cycle
     * can be active because the scheduler only promotes a request when idle.
     */
    @SuppressLint("MissingPermission")
    private void runLensSwitchCycle(LensSwitchScheduler.Request req) {
        Log.d(TAG, "runLensSwitchCycle(" + req + ") from \"" + Thread.currentThread().getName() + "\"");
        if (!isCameraResumed) {
            lensSwitchScheduler.cancel();
            return;
        }
        PreferenceKeys.setCameraID(req.cameraId);
        zoomDrivenLensSwitch = req.zoomDriven;
        armOrCancelIszTransition();
        CameraFragment.mSelectedMode = PhotonCamera.getSettings().selectedMode;
        if (paramController != null) {
            paramController.onCameraChanged();
        }
        final int token = openToken.incrementAndGet(); // invalidate callbacks of any previous open
        mCameraOpening.set(false); // the device is closed below before reopening
        boolean locked = false;
        try {
            locked = mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS);
            if (!locked) {
                // Never block the UI thread forever on a stuck open/close lock:
                // abort this cycle and let the scheduler idle out.
                Log.e(TAG, "runLensSwitchCycle(): lock timeout, aborting cycle");
                lensSwitchScheduler.cancel();
                return;
            }
            closeCameraResourcesLocked();
            stopBackgroundThread();
            lastCameraCloseMs = SystemClock.elapsedRealtime();
            cameraEventsListener.onCameraRestarted();
        } catch (Exception e) {
            Log.e(TAG, Log.getStackTraceString(e));
            // Do not crash on a lock hiccup: abort and let the next request retry.
            lensSwitchScheduler.cancel();
            return;
        } finally {
            if (locked) {
                try {
                    mCameraOpenCloseLock.release();
                } catch (Exception ignored) {
                    showToast("Failed to release camera");
                }
            }
        }
        startBackgroundThread();
        postDelayedOpen(token, req);
    }

    /** Closes the session, device, readers and surfaces. Caller holds the lock. */
    private void closeCameraResourcesLocked() {
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
        }
        if (null != mImageReaderRaw) {
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
        if (surface != null) {
            surface.release();
            surface = null;
        }
    }

    /**
     * Opens the queued target after the settle delay. The delay doubles as a
     * debounce window: a target queued meanwhile supersedes this open, so a
     * fast sweep across several lenses ends with a single device open.
     */
    private void postDelayedOpen(final int token, final LensSwitchScheduler.Request req) {
        long elapsed = SystemClock.elapsedRealtime() - lastCameraCloseMs;
        long delay = Math.max(0L, LENS_SWITCH_SETTLE_MS - elapsed);
        if (delay > 0) {
            Log.d(TAG, "postDelayedOpen(" + req.cameraId + "): settling for " + delay + "ms");
        }
        final int generation = lensSwitchScheduler.generation();
        mMainHandler.postDelayed(() -> {
            if (token != openToken.get() || generation != lensSwitchScheduler.generation() || !isCameraResumed) {
                return;
            }
            issueLensSwitchOpen(token, req);
        }, delay);
    }

    private void issueLensSwitchOpen(final int token, final LensSwitchScheduler.Request req) {
        if (token != openToken.get() || !isCameraResumed) return;
        LensSwitchScheduler.Request next = lensSwitchScheduler.pollSuperseding();
        if (next != null) {
            // A newer target arrived during the settle window; skip this open.
            runLensSwitchCycle(next);
            return;
        }
        openCameraDevice(token, req.cameraId, req.zoomDriven);
    }

    @SuppressLint("MissingPermission")
    private void openCameraDevice(final int token, String cameraId, boolean zoomDriven) {
        if (token != openToken.get() || !isCameraResumed) return;
        PreferenceKeys.setCameraID(cameraId);
        zoomDrivenLensSwitch = zoomDriven;
        armOrCancelIszTransition();
        parseCameraIds(cameraId);
        // Recreate the ImageReaders / preview sizing before the device is
        // opened. onOpened() runs on the camera background thread and builds
        // the preview session from them, so doing this after openCamera()
        // races the callback and can hand it null readers, which used to abort
        // session creation and strand the whole switch pipeline.
        setUpOutputsAfterOpen();
        boolean acquired = false;
        boolean issued = false;
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                Log.e(TAG, "openCameraDevice(): lock timeout");
            } else {
                acquired = true;
                mCameraOpening.set(true);
                this.mCameraManager.openCamera(logicalID, makeStateCallback(token), mBackgroundHandler);
                issued = true;
            }
        } catch (CameraAccessException e) {
            mCameraOpening.set(false);
            Log.e(TAG, Log.getStackTraceString(e));
        } catch (InterruptedException e) {
            mCameraOpening.set(false);
            Log.e(TAG, Log.getStackTraceString(e));
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            mCameraOpening.set(false);
            Log.e(TAG, Log.getStackTraceString(e));
        } finally {
            // On success the open callback owns the permit; a failed issuance
            // must return it here or the leaked permit blocks every later cycle.
            if (acquired && !issued) {
                mCameraOpenCloseLock.release();
            }
        }
        if (!issued) {
            handleOpenFailure(token);
        }
    }

    /** Splits a composite {@code logical-physical} camera id into its parts. */
    private void parseCameraIds(String cameraId) {
        String effective = cameraId;
        if (effective != null && !LogicalCameraResolver.isMemberId(effective) && isVideoLogicalActive()) {
            // Video logical mode: open the logical device regardless of the
            // selected physical lens. Prefs keep the physical id; only the
            // parsed open ids are overridden here.
            String logical = PreferenceKeys.getVideoLogicalId();
            if (logical != null && !logical.trim().isEmpty()) effective = logical.trim();
        }
        if (effective != null && effective.contains("-")) {
            String[] ids = effective.split("-");
            logicalID = ids[0];
            physicalID = ids[1];
        } else {
            logicalID = effective;
            physicalID = effective;
        }
        if (isVideoLogicalActive() && logicalID != null && !logicalID.isEmpty()) {
            // Stream sizing, orientation, FPS and HDR must come from the
            // logical camera; per-sensor features keep using the member id
            // via getTunablePhysicalId().
            ensureLogicalCharacteristics(logicalID);
            if (mCameraCharacteristicsMap != null) {
                CameraCharacteristics logicalChars = mCameraCharacteristicsMap.get(logicalID);
                if (logicalChars != null) mCameraCharacteristics = logicalChars;
            }
        }
    }

    /**
     * Caches characteristics for a logical camera (plus any missing members)
     * so physical-keyed lookups keep working after the logical override.
     * No-op once cached.
     */
    private void ensureLogicalCharacteristics(String logicalId) {
        try {
            if (logicalId == null || logicalId.isEmpty() || mCameraManager == null) return;
            if (mCameraCharacteristicsMap != null && mCameraCharacteristicsMap.containsKey(logicalId)) {
                return;
            }
            CameraCharacteristics chars = mCameraManager.getCameraCharacteristics(logicalId);
            if (chars == null) return;
            if (mCameraCharacteristicsMap == null) mCameraCharacteristicsMap = new HashMap<>();
            mCameraCharacteristicsMap.put(logicalId, chars);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    for (String pid : chars.getPhysicalCameraIds()) {
                        if (pid != null && !mCameraCharacteristicsMap.containsKey(pid)) {
                            try {
                                CameraCharacteristics member =
                                        mCameraManager.getCameraCharacteristics(pid);
                                if (member != null) mCameraCharacteristicsMap.put(pid, member);
                            } catch (Exception ignored) {
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            }
            Log.d(TAG, "logical characteristics cached for " + logicalId);
        } catch (Exception e) {
            Log.w(TAG, "ensureLogicalCharacteristics failed", e);
        }
    }

    /**
     * Physical id for per-sensor features (tunables, OIS, sensor specifics):
     * the active logical member in video logical mode, else the session
     * physical id.
     */
    private String getTunablePhysicalId() {
        try {
            if (isVideoLogicalActive() && mLogicalMemberPhysical != null
                    && !mLogicalMemberPhysical.isEmpty()) {
                return mLogicalMemberPhysical;
            }
        } catch (Exception ignored) {
        }
        return physicalID;
    }

    /** Post-open characteristic/output setup (mirrors the old restart tail). */
    private void setUpOutputsAfterOpen() {
        try {
            if (mCameraCharacteristics == null) {
                if (mCameraCharacteristicsMap == null || mCameraCharacteristicsMap.isEmpty()) {
                    fillInCameraCharacteristics();
                }
                mCameraCharacteristics = mCameraCharacteristicsMap.get(physicalID);
            }
            Size optimal = getPreviewOutputSize(getSafeDisplay(), mCameraCharacteristics, CameraFragment.mSelectedMode);
            setUpCameraOutputs(optimal.getWidth(), optimal.getHeight());
            configureTransform(optimal.getWidth(), optimal.getHeight());
        } catch (Exception e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
    }

    /** Schedules a bounded retry after an open attempt failed. */
    private void handleOpenFailure(final int token) {
        if (token != openToken.get()) return;
        LensSwitchScheduler.Request retry = lensSwitchScheduler.onFailure();
        if (retry == null) {
            Log.e(TAG, "handleOpenFailure(): open retries exhausted");
            showToast(activity.getString(R.string.camera_error));
            return;
        }
        long delay = OPEN_RETRY_BASE_MS << Math.min(retry.attempt - 1, 3);
        Log.d(TAG, "handleOpenFailure(): retrying " + retry.cameraId + " in " + delay + "ms");
        mMainHandler.postDelayed(() -> {
            if (token != openToken.get() || !isCameraResumed) return;
            issueLensSwitchOpen(token, retry);
        }, delay);
    }

    /**
     * Reopens the camera after an unexpected disconnect (e.g. the camera
     * service restarted). Bounded so a persistently failing HAL cannot loop.
     */
    private void scheduleCameraRecovery() {
        if (!isCameraResumed) return;
        final int attempt = disconnectRecoveries.incrementAndGet();
        if (attempt > MAX_DISCONNECT_RECOVERIES) {
            Log.e(TAG, "scheduleCameraRecovery(): recovery attempts exhausted");
            showToast(activity.getString(R.string.camera_error));
            return;
        }
        final String target = PhotonCamera.getSettings().mCameraID;
        final int token = openToken.get();
        Log.d(TAG, "scheduleCameraRecovery(): attempt " + attempt + " for " + target);
        mMainHandler.postDelayed(() -> {
            if (token != openToken.get() || !isCameraResumed) return;
            enqueueLensSwitch(target, true);
        }, DISCONNECT_RETRY_BASE_MS * attempt);
    }

    /** Called when the preview session configured for the current open. */
    public void onPreviewConfigured(int token) {
        mMainHandler.post(() -> {
            if (token != openToken.get()) return;
            disconnectRecoveries.set(0);
            LensSwitchScheduler.Request next = lensSwitchScheduler.onSettled();
            if (next != null) runLensSwitchCycle(next);
        });
    }

    /** Called when the preview session failed to configure. */
    public void onPreviewConfigureFailed(int token) {
        if (token != openToken.get()) return;
        LensSwitchScheduler.Request retry = lensSwitchScheduler.onFailure();
        if (retry == null) {
            showToast(activity.getString(R.string.session_on_configure_failed));
            return;
        }
        runOnMain(() -> runLensSwitchCycle(retry));
    }

    /**
     * Reports that the session for the current open could not be built at all
     * (missing surface, missing outputs or a session-creation error). Without
     * this the scheduler would stay active forever and silently swallow every
     * later lens switch. In-flight cycles are retried through the scheduler;
     * a failed cold open falls back to the disconnect recovery loop.
     */
    public void onPreviewSessionFailed(int token) {
        if (token != openToken.get()) return;
        if (lensSwitchScheduler.isActive()) {
            onPreviewConfigureFailed(token);
        } else {
            scheduleCameraRecovery();
        }
    }
    private Size getAspect(CameraMode targetMode){
        Size aspectRatio;
        if (targetMode == CameraMode.VIDEO || targetMode == CameraMode.RAWVIDEO || PhotonCamera.getSettings().aspect169) {
            aspectRatio = new Size(9, 16);
        } else {
            aspectRatio = new Size(3, 4);
        }
        return aspectRatio;
    }

    private Display getSafeDisplay() {
        if (mTextureView != null) {
            Display d = mTextureView.getDisplay();
            if (d != null) return d;
        }
        //noinspection deprecation
        return activity.getWindowManager().getDefaultDisplay();
    }

    //Size for preview drawing
    private Size getTextureOutputSize(
            Display display,
            CameraMode targetMode
    ) {
        Size aspectRatio = getAspect(targetMode);
        Point displayPoint = new Point();
        display.getRealSize(displayPoint);
        int shortSide = Math.min(displayPoint.x, displayPoint.y);
        int longSide = shortSide * aspectRatio.getHeight() / aspectRatio.getWidth();

        return new Size(longSide, shortSide);
    }

    //Size for preview buffer
    private Size getPreviewOutputSize(
            Display display,
            CameraCharacteristics characteristics,
            CameraMode targetMode
    ) {
        if (characteristics == null) {
            if (mCameraCharacteristicsMap == null || mCameraCharacteristicsMap.isEmpty()) {
                fillInCameraCharacteristics();
            }
            if (mCameraCharacteristicsMap != null && mCameraCharacteristicsMap.containsKey(physicalID)) {
                characteristics = mCameraCharacteristicsMap.get(physicalID);
                mCameraCharacteristics = characteristics;
            }
        }

        Size aspectRatio = getAspect(targetMode);
        Point displayPoint = new Point();
        display.getRealSize(displayPoint);
        int shortSide = Math.min(displayPoint.x, displayPoint.y);
        int longSide = shortSide / aspectRatio.getWidth() * aspectRatio.getHeight();

        if (characteristics == null) {
            return new Size(800, 600);
        }

        // If image format is provided, use it to determine supported sizes; else use target class
        StreamConfigurationMap config = characteristics.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

        if (config == null) {
            return new Size(800, 600);
        }

        Size[] allSizes = config.getOutputSizes(SurfaceTexture.class);
        if (allSizes == null || allSizes.length == 0) {
            return new Size(800, 600);
        }

        if (targetMode == CameraMode.VIDEO) {
            // VIDEO: frame what is recorded. Prefer the supported 16:9 preview
            // buffer closest in area to the chosen recording size, so the
            // finder FOV matches the recorded FOV.
            Size matched = matchVideoPreviewSize(allSizes, aspectRatio);
            if (matched != null) return matched;
        }

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
     * Picks the SurfaceTexture preview size closest in area to the currently
     * selected video recording size (exact 16:9 only). Bandwidth-capped at
     * {@code max(previewRes, recording area)} so a 4K recording may use a
     * larger finder while smaller recordings keep the historic 5MP ceiling.
     * Returns null when nothing suitable exists (caller falls back).
     */
    private Size matchVideoPreviewSize(Size[] allSizes, Size aspectRatio) {
        try {
            int idInt = parseVideoCameraId();
            CamcorderProfile profile = resolveVideoProfile(idInt, PreferenceKeys.getVideoResolution());
            android.util.Size videoSize = resolveVideoSize(PreferenceKeys.getVideoResolution(), profile, false);
            long targetArea = (long) videoSize.getWidth() * (long) videoSize.getHeight();
            if (targetArea <= 0) return null;
            long cap = Math.max((long) ResolutionSolution.previewRes, targetArea);
            Size best = null;
            long bestScore = Long.MAX_VALUE;
            for (Size size : allSizes) {
                if (size == null) continue;
                int sizeShort = Math.min(size.getHeight(), size.getWidth());
                int sizeLong = Math.max(size.getHeight(), size.getWidth());
                if (sizeLong % aspectRatio.getHeight() != 0
                        || sizeShort != aspectRatio.getWidth() * sizeLong / aspectRatio.getHeight()) {
                    continue;
                }
                long area = (long) sizeShort * (long) sizeLong;
                if (area > cap) continue;
                long score = Math.abs(area - targetArea);
                // Prefer at-or-below the recording area on ties (downscale, not upscale).
                if (area > targetArea) score += 1;
                if (score < bestScore) {
                    bestScore = score;
                    best = new Size(sizeShort, sizeLong);
                }
            }
            if (best != null) {
                Log.d(TAG, "video preview matched to recording "
                        + videoSize.getWidth() + "x" + videoSize.getHeight()
                        + " -> " + best.getWidth() + "x" + best.getHeight());
            }
            return best;
        } catch (Exception e) {
            Log.w(TAG, "matchVideoPreviewSize failed", e);
            return null;
        }
    }

    /**
     * Lock the focus as the first step for a still image capture.
     */
    private void lockFocus() {
        if(burst) return;
        if (mPreviewRequestBuilder == null || mCaptureSession == null) {
            Log.w(TAG, "lockFocus(): camera not ready (builder=" + mPreviewRequestBuilder + " session=" + mCaptureSession + ")");
            return;
        }
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
        if (mPreviewRequestBuilder == null || mCaptureSession == null) {
            return;
        }
        try {
            // This is how to tell the camera to trigger.
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            // Tell #mCaptureCallback to wait for the precapture sequence to be set.
            mState = STATE_WAITING_PRECAPTURE;
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException | IllegalStateException e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
    }

    private String physicalID = "";
    private String logicalID = "";

    /**
     * Opens the camera specified by {@link Settings#mCameraID}.
     */
    public void openCamera(int width, int height) {
        // Both the SurfaceTexture listener and resumeCamera() can request an
        // open for the same surface lifecycle event; a second open while one is
        // in flight fails with CAMERA_IN_USE and kills the preview.
        if (!mCameraOpening.compareAndSet(false, true)) {
            Log.d(TAG, "openCamera(): an open is already in flight, skipping");
            return;
        }
        // A cold open owns the device slot; drop any queued lens switch that
        // would otherwise race with it. A pinch made during the open is queued
        // again and picked up by handleCameraOpened().
        lensSwitchScheduler.cancel();
        final int token = openToken.incrementAndGet();
        //Open camera in non ui thread
        processExecutor.execute(()->{
            CameraFragment.mSelectedMode = PhotonCamera.getSettings().selectedMode;
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                //requestCameraPermission();
                mCameraOpening.set(false);
                return;
            }
            if (!isCameraResumed) {
                // The app was backgrounded before this task ran.
                mCameraOpening.set(false);
                Log.d(TAG, "openCamera(): app already backgrounded, skipping");
                return;
            }
            processExecutor.execute(()-> {
                mMediaRecorder = new MediaRecorder();
            });
            cameraEventsListener.onOpenCamera(this.mCameraManager);
            setUpCameraOutputs(width, height);
            configureTransform(width, height);
            if (!isCameraResumed) {
                // The app was backgrounded while outputs were being set up.
                mCameraOpening.set(false);
                Log.d(TAG, "openCamera(): app backgrounded during setup, skipping");
                return;
            }
            boolean acquired = false;
            boolean issued = false;
            try {
                if (!mCameraOpenCloseLock.tryAcquire(1000, TimeUnit.MILLISECONDS)) {
                    mCameraOpening.set(false);
                    Log.e(TAG, "openCamera(): lock timeout");
                    scheduleCameraRecovery();
                    return;
                }
                acquired = true;
                // Split x-y, x - logical, y - physical
                parseCameraIds(PhotonCamera.getSettings().mCameraID);
                this.mCameraManager.openCamera(logicalID, makeStateCallback(token), mBackgroundHandler);
                issued = true;
            } catch (CameraAccessException e) {
                mCameraOpening.set(false);
                Log.e(TAG, Log.getStackTraceString(e));
                scheduleCameraRecovery();
            } catch (InterruptedException e) {
                mCameraOpening.set(false);
                Log.e(TAG, Log.getStackTraceString(e));
                Thread.currentThread().interrupt();
                scheduleCameraRecovery();
            } catch (Exception e) {
                mCameraOpening.set(false);
                Log.e(TAG, Log.getStackTraceString(e));
                scheduleCameraRecovery();
            } finally {
                // On success the open callback owns the permit; a failed
                // issuance must return it here or it leaks and blocks the next
                // restart/close on the UI thread.
                if (acquired && !issued) {
                    mCameraOpenCloseLock.release();
                }
            }
    });
    }
    public void UpdateCameraCharacteristics(String cameraId) {
        if (isVideoLogicalActive() && mLogicalMemberPhysical != null
                && !mLogicalMemberPhysical.isEmpty()) {
            // Sensor specifics follow the active member stream; the logical
            // id itself has no sensor-specific tuning block.
            try {
                PhotonCamera.getSpecificSensor().selectSpecifics(Integer.parseInt(mLogicalMemberPhysical));
            } catch (NumberFormatException ignored) {
            }
        } else {
            PhotonCamera.getSpecificSensor().selectSpecifics(Integer.parseInt(cameraId));
        }
        CameraCharacteristics characteristics = this.mCameraCharacteristicsMap.get(cameraId);
        mCameraCharacteristics = characteristics;
        if (paramController != null) {
            paramController.onCameraChanged();
        }
        // Re-anchor the active lens choice; the lens/facing model itself is fed
        // from the lens-data map by CameraFragment on reopen. Skipped in video
        // logical mode, where the fragment owns member anchoring.
        if (!isVideoLogicalActive()) {
            zoomController.setActiveLens(PhotonCamera.getSettings().mCameraID);
        }
        //Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);

        StreamConfigurationMap map = null;
        if (mCameraCharacteristics != null) {
            map = mCameraCharacteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        }
        if (map == null) {
            return;
        }
        ArrayList<Size> allTargets = getAllTargets();

        Size preview = getCameraOutputSize(map.getOutputSizes(mPreviewTargetFormat));

        int maxjpg = 3;
        if (mTargetFormat == mPreviewTargetFormat && isDualSession)
            maxjpg = PhotonCamera.getSettings().frameCount + 3;
        if (isZslMode())
            maxjpg = Math.min(PhotonCamera.getSettings().frameCount + 3, 40);
        Size target = getCameraOutputSize(allTargets.toArray(new Size[0]), preview);
        Size aspect = getAspect(PhotonCamera.getSettings().selectedMode);
        if(preview.getWidth() > preview.getHeight())
            preview = new Size(preview.getWidth(),preview.getWidth()*aspect.getWidth()/aspect.getHeight());
        else {
            preview = new Size(preview.getHeight()*aspect.getWidth()/aspect.getHeight(),preview.getHeight());
        }
        if(mImageReaderPreview != null)
            mImageReaderPreview.close();

        mImageReaderPreview = ImageReader.newInstance(preview.getWidth(), preview.getHeight(), mPreviewTargetFormat, maxjpg);
        mImageReaderPreview.setOnImageAvailableListener(mOnYuvImageAvailableListener, mBackgroundHandler);
            mBufferSize = getPreviewOutputSize(getSafeDisplay(),characteristics,PhotonCamera.getSettings().selectedMode);

        if(mImageReaderRaw != null)
            mImageReaderRaw.close();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && (PhotonCamera.getSettings().QuadBayer
                || !(Build.BRAND.equalsIgnoreCase("oppo")
                || Build.BRAND.equalsIgnoreCase("vivo")
                || Build.BRAND.equalsIgnoreCase("oneplus")
                || Build.BRAND.equalsIgnoreCase("realme")
                || Build.BRAND.equalsIgnoreCase("iqoo")
                || Build.BRAND.equalsIgnoreCase("nothing")
                || Build.BRAND.equalsIgnoreCase("google")
        ))
        ) {
            mImageReaderRaw = ImageReader.newInstance(target.getWidth(), target.getHeight(), mTargetFormat, maxjpg, 0x00100000);
        } else {
            mImageReaderRaw = ImageReader.newInstance(target.getWidth(), target.getHeight(), mTargetFormat, maxjpg);
        }
        mImageReaderRaw.setOnImageAvailableListener(mOnRawImageAvailableListener, mBackgroundHandler);
        // Find out if we need to swap dimension to get the preview size relative to sensor
        // coordinate.
        int displayRotation = PhotonCamera.getGravity().getRotation();
        mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        Range<Integer>[] ranges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
        if (ranges == null) {
            ranges = new Range[1];
            ranges[0] = new Range<>(14, 30);
        }
        int minLower = Integer.MAX_VALUE;
        for (Range<Integer> range : ranges) {
            if (range.getLower() < minLower) {
                minLower = range.getLower();
            }
        }
        if (minLower == Integer.MAX_VALUE) minLower = 14;
        // Clamp the auto upper bound to what the HAL actually offers.
        int maxUpper = 30;
        try {
            int supportedMax = Integer.MIN_VALUE;
            for (Range<Integer> range : ranges) {
                if (range != null && range.getUpper() > supportedMax) supportedMax = range.getUpper();
            }
            if (supportedMax != Integer.MIN_VALUE) maxUpper = Math.min(30, supportedMax);
        } catch (Exception ignored) {
        }
        FpsRangeAuto = new Range<>(minLower, maxUpper);
        try {
            mAvailableFpsRanges = ranges.clone();
        } catch (Exception ignored) {
            mAvailableFpsRanges = ranges;
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
        activity.runOnUiThread(() -> {
            //Preview drawing size changing
            mPreviewSize = getTextureOutputSize(getSafeDisplay(), PhotonCamera.getSettings().selectedMode);
            applyPreviewAspect();
            updatePreviewMirror();
            cameraEventsListener.onCharacteristicsUpdated(characteristics);
            if (PhotonCamera.getSettings().DebugData)
                showToast("preview:" + new Point(mPreviewWidth, mPreviewHeight));
        });
        //activity.runOnUiThread(() -> cameraEventsListener.onCharacteristicsUpdated(characteristics));
    }

    /**
     * Sizes the viewfinder frame to the preview aspect. The frame anchors the HUD
     * and defines the sharp rect the renderer letterboxes into; the preview
     * surface itself (ViewfinderEdgeBlurController) fills the layout or the frame.
     */
    private void applyPreviewAspect() {
        if (mPreviewSize == null) {
            return;
        }
        View frame = activity.findViewById(R.id.viewfinder_frame);
        if (frame instanceof ViewfinderFrameView) {
            ((ViewfinderFrameView) frame).setAspectRatio(
                    mPreviewSize.getHeight(), mPreviewSize.getWidth());
        }
    }

    Surface surface;
    public void createCameraPreviewSession(boolean isBurstSession) {
        final int sessionToken = openToken.get();
        cancelLogicalZoom();
        try {
            SensorConfigInjector.applyToSensor(getTunablePhysicalId(), this);
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            if (texture == null) {
                Log.w(TAG, "createCameraPreviewSession(): SurfaceTexture not ready, waiting for surface");
                mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
                // Report the missing session so a lens-switch cycle cannot stay
                // active forever with no preview.
                onPreviewSessionFailed(sessionToken);
                return;
            }
            // We configure the size of default buffer to be the size of camera preview we want.
            Log.d(TAG, "createCameraPreviewSession() mTextureView:" + mTextureView);
            Log.d(TAG, "createCameraPreviewSession() Texture:" + texture);
            Log.d(TAG, "bufferSize:" + mBufferSize);
            Log.d(TAG, "previewSize:" + mPreviewSize);
            Log.d(TAG, "ID:" + PhotonCamera.getSettings().mCameraID + " deviceID:" + mCameraDevice.getId() + " logicalID:" + logicalID + " physicalID:" + physicalID);

            //Camera output
            texture.setDefaultBufferSize(mBufferSize.getHeight(), mBufferSize.getWidth());

            // This is the output Surface we need to start preview.
            if(surface == null)
                surface = new Surface(texture);
            // We set up a CaptureRequest.Builder with the output Surface.
            setCaptureRequestBuilder();

            // Here, we create a CameraCaptureSession for camera preview.
            List<Surface> surfaces = configureSurfaces(isBurstSession);
            Log.d(TAG, "createCameraPreviewSession() surfaces:" + Arrays.toString(surfaces.toArray()));
            ArrayList<OutputConfiguration> outputConfigurations = new ArrayList<>();
            long videoDynamicRange = android.hardware.camera2.params.DynamicRangeProfiles.STANDARD;
            boolean wantVideoHdrSession = mIsRecordingVideo && mVideoHdrActive
                    && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU;
            if (wantVideoHdrSession) {
                // Resolved in configureSurfaces() before the encoder was
                // prepared, so both already agree; just consume it here.
                videoDynamicRange = mPendingVideoDynamicRange;
                if (videoDynamicRange
                        == android.hardware.camera2.params.DynamicRangeProfiles.STANDARD) {
                    wantVideoHdrSession = false;
                    mVideoHdrActive = false;
                }
            }
            boolean hdrApplyFailed = false;
            String streamPhysicalId = getSessionStreamPhysicalId();
            for (Surface surfacei : surfaces) {
                var config = new OutputConfiguration(surfacei);
                if(!Objects.equals(streamPhysicalId, logicalID) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P){
                    config.setPhysicalCameraId(streamPhysicalId);
                }
                if (wantVideoHdrSession
                        && videoDynamicRange
                                != android.hardware.camera2.params.DynamicRangeProfiles.STANDARD) {
                    try {
                        if (mVideoRecorderSurface != null && surfacei == mVideoRecorderSurface) {
                            config.setDynamicRangeProfile(videoDynamicRange);
                            Log.d(TAG, "video HDR dynamic range profile=" + videoDynamicRange
                                    + " transfer=" + PreferenceKeys.getVideoHdrTransfer());
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "HDR dynamic range profile not applied", e);
                        hdrApplyFailed = true;
                    }
                }
                outputConfigurations.add(config);
            }
            if (hdrApplyFailed && mVideoHdrActive && mIsRecordingVideo) {
                // The HAL rejected the 10-bit output: re-prepare a consistent
                // SDR encoder instead of writing Main10 with SDR transfer.
                Log.w(TAG, "HDR output rejected, re-preparing SDR encoder");
                showToast("HDR output unsupported, recording SDR");
                try {
                    mVideoHdrActive = false;
                    mPendingVideoDynamicRange =
                            android.hardware.camera2.params.DynamicRangeProfiles.STANDARD;
                    setUpMediaRecorder(false);
                    mVideoRecorderSurface = mMediaRecorder.getSurface();
                    surfaces = Arrays.asList(surface, mVideoRecorderSurface);
                    if (!mRecorderTargetAdded) {
                        mPreviewRequestBuilder.addTarget(mVideoRecorderSurface);
                        mRecorderTargetAdded = true;
                    }
                    outputConfigurations.clear();
                    String retryStreamPhysicalId = getSessionStreamPhysicalId();
                    for (Surface surfacei : surfaces) {
                        var config = new OutputConfiguration(surfacei);
                        if (!Objects.equals(retryStreamPhysicalId, logicalID)
                                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            config.setPhysicalCameraId(retryStreamPhysicalId);
                        }
                        outputConfigurations.add(config);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "SDR fallback re-prepare failed: " + Log.getStackTraceString(e));
                }
            }

            CameraCaptureSession.StateCallback stateCallback =
                    new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Log.d(TAG, "CameraCaptureSession onConfigured():" + cameraCaptureSession);
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
                        applyAeMeteringRegions(mPreviewRequestBuilder);
                        Camera2ApiAutoFix.applyPrev(mPreviewRequestBuilder);
                        VendorTagUtils.builderSessionApply(mPreviewRequestBuilder, false, useMaximumResolutionKey, getTunablePhysicalId());
                        // Video-only tunable keys: global list, applied in VIDEO mode only
                        // (preview + recording share this repeating builder).
                        // HDR recordings get the HDR list, everything else the
                        // SDR list; mVideoHdrActive is fresh here (set during
                        // configureSurfaces for recordings, stale-false for
                        // idle preview) so fallbacks stay consistent.
                        try {
                            if (PhotonCamera.getSettings() != null
                                    && PhotonCamera.getSettings().selectedMode == CameraMode.VIDEO) {
                                com.particlesdevs.photoncamera.settings.TunableKeyManager
                                        .applyVideoTunableKeys(mPreviewRequestBuilder, getTunablePhysicalId(),
                                                mVideoHdrActive && mIsRecordingVideo);
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "video tunable keys failed", e);
                        }
                        applyIszIfActive(mPreviewRequestBuilder, physicalID);
                        //if(isZslMode()){
                            try {
                                mPreviewRequestBuilder.set(CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE, CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE_ON);
                            } catch (Exception e) {
                                Log.d(TAG, "Failed to set LENS_SHADING_MAP_MODE_ON for ZSL mode:" + Log.getStackTraceString(e));
                            }
                        //}

                        // Apply dynamic OIS for preview stream
                        applyOisMode(mPreviewRequestBuilder, false);

                        // Finally, we start displaying the camera preview.
                        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                                getSelectedFpsRange());
                        mPreviewInputRequest = mPreviewRequestBuilder.build();
                        if (isBurstSession && isDualSession) {
                            switch (CameraFragment.mSelectedMode) {
                                case NIGHT:
                                case PHOTO:
                                case MOTION:
                                    mCaptureSession.captureBurst(captures, CaptureCallback, mBackgroundHandler);
                                    break;
                                case VIDEO:
                                    // VIDEO never bursts: fall through to repeating preview.
                                    mCaptureSession.setRepeatingRequest(mPreviewInputRequest,
                                            mCaptureCallback, mBackgroundHandler);
                                    break;
                                case UNLIMITED:
                                case RAWVIDEO:
                                    mCaptureSession.setRepeatingBurst(captures, CaptureCallback, mBackgroundHandler);
                                    break;
                            }
                        } else {
                            //if(mSelectedMode != CameraMode.VIDEO)
                            mCaptureSession.setRepeatingRequest(mPreviewInputRequest,
                                    mCaptureCallback, mBackgroundHandler);
                            if (!isVideoMode()) {
                                // Photo-mode AF trigger dance; in VIDEO it would
                                // clobber CONTINUOUS_VIDEO back to the photo pref.
                                unlockFocus();
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, Log.getStackTraceString(e));
                    }
                    // A configured session completes the current switch cycle;
                    // a newer pending target (if any) starts from here.
                    onPreviewConfigured(sessionToken);
                    if (mIsRecordingVideo)
                        activity.runOnUiThread(() -> {
                            // Start recording
                            try {
                                mMediaRecorder.start();
                                startVideoRecTicker();
                            } catch (Exception e) {
                                Log.e(TAG, "video record start failed: " + Log.getStackTraceString(e));
                                stopRecordingVideo();
                            }
                        });
                }

                @Override
                public void onConfigureFailed(
                        @NonNull CameraCaptureSession cameraCaptureSession) {
                    Log.d(TAG, "CameraCaptureSession onConfigureFailed()");
                    onPreviewConfigureFailed(sessionToken);
                }
            };
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                SessionConfiguration configuration = new SessionConfiguration(
                        sessionType,
                        outputConfigurations,
                        processExecutor,
                        stateCallback
                );
                mCameraDevice.createCaptureSession(configuration);
            } else {
                mCameraDevice.createCaptureSession(surfaces, stateCallback, mBackgroundHandler);
            }
        } catch (Exception e) {
            Log.e(TAG, Log.getStackTraceString(e));
            onPreviewSessionFailed(sessionToken);
        }
    }

    @NotNull
    private List<Surface> configureSurfaces(boolean isBurstSession) {
        if (mImageReaderPreview == null || mImageReaderRaw == null) {
            throw new IllegalStateException("Preview/RAW ImageReader not ready (preview="
                    + mImageReaderPreview + ", raw=" + mImageReaderRaw + ")");
        }
        List<Surface> surfaces = Arrays.asList(surface, mImageReaderPreview.getSurface());
        if (isDualSession) {
            if (isBurstSession) {
                surfaces = Arrays.asList(mImageReaderPreview.getSurface(), mImageReaderRaw.getSurface());
            }
            if (mTargetFormat == mPreviewTargetFormat) {
                surfaces = Arrays.asList(surface, mImageReaderPreview.getSurface());
            }
        } else {
           if(Build.BRAND.equalsIgnoreCase("samsung")){
                surfaces = Arrays.asList(surface, mImageReaderRaw.getSurface());
            } else {
                surfaces = Arrays.asList(surface, mImageReaderPreview.getSurface(), mImageReaderRaw.getSurface());
            }
           if(PhotonCamera.getSettings().previewFormat == 0) {
                surfaces = Arrays.asList(surface, mImageReaderRaw.getSurface());
           }
        }
        if (mIsRecordingVideo) {
            // Resolve the 10-bit session profile BEFORE preparing the encoder
            // so both degrade to SDR together when 10-bit output is
            // unavailable (otherwise the file ends up Main10 with SDR
            // transfer: BT.2020 primaries + BT.709 transfer).
            mPendingVideoDynamicRange = 0L;
            boolean allowHdr = true;
            if (isVideoHdrRequested()
                    && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                try {
                    mPendingVideoDynamicRange = resolveVideoDynamicRangeProfile();
                } catch (Exception e) {
                    Log.w(TAG, "HDR dynamic range resolve failed, using SDR session", e);
                    mPendingVideoDynamicRange = 0L;
                }
                if (mPendingVideoDynamicRange
                        == android.hardware.camera2.params.DynamicRangeProfiles.STANDARD) {
                    allowHdr = false;
                    Log.w(TAG, "no 10-bit dynamic range profile, recording SDR");
                    showToast("HDR not available on this camera, recording SDR");
                }
            }
            setUpMediaRecorder(allowHdr);
            mVideoRecorderSurface = mMediaRecorder.getSurface();
            surfaces = Arrays.asList(surface, mVideoRecorderSurface);
            if (!mRecorderTargetAdded) {
                mPreviewRequestBuilder.addTarget(mVideoRecorderSurface);
                mRecorderTargetAdded = true;
            }
        } else if (isVideoMode()) {
            // Idle VIDEO preview: record-ready session with the preview surface
            // only. Still ImageReaders stay out so no photo bandwidth is spent
            // and starting a recording needs no session rebuild.
            surfaces = Arrays.asList(surface);
        }
        return surfaces;
    }

    /** Recorder Surface captured at setup, for reliable OutputConfiguration matching. */
    private Surface mVideoRecorderSurface;
    /**
     * 10-bit session profile resolved before the encoder is prepared
     * ({@code 0} = STANDARD/SDR). Avoids referencing DynamicRangeProfiles in
     * an initializer so the class stays loadable below API 33.
     */
    private long mPendingVideoDynamicRange = 0L;
    /** Guards the recorder target against double-add on SDR-fallback retry. */
    private boolean mRecorderTargetAdded = false;

    /** True when the camera is in VIDEO mode (idling or recording). */
    public boolean isVideoMode() {
        try {
            return PhotonCamera.getSettings() != null
                    && PhotonCamera.getSettings().selectedMode == CameraMode.VIDEO;
        } catch (Exception e) {
            return false;
        }
    }

    /** Physical id of the active logical member (null when synthetic/none). */
    private String mLogicalMemberPhysical;
    /** Pill id of the active logical member (e.g. "5#2"), or null. */
    private String mActiveLogicalMemberId;
    /** In-flight smooth-zoom animator for logical pill taps (null when idle). */
    private ValueAnimator mLogicalZoomAnimator;
    /** Last ratio actually submitted in logical mode (animation seed). */
    private float mLogicalRenderRatio;
    /** Smooth logical pill-tap zoom duration in ms. */
    private static final long LOGICAL_ZOOM_ANIM_MS = 400L;

    /** Pill id of the active logical member, or null when not in logical mode. */
    public String getActiveLogicalMemberId() {
        return mActiveLogicalMemberId;
    }

    /** True when video logical-id mode is effectively active. */
    public boolean isVideoLogicalActive() {
        try {
            return LogicalCameraResolver.isVideoLogicalActive(activity);
        } catch (Exception e) {
            return false;
        }
    }

    /** Member list driving the pill/zoom in video logical mode (empty when inactive). */
    @NonNull
    public List<LogicalCameraResolver.Member> getEffectiveLogicalMembers() {
        try {
            if (!isVideoLogicalActive()) return Collections.emptyList();
            return LogicalCameraResolver.resolveEffectiveMembers(
                    activity, PreferenceKeys.getVideoLogicalId());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * Physical id whose stream the session outputs should come from. In video
     * logical mode on API 30+ the logical HAL switches internally (untagged);
     * below API 30 (no zoom-ratio API) member streams are targeted explicitly.
     */
    private String getSessionStreamPhysicalId() {
        try {
            if (isVideoLogicalActive()
                    && Build.VERSION.SDK_INT < Build.VERSION_CODES.R
                    && mLogicalMemberPhysical != null && !mLogicalMemberPhysical.isEmpty()) {
                return mLogicalMemberPhysical;
            }
        } catch (Exception ignored) {
        }
        return physicalID;
    }

    /**
     * Feeds logical member entries to the zoom controller and anchors the
     * member nearest the current target (preselect). Returns the anchor
     * member id for the pill highlight.
     */
    @NonNull
    public String configureLogicalZoomLenses(
            @NonNull List<LogicalCameraResolver.Member> members) {
        String anchor = "";
        try {
            cancelLogicalZoom();
            mLogicalRenderRatio = 0f;
            List<ZoomController.LensEntry> entries = new ArrayList<>();
            for (int i = 0; i < members.size(); i++) {
                LogicalCameraResolver.Member m = members.get(i);
                if (m == null) continue;
                float maxD = (i + 1 < members.size())
                        ? members.get(i + 1).zoomFactor / m.zoomFactor
                        : Math.max(1f, m.maxDigitalZoom);
                entries.add(new ZoomController.LensEntry(m.memberId, m.zoomFactor, maxD));
                if (anchor.isEmpty()) anchor = m.memberId;
            }
            zoomController.setLenses(entries);
            float target = zoomController.getZoomRatio();
            float best = Float.MAX_VALUE;
            for (LogicalCameraResolver.Member m : members) {
                if (m == null) continue;
                float d = Math.abs(m.zoomFactor - target);
                if (d < best) {
                    best = d;
                    anchor = m.memberId;
                }
            }
            zoomController.setActiveLens(anchor);
            mActiveLogicalMemberId = anchor;
            LogicalCameraResolver.Member anchored =
                    LogicalCameraResolver.findMember(members, anchor);
            mLogicalMemberPhysical =
                    (anchored != null && !anchored.synthetic) ? anchored.physicalId : null;
            Log.d(TAG, "logical zoom lenses=" + entries.size() + " anchor=" + anchor);
        } catch (Exception e) {
            Log.w(TAG, "configureLogicalZoomLenses failed", e);
        }
        return anchor;
    }

    /**
     * Pill tap on a logical member: smooth-zooms to the member native ratio
     * instead of jumping, without reopening the camera. Pre-R snaps (no
     * zoom-ratio API to animate through).
     */
    public void zoomToLogicalMember(@NonNull String memberId) {
        try {
            if (!isVideoLogicalActive()) return;
            List<LogicalCameraResolver.Member> members = getEffectiveLogicalMembers();
            LogicalCameraResolver.Member target =
                    LogicalCameraResolver.findMember(members, memberId);
            if (target == null) return;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                // Video mode never uses detent snap + hysteresis (see setZoom).
                String switchTo = zoomController.setTargetZoom(target.zoomFactor, 0.5f, 0.5f, false);
                if (switchTo != null) {
                    requestLensSwitch(switchTo);
                } else if (mPreviewRequestBuilder != null) {
                    applyZoom(mPreviewRequestBuilder);
                    rebuildPreviewBuilder();
                }
                return;
            }
            startLogicalZoom(memberId, target.zoomFactor);
        } catch (Exception e) {
            Log.w(TAG, "zoomToLogicalMember failed", e);
        }
    }

    /**
     * Animates CONTROL_ZOOM_RATIO from the last rendered value to the target
     * through the seamless-apply path (no session churn), then commits the
     * zoom-controller state and member bookkeeping. Interruptible: any new
     * tap, gesture, mode/lens change or lifecycle event cancels first.
     */
    private void startLogicalZoom(@NonNull final String memberId, final float to) {
        try {
            cancelLogicalZoom();
            float from = mLogicalRenderRatio > 0f ? mLogicalRenderRatio : zoomController.getZoomRatio();
            if (Math.abs(to - from) < 1e-4f) {
                String switchTo = zoomController.setTargetZoom(to, 0.5f, 0.5f, false);
                if (switchTo != null) {
                    requestLensSwitch(switchTo);
                } else if (mPreviewRequestBuilder != null) {
                    applyZoom(mPreviewRequestBuilder);
                    rebuildPreviewBuilder();
                }
                return;
            }
            mLogicalRenderRatio = from;
            mLogicalZoomAnimator = ValueAnimator.ofFloat(from, to);
            mLogicalZoomAnimator.setDuration(LOGICAL_ZOOM_ANIM_MS);
            try {
                mLogicalZoomAnimator.setInterpolator(Motion.emphasized(activity));
            } catch (Exception ignored) {
            }
            mLogicalZoomAnimator.addUpdateListener(animation -> {
                try {
                    float value = (float) animation.getAnimatedValue();
                    if (mPreviewRequestBuilder != null) {
                        applyZoomLogical(mPreviewRequestBuilder, value);
                        rebuildPreviewBuilder();
                    }
                    try {
                        cameraEventsListener.onLogicalZoomProgress(value);
                    } catch (Exception ignored) {
                    }
                } catch (Exception e) {
                    Log.w(TAG, "logical zoom tick failed", e);
                }
            });
            mLogicalZoomAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mLogicalZoomAnimator = null;
                    try {
                        String switchTo = zoomController.setTargetZoom(to, 0.5f, 0.5f, false);
                        if (switchTo != null) {
                            requestLensSwitch(switchTo);
                        } else if (mPreviewRequestBuilder != null) {
                            applyZoom(mPreviewRequestBuilder);
                            rebuildPreviewBuilder();
                        }
                        try {
                            cameraEventsListener.onLogicalZoomProgress(to);
                        } catch (Exception ignored) {
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "logical zoom commit failed", e);
                    }
                }
            });
            mLogicalZoomAnimator.start();
        } catch (Exception e) {
            Log.w(TAG, "startLogicalZoom failed", e);
        }
    }

    /** Cancels any in-flight smooth logical zoom (safe from any thread). */
    private void cancelLogicalZoom() {
        try {
            if (Looper.myLooper() != Looper.getMainLooper()) {
                mMainHandler.post(this::cancelLogicalZoom);
                return;
            }
            if (mLogicalZoomAnimator != null) {
                mLogicalZoomAnimator.cancel();
                mLogicalZoomAnimator = null;
            }
        } catch (Exception e) {
            Log.w(TAG, "cancelLogicalZoom failed", e);
        }
    }

    /** Seamless member switch on the open logical device (no reopen). */
    private void applyLogicalMemberSwitch(@NonNull String memberId) {
        try {
            List<LogicalCameraResolver.Member> members = getEffectiveLogicalMembers();
            LogicalCameraResolver.Member target =
                    LogicalCameraResolver.findMember(members, memberId);
            if (target == null) return;
            mActiveLogicalMemberId = memberId;
            mLogicalMemberPhysical = target.synthetic ? null : target.physicalId;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (mPreviewRequestBuilder != null) {
                    applyZoom(mPreviewRequestBuilder);
                    rebuildPreviewBuilder();
                }
            } else {
                // Pre-R has no zoom-ratio API: rebuild the session on the
                // member's physical stream instead (brief freeze, no reopen).
                createCameraPreviewSession(false);
            }
            try {
                cameraEventsListener.onLogicalMemberChanged(memberId);
            } catch (Exception ignored) {
            }
            Log.d(TAG, "logical member switch -> " + memberId
                    + " ratio=" + zoomController.getZoomRatio());
        } catch (Exception e) {
            Log.w(TAG, "applyLogicalMemberSwitch failed", e);
        }
    }

    private void setCaptureRequestBuilder() throws CameraAccessException {
        mPreviewRequestBuilder = null;
        mVideoRecorderSurface = null;
        mRecorderTargetAdded = false;
        // VIDEO mode idles record-ready: TEMPLATE_RECORD both while previewing
        // and while recording, so entering video never needs a session rebuild.
        boolean recordTemplate = mIsRecordingVideo || isVideoMode();
        if (recordTemplate) {
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
        } else {
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        }

        mPreviewRequestBuilder.addTarget(surface);
        synchronized (mZslBufferLock) {
            while (!mZslRingBuffer.isEmpty()) {
                Image img = mZslRingBuffer.pollFirst();
                if (img != null) img.close();
            }
        }
        // Drain any frames still queued in the RAW ImageReader to prevent them leaking
        // into the next non-ZSL capture's IMAGE_BUFFER
        if (mImageReaderRaw != null) {
            Image stale;
            try {
                while ((stale = mImageReaderRaw.acquireNextImage()) != null) stale.close();
            } catch (Exception ignored) {}
        }
        if (isZslMode()) {
            mPreviewRequestBuilder.addTarget(mImageReaderRaw.getSurface());
        }
        mInitialMeteringAF = mPreviewRequestBuilder.get(CONTROL_AF_REGIONS);
        mPreviewMeteringAF = mInitialMeteringAF;
        mPreviewAFMode = PreferenceKeys.getAfMode();
        if (recordTemplate) {
            mPreviewRequestBuilder.set(CONTROL_AF_MODE, CONTROL_AF_MODE_CONTINUOUS_VIDEO);
            mPreviewAFMode = CONTROL_AF_MODE_CONTINUOUS_VIDEO;
            // Explicit ON/OFF: leaving the key unset would inherit whatever the
            // previous session left behind.
            mPreviewRequestBuilder.set(CONTROL_VIDEO_STABILIZATION_MODE,
                    PreferenceKeys.isEisPhotoOn()
                            ? CONTROL_VIDEO_STABILIZATION_MODE_ON
                            : CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF);
        }
        mInitialMeteringAE = mPreviewRequestBuilder.get(CONTROL_AE_REGIONS);
        mPreviewMeteringAE = mInitialMeteringAE;
        mPreviewAEMode = mPreviewRequestBuilder.get(CONTROL_AE_MODE);
        applyZoom(mPreviewRequestBuilder);
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
        if (mPreviewRequestBuilder == null || mCaptureSession == null) {
            Log.w(TAG, "takePicture(): camera not ready, ignoring shutter press");
            return;
        }
        if (isZslMode()) {
            captureStillPicture();
            return;
        }
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
    public void unlockFocus() {
        if (mPreviewRequestBuilder == null || mCaptureSession == null) {
            Log.d(TAG, "unlockFocus(): camera not ready (builder=" + mPreviewRequestBuilder + ", session=" + mCaptureSession + ")");
            return;
        }
        try {
            // Reset the auto-focus trigger
            //mCaptureSession.stopRepeating();
            //mCaptureSession.abortCaptures();
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            rebuildPreviewBuilderOneShot();
            //mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
            //        CameraMetadata.CONTROL_AF_TRIGGER_START);
            //rebuildPreviewBuilderOneShot();
            reset3Aparams();
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_START);
            rebuildPreviewBuilderOneShot();
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            rebuildPreviewBuilderOneShot();
            paramController.setupPreview();
            /*mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_START);
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);*/
            // After this, the camera will go back to the normal state of preview.
            mState = STATE_PREVIEW;
            rebuildPreviewBuilder();
            //mCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback,
            //        mBackgroundHandler);
        }catch(Exception e){
            Log.d(TAG, "unlockFocus:"+e);
        }
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
            Log.e(TAG, Log.getStackTraceString(e));
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
            applyAeMeteringRegions(builder);
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
                    //mCaptureResult = partialResult;
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
                    mBackgroundHandler.postDelayed(() -> {
                        while(mImageSaver.implementation.IMAGE_BUFFER.size() > PhotonCamera.getSettings().frameCount/2) {
                            try {
                                Thread.sleep(1);
                            } catch (InterruptedException ignored) {}
                        }
                        cameraEventsListener.onCaptureSequenceCompleted(null);
                    }, 100);
                    mMeasuredFrameCnt = finalFrameCount;
                    burst = false;
                    //Surface texture related
                    activity.runOnUiThread(() -> UpdateCameraCharacteristics(physicalID));
                    if (!isDualSession)
                        unlockFocus();
                    else
                        createCameraPreviewSession(false);
                    taskResults.removeIf(Future::isDone); //remove already completed results
                    Future<?> result = processExecutor.submit(() -> mImageSaver.runRaw(mCameraCharacteristics, mCaptureResult, mCaptureRequest, new ArrayList<>(BurstShakiness), cameraRotation, mExposures));
                    taskResults.add(result);
                }
            };
            burst = true;
            Camera2ApiAutoFix.ApplyBurst();
            if (isDualSession)
                createCameraPreviewSession(true);
            else {
                mCaptureSession.captureBurst(captures, CaptureCallback, mBackgroundHandler);
            }

        } catch (CameraAccessException e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
    }

    public void runDebug(CaptureRequest.Builder builder){
        activity.runOnUiThread(() -> debugCapture(builder));
    }

    public boolean isZslMode() {
        return PhotonCamera.getSettings().selectedMode == CameraMode.MOTION
                && !IsoExpoSelector.HDR
                && !isDualSession;
    }

    /**
     * Round 3 (ZSL): pack a shutter-copied burst frame immediately, so the
     * copy loop's native peak drops from 8x24 MB to ~8x15.7 MB and ApplyHdrX
     * finds nothing left to pack. Fail-safe: HdrxProcessor's loop still
     * handles leftovers, and a failed pack keeps the 16-bit buffer. Only
     * called when the burst holds >1 frame (single frames keep the 16-bit
     * contract; the HdrxProcessor guard covers freak null-collapses).
     */
    private void packZslBurstFrame(ImageFrame frame) {
        int whiteLevel = 0;
        try {
            if (mCameraCharacteristics != null) {
                Integer wl = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL);
                if (wl != null) whiteLevel = wl;
            }
        } catch (Exception ignored) {
        }
        ImageFrame.packBurstAtArrival(frame, whiteLevel, PhotonCamera.DEBUG);
    }

    private void triggerZslCapture() {
        if (mZslCapturing || CaptureController.isProcessing) {
            Log.w(TAG, "ZSL: capture already in progress, ignoring");
            return;
        }
        mZslCapturing = true;
        burst = false;

        int frameCount = FrameNumberSelector.getFrames();
        cameraRotation = PhotonCamera.getGravity().getCameraRotation(mSensorOrientation);
        BurstShakiness = new ArrayList<>();
        mExposures = new HashMap<>();

        // Drain raw Image objects from the ring buffer (no copy yet)
        List<Image> rawImages;
        synchronized (mZslBufferLock) {
            rawImages = new ArrayList<>(mZslRingBuffer);
            mZslRingBuffer.clear();
        }

        int take = Math.min(rawImages.size(), frameCount);
        int skip = rawImages.size() - take;
        for (int i = 0; i < skip; i++) {
            rawImages.get(i).close();
        }

        // Populate exposures map from preview capture result — all ZSL frames share preview exposure
        double previewExpTime = 1.0;
        double previewISO = 100.0;
        long exposureTimeNs = 0;
        if (mPreviewCaptureResult != null) {
            Long expTimeNs = mPreviewCaptureResult.get(CaptureResult.SENSOR_EXPOSURE_TIME);
            Integer isoVal = mPreviewCaptureResult.get(CaptureResult.SENSOR_SENSITIVITY);
            if (expTimeNs != null) {
                exposureTimeNs = expTimeNs;
                previewExpTime = expTimeNs / 1_000_000_000.0;
            }
            if (isoVal != null) previewISO = isoVal.doubleValue();
        }
        final double exposureVal = previewExpTime * previewISO;
        boolean doZoomCrop = zoomController.isZoomed();
        // Copy selected Images to ImageFrames only now (on shutter press)
        List<ImageFrame> selected = new ArrayList<>();
        for (int i = skip; i < rawImages.size(); i++) {
            Image img = rawImages.get(i);
            int rowStride = img.getPlanes()[0].getRowStride();
            int pixelStride = img.getPlanes()[0].getPixelStride();
            int width = (img.getFormat() == ImageFormat.RAW10)
                    ? img.getWidth()
                    : (pixelStride > 0 ? rowStride / pixelStride : img.getWidth());
            int height = img.getHeight();
            int bufCapacity = img.getPlanes()[0].getBuffer().capacity();
            int offset = 0;

            // Digital zoom crop (crops both JPEG and RAW/DNG on the ZSL path).
            ImageFrame frame;
            if (doZoomCrop) {
                int logicalW = img.getWidth();
                int logicalH = img.getHeight();
                frame = ImageFrame.fromCrop(
                        img.getPlanes()[0].getBuffer(), img.getFormat(),
                        logicalW, logicalH, rowStride, pixelStride,
                        zoomController.computeCropRegion(logicalW, logicalH),
                        PhotonCamera.getSettings().binning);
                if (frame == null) { img.close(); continue; }
                frame.timestamp = img.getTimestamp();
                img.close();
                mExposures.put(frame.timestamp, exposureVal);
                if (take > 1) packZslBurstFrame(frame);
                selected.add(frame);
                continue;
            }

            if (PhotonCamera.getSettings().aspect169 && width > height) {
                height = width * 9 / 16;
                int offsetH = (img.getHeight() - height) / 2;
                offsetH -= offsetH % 2;
                offset = rowStride * offsetH;
                bufCapacity = rowStride * height;
            }
            boolean doBinning2 = PhotonCamera.getSettings().binning;
            synchronized (com.particlesdevs.photoncamera.util.Allocator.class) {
                Allocator.binning = doBinning2;
                frame = new ImageFrame(img.getPlanes()[0].getBuffer(), img.getFormat(), width, rowStride, offset, bufCapacity);
            }
            frame.timestamp = img.getTimestamp();
            frame.width = width;
            frame.height = height;
            if(doBinning2) {
                frame.width/= 2;
                frame.height/= 2;
            }
            img.close();
            mExposures.put(frame.timestamp, exposureVal);
            if (take > 1) packZslBurstFrame(frame);
            selected.add(frame);
        }
        int actualCount = selected.size();
        if (PhotonCamera.DEBUG) {
            int packedCount = 0;
            for (ImageFrame f : selected) {
                if (f != null && f.packedBits > 0) packedCount++;
            }
            Log.d(TAG, "ZSL arrival pack: " + packedCount + "/" + actualCount);
        }

        mImageSaver = new ImageSaver(cameraEventsListener);
        mImageSaver.setFrameCount(actualCount);
        mImageSaver.setImageFormat(CaptureController.RAW_FORMAT);
        mImageSaver.implementation = ImageSaverSelector.getImageSaver(CaptureController.RAW_FORMAT, mImageSaver.implementation);
        mImageSaver.implementation.frameCount = actualCount;

        SaverImplementation.IMAGE_BUFFER.clear();
        SaverImplementation.IMAGE_BUFFER.addAll(selected);

        mCaptureResult = mPreviewCaptureResult;
        mMeasuredFrameCnt = actualCount;

        cameraEventsListener.onFrameCountSet(actualCount);
        cameraEventsListener.onCaptureStillPictureStarted("ZSLCaptureStarted!");
        cameraEventsListener.onBurstPrepared(null);
        final double frametime = ExposureIndex.time2sec(IsoExpoSelector.GenerateExpoPair(-1, this).exposure);
        for (int i = 0; i < actualCount; i++) {
            cameraEventsListener.onFrameCaptureStarted(null);
            cameraEventsListener.onFrameCaptureCompleted(
                    new TimerFrameCountViewModel.FrameCntTime(i, actualCount, frametime));
        }
        cameraEventsListener.onCaptureSequenceCompleted(null);

        long[] frameTimestamps = new long[actualCount];
        for (int i = 0; i < actualCount; i++) {
            frameTimestamps[i] = selected.get(i).timestamp;
        }
        PhotonCamera.getGyro().buildZslBurstShakiness(frameTimestamps, exposureTimeNs, BurstShakiness);

        // Populate fullpairs the same way setExpo() does for a normal burst
        IsoExpoSelector.fullpairs.clear();
        for (int i = 0; i < actualCount; i++) {
            IsoExpoSelector.fullpairs.add(IsoExpoSelector.GenerateExpoPair(i, this));
        }

        final int capturedCount = actualCount;
        processExecutor.execute(() -> {
            try {
                PhotonCamera.getGyro().CompleteSequence();
                mBackgroundHandler.post(this::unlockFocus);
                if (capturedCount == 0) {
                    Log.w(TAG, "ZSL ring buffer was empty, no frames to process");
                    cameraEventsListener.onProcessingFinished("ZSL buffer empty");
                    return;
                }
                mImageSaver.implementation.bufferLock = false;
                mImageSaver.updateFrameCount(capturedCount);
                mImageSaver.runRaw(mCameraCharacteristics, mPreviewCaptureResult, mPreviewCaptureRequest,
                        new ArrayList<>(BurstShakiness), cameraRotation, mExposures);
            } catch (Exception e) {
                Log.e(TAG, "ZSL runRaw: " + Log.getStackTraceString(e));
                cameraEventsListener.onProcessingError(e.getLocalizedMessage());
            } finally {
                mZslCapturing = false;
            }
        });
    }

    private void captureStillPicture() {
        try {
            if (null == mCameraDevice) {
                return;
            }
            SensorConfigInjector.applyToSensor(getTunablePhysicalId(), this);
            if (isZslMode()) {
                triggerZslCapture();
                return;
            }
            // This is the CaptureRequest.Builder that we use to take a picture.
            final CaptureRequest.Builder captureBuilder;
            if(PhotonCamera.getSettings().selectedMode.equals(CameraMode.RAWVIDEO)) {
                captureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
                captureBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, getSelectedFpsRange());
            } else {
                captureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            }
            float focus = mFocus;
            double frametime = ExposureIndex.time2sec(IsoExpoSelector.GenerateExpoPair(-1, this).exposure);
            //this.mCaptureSession.stopRepeating();
            if(isDualSession) {
                if (mTargetFormat != mPreviewTargetFormat)
                    captureBuilder.addTarget(mImageReaderRaw.getSurface());
                else
                    captureBuilder.addTarget(mImageReaderPreview.getSurface());
            } else {
                captureBuilder.addTarget(mImageReaderRaw.getSurface());
                CameraMode selectedMode = PhotonCamera.getSettings().selectedMode;
                if(frametime > 0.06 && !isDualSession || selectedMode == CameraMode.RAWVIDEO || selectedMode == CameraMode.UNLIMITED || (!IsoExpoSelector.HDR)) {
                    captureBuilder.addTarget(surface);
                }
            }
            Camera2ApiAutoFix.applyEnergySaving();
            cameraRotation = PhotonCamera.getGravity().getCameraRotation(mSensorOrientation);

            //captureBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);
            //setCaptureAEMode(captureBuilder);
            if (mFlashed) captureBuilder.set(FLASH_MODE, FLASH_MODE_TORCH);
            Log.d(TAG, "Focus:" + focus);
            captureBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_CANCEL);

            if (VendorTagUtils.isOisSupported(activity, mCameraCharacteristics, getTunablePhysicalId())) {
                Log.d(TAG, "LENS_OPTICAL_STABILIZATION_MODE");
                applyOisMode(captureBuilder, true);//Fix ois bugs for preview and burst
            }

            if (paramController != null && paramController.WB != 0) {
                int wbVal = paramController.WB;
                if (wbVal >= 2000) {
                    RggbChannelVector gains;
                    ColorSpaceTransform transform;

                    if (paramController.isSpotWb && paramController.spotGains != null) {
                        gains = paramController.spotGains;
                        transform = (paramController.spotTransform != null)
                                ? paramController.spotTransform
                                : ColorTemperatureConverter.createColorTransform(wbVal, mCameraCharacteristics);

                        Log.d("WB_RESULT_DEBUG", "captureStillPicture Spot WB Set: Kelvin=" + wbVal
                                + " | Tint=" + paramController.spotTintStr
                                + " | Measured Gains=" + gains
                                + " | Transform=" + transform);
                    } else {
                        gains = ColorTemperatureConverter.kelvinToRggb(wbVal, mCameraCharacteristics);
                        transform = ColorTemperatureConverter.createColorTransform(wbVal, mCameraCharacteristics);

                        Log.d("WB_RESULT_DEBUG", "captureStillPicture Manual WB Set: Kelvin=" + wbVal
                                + " | Gains=" + gains
                                + " | Transform=" + transform);
                    }

                    captureBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF);
                    captureBuilder.set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX);
                    captureBuilder.set(CaptureRequest.COLOR_CORRECTION_GAINS, gains);
                    if (transform != null) {
                        captureBuilder.set(CaptureRequest.COLOR_CORRECTION_TRANSFORM, transform);
                    }
                }
            }

            for (int i = 0; i < 3; i++) {
                Log.d(TAG, "Temperature:" + mPreviewTemp[i]);
            }
            Log.d(TAG, "CaptureBuilderStarted!");
            //setAutoFlash(captureBuilder);
            //int rotation = Interface.getGravity().getCameraRotation();//activity.getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, PhotonCamera.getGravity().getCameraRotation(mSensorOrientation));
            applyZoom(captureBuilder, false);
            if (mTouchFocus != null && mTouchFocus.isTouchFocus) {
                captureBuilder.set(CaptureRequest.CONTROL_AE_REGIONS, mPreviewRequestBuilder.get(CaptureRequest.CONTROL_AE_REGIONS));
                captureBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, mPreviewRequestBuilder.get(CaptureRequest.CONTROL_AF_REGIONS));
            } else {
                applyAeMeteringRegions(captureBuilder);
            }
            VendorTagUtils.builderSessionApply(captureBuilder, true, useMaximumResolutionKey, physicalID);
            applyIszIfActive(captureBuilder, physicalID);
            try {
                captureBuilder.set(CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE, CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE_ON);
            } catch (Exception e) {
                Log.d(TAG, "Failed to set LENS_SHADING_MAP_MODE_ON:" + Log.getStackTraceString(e));
            }

            captures = new ArrayList<>();
            BurstShakiness = new ArrayList<>();
            mExposures = new HashMap<>();
            SaverImplementation.IMAGE_BUFFER.clear();

            int frameCount = FrameNumberSelector.getFrames();
            //if (frameCount == 1) frameCount++;
            cameraEventsListener.onFrameCountSet(frameCount);
            Log.d(TAG, "HDRFact1:" + paramController.isManualMode() + " HDRFact2:" + PhotonCamera.getSettings().alignAlgorithm);
            //IsoExpoSelector.HDR = (!manualParamModel.isManualMode()) && (PhotonCamera.getSettings().alignAlgorithm == 0);
            //IsoExpoSelector.HDR = (PhotonCamera.getSettings().alignAlgorithm == 1);
            Log.d(TAG, "HDR:" + IsoExpoSelector.HDR);
            Object mode = mPreviewRequestBuilder.get(CONTROL_AF_MODE);
            if(mode != null && (int) mode != CaptureRequest.CONTROL_AF_MODE_AUTO || PreferenceKeys.getAfMode() == CaptureRequest.CONTROL_AF_MODE_AUTO && !PhotonCamera.getSettings().selectedMode.equals(CameraMode.RAWVIDEO)) {
                captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
                captureBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);
            }
            //captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_EDOF);
            //if ((!(focus == 0.0 && Build.BRAND.equalsIgnoreCase("samsung")))) {
                MeteringRectangle rectaf = new MeteringRectangle(0, 0, 0, 0, 0);
                //captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CONTROL_AF_MODE_OFF);
                //captureBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CONTROL_AF_TRIGGER_CANCEL);
                //captureBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, focus);
                /*if(!mTouchFocus.isTouchFocus)
                    captureBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, new MeteringRectangle[]{rectaf});
                else {
                    captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
                    captureBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);
                    //captureBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, mFocus);
                }
                if (paramController.FOCUS != -1){
                    captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
                    captureBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, paramController.FOCUS);
                }*/
            //}
            /*
            if(!isDualSession){
                captureBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CONTROL_AF_TRIGGER_IDLE);
                captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
                captureBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, focus);
            }*/



            /*mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
            if (focus != 0.0)
                mPreviewRequestBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, focus);
            rebuildPreviewBuilder();*/

            IsoExpoSelector.useTripod = PhotonCamera.getGyro().getTripod();
            if (frameCount == -1) {
                for (int i = 0; i < 1; i++) {
                    if(!PhotonCamera.getSettings().selectedMode.equals(CameraMode.RAWVIDEO))
                        IsoExpoSelector.setExpo(captureBuilder, i, this);
                    else {
                        captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, mPreviewAFMode);
                        captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, mPreviewAEMode);
                    }
                    captures.add(captureBuilder.build());
                }
            } else {
                long[] times = new long[frameCount];
                for (int i = 0; i < frameCount; i++) {
                    IsoExpoSelector.setExpo(captureBuilder, i, this);
                    times[i] = IsoExpoSelector.lastSelectedExposure;
                    captures.add(captureBuilder.build());
                    mCaptureRequest = captureBuilder.build();
                }
                PhotonCamera.getGyro().PrepareGyroBurst(times, BurstShakiness);
            }

            //img
            Log.d(TAG, "FrameCount:" + frameCount);
            mImageSaver = new ImageSaver(cameraEventsListener);
            mImageSaver.setFrameCount(frameCount);
//            final int[] burstcount = {0, 0, frameCount};
            /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                mImageReaderRaw.discardFreeBuffers();
            }*/
            Log.d(TAG, "CaptureStarted!");

            final long[] baseFrameNumber = {0};
            final int[] maxFrameCount = {frameCount};

            cameraEventsListener.onCaptureStillPictureStarted("CaptureStarted!");
            mMeasuredFrameCnt = 0;

            cameraEventsListener.onBurstPrepared(null);
            this.CaptureCallback = new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureStarted(@NonNull CameraCaptureSession session,
                                             @NonNull CaptureRequest request,
                                             long timestamp,
                                             long frameNumber) {

                    if (baseFrameNumber[0] == 0) {
                        baseFrameNumber[0] = frameNumber;
                        if (maxFrameCount[0] != -1) PhotonCamera.getGyro().CaptureGyroBurst();
                        Log.v("BurstCounter", "CaptureStarted with FirstFrameNumber:" + frameNumber);
                    } else {
                        Log.v("BurstCounter", "CaptureStarted:" + frameNumber);
                    }
                    cameraEventsListener.onFrameCaptureStarted(null);
                    //if (maxFrameCount[0] != -1) PhotonCamera.getGyro().CaptureGyroBurst();
                }

                @Override
                public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request,
                                                @NonNull CaptureResult partialResult) {
                    int frameCount = (int) (partialResult.getFrameNumber() - baseFrameNumber[0]);
                    Log.v("BurstCounter", "CaptureProgressed! FrameCount:" + frameCount);
                    if (mCaptureResult == null) {
                        mCaptureResult = partialResult;
                    }
                }

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {

                    int frameCount = (int) (result.getFrameNumber() - baseFrameNumber[0]);
                    Log.v("BurstCounter", "CaptureCompleted! FrameCount:" + frameCount);
                    Object time = result.get(CaptureResult.SENSOR_TIMESTAMP);
                    Log.d(TAG, "Timestamp:" + time);
                    if (paramController != null && paramController.WB != 0) {
                        RggbChannelVector stillGains = result.get(CaptureResult.COLOR_CORRECTION_GAINS);
                        ColorSpaceTransform stillTransform = result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM);
                        Integer stillAwb = result.get(CaptureResult.CONTROL_AWB_MODE);
                        Rational[] stillNeutralPoint = result.get(CaptureResult.SENSOR_NEUTRAL_COLOR_POINT);

                        Log.d("WB_RESULT_DEBUG", "Still Picture HAL Result: AWB=" + stillAwb
                                + " | GAINS=" + stillGains
                                + " | TRANSFORM=" + stillTransform
                                + " | NEUTRAL_POINT=" + Arrays.toString(stillNeutralPoint));
                    }
                    if (time != null) {
                        // get exposure multiply ISO and exposure time
                        Object isoKey = result.get(CaptureResult.SENSOR_SENSITIVITY);
                        int iso = 100;
                        if (isoKey != null) {
                            iso = (int) isoKey;
                        }
                        Object timeKey = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
                        double exposureTime = ExposureIndex.time2sec((long) timeKey);
                        mExposures.put((long) time, exposureTime * iso);
                    }
                    cameraEventsListener.onFrameCaptureCompleted(
                            new TimerFrameCountViewModel.FrameCntTime(frameCount, maxFrameCount[0], frametime));

                    if (onUnlimited && !unlimitedStarted) {
                        mImageSaver.processStart(mCameraCharacteristics, result, request, cameraRotation);
                        unlimitedStarted = true;
                    }
                    //if(frameCount == 0)
                        mCaptureResult = result;
                    if (maxFrameCount[0] != -1) PhotonCamera.getGyro().CaptureGyroBurst();
                }

                @Override
                public void onCaptureSequenceCompleted(@NonNull CameraCaptureSession session,
                                                       int sequenceId,
                                                       long lastFrameNumber) {

                    int finalFrameCount = (int) (lastFrameNumber - baseFrameNumber[0]) + 1;
                    Log.v("BurstCounter", "CaptureSequenceCompleted! FrameCount:" + finalFrameCount);
                    Log.d("DefaultSaver", "CaptureSequenceCompleted! FrameCount:" + finalFrameCount);
                    Log.v("BurstCounter", "CaptureSequenceCompleted! LastFrameNumber:" + lastFrameNumber);
                    Log.d(TAG, "SequenceCompleted");
                    mMeasuredFrameCnt = finalFrameCount;
                    cameraEventsListener.onCaptureSequenceCompleted(null);
                    burst = false;
                    //unlockFocus();
                    //Surface texture related
                    //activity.runOnUiThread(() -> UpdateCameraCharacteristics(PhotonCamera.getSettings().mCameraID));
                    if (PhotonCamera.getSettings().selectedMode != CameraMode.UNLIMITED && PhotonCamera.getSettings().selectedMode != CameraMode.RAWVIDEO) {
                        //processExecutor.submit(() -> mImageSaver.runRaw(mCameraCharacteristics, mCaptureResult, new ArrayList<>(BurstShakiness), cameraRotation));
                        /*taskResults.removeIf(Future::isDone); //remove already completed results
                        Future<?> result =processExecutor.submit(() -> {
                            while (PhotonCamera.getGyro().capturingNumber < finalFrameCount){
                                try {
                                    Thread.sleep(1);
                                } catch (InterruptedException ignored) {
                                }
                            }
                            if (maxFrameCount[0] != -1) PhotonCamera.getGyro().CompleteGyroBurst();
                            mImageSaver.runRaw(mCameraCharacteristics, mCaptureResult, new ArrayList<>(BurstShakiness), cameraRotation);
                        });
                        //Future<?> result = processExecutor.submit(() -> mImageSaver.runRaw(mCameraCharacteristics, mCaptureResult, new ArrayList<>(BurstShakiness), cameraRotation));
                        taskResults.add(result);*/
                        processExecutor.execute(() -> {
                            int cnt = 0;
                            //int captureNumber = PhotonCamera.getGyro().capturingNumber;
                            while (PhotonCamera.getGyro().capturingNumber < finalFrameCount || mImageSaver.bufferSize() < finalFrameCount){
                                if(cnt > 1000) {
                                    Log.d(TAG, "GyroBurstTimeout");
                                    break;
                                }
                                try {
                                    Thread.sleep(1);
                                } catch (InterruptedException ignored) {
                                }
                                //if(captureNumber - PhotonCamera.getGyro().capturingNumber != 0)
                                //    cnt = 0;
                                //else
                                    cnt++;
                            }
                            PhotonCamera.getGyro().CompleteSequence();
                            mBackgroundHandler.post(() -> {
                                if (!isDualSession)
                                    unlockFocus();
                                else
                                    createCameraPreviewSession(false);
                            });
                            try{
                            if(mImageSaver.bufferSize() == 0){
                                return;
                            }
                            mImageSaver.updateFrameCount(mImageSaver.bufferSize());
                            if (mImageSaver.bufferSize() != 0)
                                mImageSaver.runRaw(mCameraCharacteristics, mCaptureResult, mCaptureRequest, new ArrayList<>(BurstShakiness), cameraRotation, mExposures);
                            } catch (Exception e){
                                Log.e(TAG, "runRaw:"+Log.getStackTraceString(e));
                                cameraEventsListener.onProcessingError(e.getLocalizedMessage());
                            }
                        });
                        /*mBackgroundHandler.post(() -> {
                                    while (PhotonCamera.getGyro().capturingNumber < finalFrameCount){
                                        try {
                                            Thread.sleep(1);
                                        } catch (InterruptedException ignored) {
                                        }
                                    }
                                    if (maxFrameCount[0] != -1) PhotonCamera.getGyro().CompleteGyroBurst();
                                    mImageSaver.runRaw(mCameraCharacteristics, mCaptureResult, new ArrayList<>(BurstShakiness), cameraRotation);
                                });*/
                        //mBackgroundHandler.post(() -> {mImageSaver.runRaw(mCameraCharacteristics, mCaptureResult, new ArrayList<>(BurstShakiness), cameraRotation);});
                    }
                }
            };
            //mCaptureSession.setRepeatingBurst(captures, CaptureCallback, null);
            burst = true;
            Camera2ApiAutoFix.ApplyBurst();
            if (isDualSession)
                createCameraPreviewSession(true);
            else {
            mCaptureSession.stopRepeating();
            mCaptureSession.abortCaptures();
                switch (PhotonCamera.getSettings().selectedMode) {
                    case UNLIMITED:
                        mCaptureSession.setRepeatingBurst(captures, CaptureCallback, mBackgroundHandler);
                        break;
                    case RAWVIDEO:
                        mCaptureSession.setRepeatingRequest(captures.get(0), CaptureCallback, mBackgroundHandler);
                        break;
                    case NIGHT:
                    case PHOTO:
                    case MOTION:
                        mCaptureSession.captureBurst(captures, CaptureCallback, mBackgroundHandler);
                        break;
                }
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
    }

    public void abortCaptures() {
        if (mCaptureSession == null) {
            return;
        }
        try {
            mCaptureSession.abortCaptures();
        } catch (CameraAccessException | IllegalStateException e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
    }

    public void reset3Aparams() {
        setAEMode(mPreviewRequestBuilder, PreferenceKeys.getAeMode());
        if (isVideoMode()) {
            // VIDEO owns AF: re-assert continuous video instead of restoring
            // the photo-mode pref.
            setAFMode(mPreviewRequestBuilder, CONTROL_AF_MODE_CONTINUOUS_VIDEO);
            mPreviewAFMode = CONTROL_AF_MODE_CONTINUOUS_VIDEO;
        } else {
            setAFMode(mPreviewRequestBuilder, PreferenceKeys.getAfMode());
        }
        rebuildPreviewBuilder();
    }

    public void setPreviewAEModeRebuild(int aeMode) {
        setAEMode(mPreviewRequestBuilder, aeMode);
        rebuildPreviewBuilder();
    }

    public void applyFpsRange() {
        if (mPreviewRequestBuilder == null) return;
        PhotonCamera.getSettings().fpsMode = PreferenceKeys.getFpsMode();
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, getSelectedFpsRange());
        rebuildPreviewBuilder();
    }

    /**
     * Applies the EIS toggle to a record-template (VIDEO mode) request live:
     * re-asserts VIDEO_STABILIZATION ON/OFF and resubmits the repeating
     * request, so no session rebuild is needed.
     */
    public void applyVideoStabilization() {
        if (mPreviewRequestBuilder == null) return;
        if (!isVideoMode() && !mIsRecordingVideo) return;
        try {
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                    PreferenceKeys.isEisPhotoOn()
                            ? CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON
                            : CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF);
        } catch (Exception e) {
            Log.w(TAG, "applyVideoStabilization failed", e);
            return;
        }
        rebuildPreviewBuilder();
    }

    public void applyAeMetering() {
        if (mPreviewRequestBuilder == null) return;
        applyAeMeteringRegions(mPreviewRequestBuilder);
        rebuildPreviewBuilder();
    }

    private void applyAeMeteringRegions(CaptureRequest.Builder builder) {
        int mode = PreferenceKeys.getAeMeteringStd();
        Log.d(TAG, "applyAeMeteringRegions mode:" + mode);
        MeteringRectangle[] rectangles = getAEMeteringRectangles(mode);
        if (mode == -1) {
            rectangles = mInitialMeteringAE;
        }
        Integer maxAeRegions = mCameraCharacteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE);
        if (maxAeRegions != null && maxAeRegions > 0) {
            builder.set(CaptureRequest.CONTROL_AE_REGIONS, rectangles);
            if (builder == mPreviewRequestBuilder) {
                mPreviewMeteringAE = rectangles;
            }
        }
    }

    private MeteringRectangle[] getAEMeteringRectangles(int mode) {
        Rect activeArray = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        if (activeArray == null) return null;

        int width = activeArray.width();
        int height = activeArray.height();

        switch (mode) {
            case 0: // Center Weighted
                Integer maxRegionsObj = mCameraCharacteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE);
                int maxRegions = maxRegionsObj != null ? maxRegionsObj : 0;
                if (maxRegions >= 3) {
                    // Concentric overlapping rectangles
                    // 1. Large Base: 70%, Weight: 200
                    int w1 = (int) (width * 0.70);
                    int h1 = (int) (height * 0.70);
                    int x1 = (width - w1) / 2;
                    int y1 = (height - h1) / 2;

                    // 2. Medium Core: 45%, Weight: 300
                    int w2 = (int) (width * 0.45);
                    int h2 = (int) (height * 0.45);
                    int x2 = (width - w2) / 2;
                    int y2 = (height - h2) / 2;

                    // 3. Small Center: 20%, Weight: 500
                    int w3 = (int) (width * 0.20);
                    int h3 = (int) (height * 0.20);
                    int x3 = (width - w3) / 2;
                    int y3 = (height - h3) / 2;

                    return new MeteringRectangle[]{
                            new MeteringRectangle(x1, y1, w1, h1, 200),
                            new MeteringRectangle(x2, y2, w2, h2, 300),
                            new MeteringRectangle(x3, y3, w3, h3, 500)
                    };
                } else {
                    // Fallback: single large center rectangle (60%)
                    int cwWidth = (int) (width * 0.60);
                    int cwHeight = (int) (height * 0.60);
                    int cwX = (width - cwWidth) / 2;
                    int cwY = (height - cwHeight) / 2;
                    return new MeteringRectangle[]{new MeteringRectangle(cwX, cwY, cwWidth, cwHeight, MeteringRectangle.METERING_WEIGHT_MAX)};
                }
            case 1: // Frame Average
                // Full active array
                return new MeteringRectangle[]{new MeteringRectangle(0, 0, width, height, MeteringRectangle.METERING_WEIGHT_MAX)};
            case 2: // Spot Metering
                // Approximately 2.5% of the sensor area (sqrt(0.025) ≈ 0.158)
                int sWidth = (int) (width * 0.158);
                int sHeight = (int) (height * 0.158);
                int sX = (width - sWidth) / 2;
                int sY = (height - sHeight) / 2;
                return new MeteringRectangle[]{new MeteringRectangle(sX, sY, sWidth, sHeight, MeteringRectangle.METERING_WEIGHT_MAX)};
            default:
                return null;
        }
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
        //mImageSaver.unlimitedEnd();
        mBackgroundHandler.post(() -> mImageSaver.processEnd());
        abortCaptures();
        createCameraPreviewSession(false);
        unlimitedStarted = false;
    }

    public void callUnlimitedStart() {
        onUnlimited = true;
        takePicture();
    }

    public void VideoEnd() {
        cancelLogicalZoom();
        mIsRecordingVideo = false;
        stopRecordingVideo();
    }

    public void VideoStart() {
        cancelLogicalZoom();
        mIsRecordingVideo = true;
        createCameraPreviewSession(false);
    }

    private CamcorderProfile resolveVideoProfile(int cameraId, String resolution) {
        int[] qualities;
        switch (resolution) {
            case "3840x2160": qualities = new int[]{CamcorderProfile.QUALITY_2160P, CamcorderProfile.QUALITY_1080P, CamcorderProfile.QUALITY_720P}; break;
            case "1280x720":  qualities = new int[]{CamcorderProfile.QUALITY_720P,  CamcorderProfile.QUALITY_1080P, CamcorderProfile.QUALITY_2160P}; break;
            default:          qualities = new int[]{CamcorderProfile.QUALITY_1080P, CamcorderProfile.QUALITY_720P,  CamcorderProfile.QUALITY_2160P}; break;
        }
        for (int q : qualities) {
            if (CamcorderProfile.hasProfile(cameraId, q)) {
                return CamcorderProfile.get(cameraId, q);
            }
        }
        return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_HIGH);
    }

    /** Physical camera id as int for CamcorderProfile queries (logical "0-2" -> 0). */
    private int parseVideoCameraId() {
        try {
            String idStr = physicalID != null ? physicalID : PhotonCamera.getSettings().mCameraID;
            if (idStr != null && idStr.contains("-")) idStr = idStr.split("-")[0];
            return Integer.parseInt(idStr);
        } catch (Exception e) {
            return 0;
        }
    }

    private android.util.Size resolveVideoSize(String resolution, CamcorderProfile profile) {
        return resolveVideoSize(resolution, profile, true);
    }

    /**
     * Recording size actually passed to {@code setVideoSize()}. Prefers the
     * user's selected resolution when the HAL lists it as a
     * {@code MediaRecorder} output — many HALs support 4K via Camera2 while
     * lacking a {@code QUALITY_2160P} CamcorderProfile, in which case the old
     * profile-only lookup silently recorded 1080p. Falls back to the largest
     * supported 16:9 size at or below the request, then to the profile size.
     *
     * @param announce true to Toast on fallback (record start), false for
     *                 silent callers such as preview-size matching.
     */
    private android.util.Size resolveVideoSize(String resolution, CamcorderProfile profile, boolean announce) {
        int reqW = profile.videoFrameWidth;
        int reqH = profile.videoFrameHeight;
        try {
            String[] parts = resolution.split("x");
            reqW = Integer.parseInt(parts[0].trim());
            reqH = Integer.parseInt(parts[1].trim());
        } catch (Exception e) {
            Log.w(TAG, "resolveVideoSize: unparsable resolution '" + resolution + "', using profile size", e);
            return new android.util.Size(profile.videoFrameWidth, profile.videoFrameHeight);
        }
        try {
            CameraCharacteristics chars = mCameraCharacteristics;
            if (chars == null) {
                try {
                    chars = mCameraCharacteristicsMap.get(physicalID);
                } catch (Exception ignored) {
                }
            }
            if (chars != null) {
                StreamConfigurationMap map = chars.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map != null) {
                    android.util.Size[] recSizes;
                    try {
                        recSizes = map.getOutputSizes(MediaRecorder.class);
                    } catch (Exception e) {
                        recSizes = null;
                    }
                    if (recSizes != null && recSizes.length > 0) {
                        for (android.util.Size s : recSizes) {
                            if (s != null && s.getWidth() == reqW && s.getHeight() == reqH) {
                                if (reqW != profile.videoFrameWidth || reqH != profile.videoFrameHeight) {
                                    Log.d(TAG, "video size " + reqW + "x" + reqH
                                            + " from MediaRecorder outputs (no matching CamcorderProfile)");
                                }
                                return new android.util.Size(reqW, reqH);
                            }
                        }
                        long reqArea = (long) reqW * (long) reqH;
                        android.util.Size best = null;
                        long bestArea = -1;
                        for (android.util.Size s : recSizes) {
                            if (s == null) continue;
                            int w = s.getWidth();
                            int h = s.getHeight();
                            if (w <= 0 || h <= 0) continue;
                            // Keep 16:9 family only.
                            if ((long) w * 9L != (long) h * 16L && (long) h * 9L != (long) w * 16L) continue;
                            long area = (long) w * (long) h;
                            if (area <= reqArea && area > bestArea) {
                                bestArea = area;
                                best = s;
                            }
                        }
                        if (best != null) {
                            if (best.getWidth() != profile.videoFrameWidth
                                    || best.getHeight() != profile.videoFrameHeight) {
                                Log.w(TAG, "video size " + reqW + "x" + reqH
                                        + " unsupported, using " + best.getWidth() + "x" + best.getHeight());
                                if (announce) {
                                    showToast("Video resolution not supported, using "
                                            + best.getWidth() + "x" + best.getHeight());
                                }
                            }
                            return new android.util.Size(best.getWidth(), best.getHeight());
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "resolveVideoSize: MediaRecorder-output check failed", e);
        }
        if (reqW != profile.videoFrameWidth || reqH != profile.videoFrameHeight) {
            Log.w(TAG, "video size " + reqW + "x" + reqH
                    + " unresolved, falling back to profile "
                    + profile.videoFrameWidth + "x" + profile.videoFrameHeight);
            if (announce) {
                showToast("Video resolution not available, using "
                        + profile.videoFrameWidth + "x" + profile.videoFrameHeight);
            }
        }
        return new android.util.Size(profile.videoFrameWidth, profile.videoFrameHeight);
    }

    /**
     * Best-effort 10-bit dynamic-range profile for the video session (API 33+).
     * HLG transfer maps to HLG10, PQ maps to HDR10. Falls back to the other
     * 10-bit profile when only one is advertised, else STANDARD (SDR).
     */
    private long resolveVideoDynamicRangeProfile() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return android.hardware.camera2.params.DynamicRangeProfiles.STANDARD;
        }
        try {
            // Physical characteristics often lack the 10-bit capability that
            // the logical camera advertises, so fall through the chain.
            CameraCharacteristics chars = null;
            String charsSource = "none";
            try {
                chars = mCameraCharacteristicsMap.get(physicalID);
                if (chars != null) charsSource = "physical:" + physicalID;
            } catch (Exception ignored) {
            }
            if (chars == null) {
                chars = mCameraCharacteristics;
                if (chars != null) charsSource = "current";
            }
            if (chars != null && !hasTenBitCapability(chars) && mCameraCharacteristicsMap != null) {
                for (Map.Entry<String, CameraCharacteristics> e : mCameraCharacteristicsMap.entrySet()) {
                    if (e != null && e.getValue() != null && hasTenBitCapability(e.getValue())) {
                        chars = e.getValue();
                        charsSource = "scan:" + e.getKey();
                        break;
                    }
                }
            }
            Log.d(TAG, "video HDR characteristics source=" + charsSource);
            if (chars == null) {
                return android.hardware.camera2.params.DynamicRangeProfiles.STANDARD;
            }
            int[] caps = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
            boolean tenBit = false;
            if (caps != null) {
                for (int c : caps) {
                    if (c == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DYNAMIC_RANGE_TEN_BIT) {
                        tenBit = true;
                        break;
                    }
                }
            }
            if (!tenBit) {
                Log.w(TAG, "video HDR: no 10-bit capability on " + charsSource);
                return android.hardware.camera2.params.DynamicRangeProfiles.STANDARD;
            }
            android.hardware.camera2.params.DynamicRangeProfiles profiles = chars.get(
                    CameraCharacteristics.REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES);
            if (profiles == null) {
                Log.w(TAG, "video HDR: no dynamic range profiles on " + charsSource);
                return android.hardware.camera2.params.DynamicRangeProfiles.STANDARD;
            }
            java.util.Set<Long> supported = profiles.getSupportedProfiles();
            Log.d(TAG, "video HDR supported profiles=" + supported);
            boolean wantPq = "pq".equalsIgnoreCase(PreferenceKeys.getVideoHdrTransfer());
            long hlg10 = android.hardware.camera2.params.DynamicRangeProfiles.HLG10;
            long hdr10 = android.hardware.camera2.params.DynamicRangeProfiles.HDR10;
            long picked = android.hardware.camera2.params.DynamicRangeProfiles.STANDARD;
            if (wantPq) {
                if (supported.contains(hdr10)) picked = hdr10;
                else if (supported.contains(hlg10)) picked = hlg10;
            } else {
                if (supported.contains(hlg10)) picked = hlg10;
                else if (supported.contains(hdr10)) picked = hdr10;
            }
            Log.d(TAG, "video HDR picked profile=" + picked + " (wantPq=" + wantPq + ")");
            return picked;
        } catch (Exception e) {
            Log.w(TAG, "resolveVideoDynamicRangeProfile failed", e);
        }
        return android.hardware.camera2.params.DynamicRangeProfiles.STANDARD;
    }

    private static boolean hasTenBitCapability(CameraCharacteristics chars) {
        try {
            int[] caps = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
            if (caps == null) return false;
            for (int c : caps) {
                if (c == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DYNAMIC_RANGE_TEN_BIT) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /**
     * Prefs + encoder-side HDR request (HEVC on, HDR on, Main10 encoder
     * present). The Camera2 10-bit session profile is resolved separately;
     * both must agree or the setup degrades to SDR.
     */
    private boolean isVideoHdrRequested() {
        try {
            return PreferenceKeys.isVideoHevc()
                    && PreferenceKeys.isVideoHdr()
                    && com.particlesdevs.photoncamera.processing.encoder.VideoCodecSupport.hasHevcEncoder()
                    && com.particlesdevs.photoncamera.processing.encoder.VideoCodecSupport.isHdrVideoSupported();
        } catch (Exception e) {
            Log.w(TAG, "video HDR pref check failed, using SDR", e);
            return false;
        }
    }

    private void setUpMediaRecorder(boolean allowHdr) {
        mMediaRecorder.reset();
        int audioSource = mAudioSourceRetry >= 0 ? mAudioSourceRetry : resolveAudioSource();
        mAudioSourceRetry = -1;
        mAudioSourceUsed = audioSource;
        mMediaRecorder.setAudioSource(audioSource);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        int cameraIdInt = parseVideoCameraId();
        String resolution = PreferenceKeys.getVideoResolution();
        CamcorderProfile profile = resolveVideoProfile(cameraIdInt, resolution);
        android.util.Size videoSize = resolveVideoSize(resolution, profile);
        Log.d(TAG, "video record " + resolution + " -> " + videoSize.getWidth()
                + "x" + videoSize.getHeight() + " (camera " + cameraIdInt + ")");
        int videoFrameRate = profile.videoFrameRate;
        try {
            // Align the container frame rate with the AE range actually
            // configured on the session (validated against the HAL).
            Range<Integer> fpsRange = getSelectedFpsRange();
            if (fpsRange != null && fpsRange.getUpper() != null && fpsRange.getUpper() > 0) {
                videoFrameRate = fpsRange.getUpper();
            }
        } catch (Exception e) {
            Log.w(TAG, "video frame rate align failed, using profile rate", e);
        }
        mVideoFrameRate = videoFrameRate;
        mMediaRecorder.setVideoFrameRate(videoFrameRate);
        mMediaRecorder.setVideoSize(videoSize.getWidth(), videoSize.getHeight());
        mVideoSize = videoSize;
        boolean useHevc = false;
        boolean wantHdr = false;
        try {
            useHevc = PreferenceKeys.isVideoHevc()
                    && com.particlesdevs.photoncamera.processing.encoder.VideoCodecSupport.hasHevcEncoder();
            wantHdr = allowHdr && useHevc && PreferenceKeys.isVideoHdr()
                    && com.particlesdevs.photoncamera.processing.encoder.VideoCodecSupport.isHdrVideoSupported();
        } catch (Exception e) {
            Log.w(TAG, "video codec pref check failed, using AVC/SDR", e);
        }
        String mime = useHevc ? android.media.MediaFormat.MIMETYPE_VIDEO_HEVC
                : android.media.MediaFormat.MIMETYPE_VIDEO_AVC;
        int requestedBps = profile.videoBitRate;
        try {
            int mbps = PreferenceKeys.getVideoBitrateMbps();
            if (mbps < 30) mbps = 30;
            if (mbps > 130) mbps = 130;
            requestedBps = mbps * 1_000_000;
        } catch (Exception e) {
            Log.w(TAG, "video bitrate pref invalid, using profile bitrate", e);
        }
        int bitrateBps;
        try {
            bitrateBps = com.particlesdevs.photoncamera.processing.encoder.VideoCodecSupport
                    .clampVideoBitrate(mime, requestedBps);
        } catch (Exception e) {
            bitrateBps = requestedBps;
        }
        mMediaRecorder.setVideoEncodingBitRate(bitrateBps);
        mVideoBitrateBps = bitrateBps;
        if (useHevc) {
            mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.HEVC);
        } else {
            mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        }
        if (wantHdr) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                // 10-bit capture needs the API 33 dynamic-range session API.
                Log.w(TAG, "HDR video needs API 33+, recording SDR HEVC");
                wantHdr = false;
            } else {
                try {
                    int level = android.media.MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel4;
                    if (videoSize.getWidth() >= 3840 || videoSize.getHeight() >= 2160) {
                        level = android.media.MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel51;
                    } else if (videoSize.getWidth() >= 1920 || videoSize.getHeight() >= 1080) {
                        level = android.media.MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel4;
                    } else {
                        level = android.media.MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel31;
                    }
                    mMediaRecorder.setVideoEncodingProfileLevel(
                            android.media.MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10, level);
                    Log.d(TAG, "video HDR10-bit Main10 requested, transfer=" + PreferenceKeys.getVideoHdrTransfer());
                } catch (Exception e) {
                    Log.w(TAG, "HDR Main10 profile level not accepted, falling back to SDR HEVC", e);
                    wantHdr = false;
                }
            }
        }
        mVideoHdrActive = wantHdr;
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        int audioChannels = 2;
        try {
            audioChannels = PreferenceKeys.isAudioStereo() ? 2 : 1;
            mMediaRecorder.setAudioChannels(audioChannels);
        } catch (Exception e) {
            Log.w(TAG, "stereo audio channels not accepted, falling back to mono", e);
            audioChannels = 1;
            try {
                mMediaRecorder.setAudioChannels(1);
            } catch (Exception ignored) {
            }
        }
        int audioBitrateBps = profile.audioBitRate;
        try {
            int kbps = PreferenceKeys.getAudioBitrateKbps();
            if (kbps < 32) kbps = 32;
            if (kbps > 512) kbps = 512;
            audioBitrateBps = kbps * 1000;
        } catch (Exception e) {
            Log.w(TAG, "audio bitrate pref invalid, using profile bitrate", e);
        }
        try {
            audioBitrateBps = com.particlesdevs.photoncamera.processing.encoder.AudioCodecSupport
                    .clampAudioBitrate(audioBitrateBps);
        } catch (Exception e) {
            Log.w(TAG, "audio bitrate clamp failed", e);
        }
        mMediaRecorder.setAudioEncodingBitRate(audioBitrateBps);
        mAudioBitrateBps = audioBitrateBps;
        mMediaRecorder.setAudioSamplingRate(profile.audioSampleRate);
        Log.d(TAG, "video audio source=" + audioSourceName(mAudioSourceUsed)
                + " channels=" + audioChannels + " bitrate=" + audioBitrateBps
                + " rate=" + profile.audioSampleRate);
        mMediaRecorder.setOnInfoListener(this);
        mMediaRecorder.setOrientationHint(PhotonCamera.getGravity().getCameraRotation(mSensorOrientation));
        Date currentDate = new Date();
        DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
        String dateText = dateFormat.format(currentDate);
        File dir = new File(Environment.getExternalStorageDirectory() + "//DCIM//Camera//");
        vid = new File(dir.getAbsolutePath(), "VID_" + dateText + ".mp4");
        try {
            vid.createNewFile();
        } catch (IOException e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
        mMediaRecorder.setOutputFile(vid.getAbsolutePath());
        try {
            mMediaRecorder.prepare();
            Log.d(TAG, "video record start");

        } catch (Exception e) {
            Log.w(TAG, "video record prepare failed: " + Log.getStackTraceString(e));
            try {
                if (vid != null && vid.exists()) vid.delete();
            } catch (Exception ignored) {
            }
            if (mAudioSourceUsed != MediaRecorder.AudioSource.MIC) {
                // Retry once with MIC before giving up (e.g. Unprocessed
                // unsupported on this HAL).
                Log.w(TAG, "retrying video setup with MIC audio source");
                showToast("Audio source unsupported, retrying with MIC");
                mAudioSourceRetry = MediaRecorder.AudioSource.MIC;
                setUpMediaRecorder(allowHdr);
                return;
            }
            showToast("Video recording failed");
            try {
                mMediaRecorder.reset();
            } catch (Exception ignored) {
            }
        }
    }

    /** Maps the audio source pref to a MediaRecorder source (CAMCORDER default). */
    private int resolveAudioSource() {
        try {
            String source = PreferenceKeys.getAudioSource();
            if ("mic".equalsIgnoreCase(source)) return MediaRecorder.AudioSource.MIC;
            if ("unprocessed".equalsIgnoreCase(source)) return MediaRecorder.AudioSource.UNPROCESSED;
        } catch (Exception e) {
            Log.w(TAG, "audio source pref invalid, using Camcorder", e);
        }
        return MediaRecorder.AudioSource.CAMCORDER;
    }

    private static String audioSourceName(int source) {
        if (source == MediaRecorder.AudioSource.MIC) return "MIC";
        if (source == MediaRecorder.AudioSource.UNPROCESSED) return "UNPROCESSED";
        if (source == MediaRecorder.AudioSource.CAMCORDER) return "CAMCORDER";
        return String.valueOf(source);
    }

    private void stopRecordingVideo() {
        mIsRecordingVideo = false;
        stopVideoRecTicker();
        try {
            cameraEventsListener.onVideoRecordingStopped();
        } catch (Exception ignored) {
        }

        try {
            mMediaRecorder.stop();
        } catch (Exception stopFailure) {
            Log.d(TAG, "Failed to stop recording " + Log.getStackTraceString(stopFailure));
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

    /**
     * Starts the REC badge ticker once {@link MediaRecorder#start()} succeeds.
     * Ticks on the main thread so the fragment can push elapsed/size straight
     * to the badge; stopped in {@link #stopRecordingVideo()}.
     */
    private void startVideoRecTicker() {
        stopVideoRecTicker();
        mVideoRecordStartMs = SystemClock.elapsedRealtime();
        try {
            cameraEventsListener.onVideoRecordingStarted();
        } catch (Exception ignored) {
        }
        try {
            if (mVideoRecTickHandler == null) {
                mVideoRecTickHandler = new Handler(Looper.getMainLooper());
            }
            mVideoRecTickRunnable = new Runnable() {
                @Override
                public void run() {
                    tickVideoRec();
                    if (mVideoRecTickHandler != null && mVideoRecTickRunnable == this
                            && mIsRecordingVideo) {
                        mVideoRecTickHandler.postDelayed(this, 500);
                    }
                }
            };
            mVideoRecTickHandler.post(mVideoRecTickRunnable);
        } catch (Exception e) {
            Log.w(TAG, "video rec ticker failed", e);
        }
    }

    private void tickVideoRec() {
        try {
            long elapsedMs = SystemClock.elapsedRealtime() - mVideoRecordStartMs;
            long totalBps = (long) Math.max(0, mVideoBitrateBps)
                    + (long) Math.max(0, mAudioBitrateBps);
            long estimatedBytes = totalBps / 8 * (elapsedMs / 1000);
            long availableBytes = 0;
            try {
                availableBytes = new File(Environment.getExternalStorageDirectory()
                        + "//DCIM//Camera//").getUsableSpace();
            } catch (Exception ignored) {
            }
            cameraEventsListener.onVideoRecordingTick(elapsedMs, estimatedBytes, availableBytes);
        } catch (Exception e) {
            Log.w(TAG, "video rec tick failed", e);
        }
    }

    private void stopVideoRecTicker() {
        try {
            if (mVideoRecTickHandler != null && mVideoRecTickRunnable != null) {
                mVideoRecTickHandler.removeCallbacks(mVideoRecTickRunnable);
            }
        } catch (Exception ignored) {
        }
        mVideoRecTickRunnable = null;
    }

    private void mul(Rect in, double k) {
        in.bottom *= k;
        in.left *= k;
        in.right *= k;
        in.top *= k;
    }

    @TestOnly
    private static void mulForTest(Rect in, double k) {
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
    public void resumeCamera() {
        isCameraResumed = true;
        // Fresh start after backgrounding: drop any switch queued before pause.
        lensSwitchScheduler.cancel();
        cycleStartScheduled = false;
        cycleStartRequestedMs = 0L;
        disconnectRecoveries.set(0);
        openToken.incrementAndGet();
        if(PhotonCamera.getSettings().previewFormat != 0) {
            mPreviewTargetFormat = PhotonCamera.getSettings().previewFormat;
        } else {
            mPreviewTargetFormat = ImageFormat.JPEG;
        }
        processExecutor.execute(() -> {
            if (mTextureView == null)
                mTextureView = new GLPreview(activity);
            if (mTextureView.isAvailable()) {
                // The GL surface survived backgrounding (no onSurfaceCreated will
                // fire on resume), so open the camera directly against the
                // existing SurfaceTexture instead of waiting for a callback.
                Log.d(TAG,"ID:"+mCameraCharacteristicsMap.get(physicalID));
                Size optimal = getPreviewOutputSize(getSafeDisplay(),
                        mCameraCharacteristicsMap.get(physicalID),
                        PhotonCamera.getSettings().selectedMode);
                openCamera(optimal.getWidth(), optimal.getHeight());
            } else {
                mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
                // The availability callback is delivered through the main-thread
                // handler; it may have fired between the check above and arming
                // the listener. Re-check so the camera is never left waiting for
                // an event that already happened.
                if (mTextureView.isAvailable()) {
                    Size optimal = getPreviewOutputSize(getSafeDisplay(),
                            mCameraCharacteristicsMap.get(physicalID),
                            PhotonCamera.getSettings().selectedMode);
                    openCamera(optimal.getWidth(), optimal.getHeight());
                }
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

    /**
     * Dynamically applies Optical Image Stabilization (OIS) configuration to a CaptureRequest.
     * Respects user preference (Default/Smart/Off) and physical hardware limitations.
     *
     * @param builder        the builder for which to configure OIS
     * @param isStillCapture true if configuring a still capture request, false for preview stream
     */
    private void applyOisMode(CaptureRequest.Builder builder, boolean isStillCapture) {
        if (VendorTagUtils.isOisSupported(activity, mCameraCharacteristics, getTunablePhysicalId())) {
            int oisMode = this.oisMode;
            if (oisMode == 2) {
                // Always Off
                builder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF);
            } else if (oisMode == 1) {
                // Smart Auto
                boolean isTripod = PhotonCamera.getGyro() != null && PhotonCamera.getGyro().getTripod();
                CameraMode mode = PhotonCamera.getSettings().selectedMode;
                boolean isContinuousCapture = (mode == CameraMode.UNLIMITED);

                if (isTripod || isContinuousCapture) {
                    builder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF);
                } else {
                    builder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON);
                }
            } else {
                // Default: Maintain 100% identical behavior with the original code.
                // For still captures, we explicitly force OIS ON.
                // For preview/video streams, we do NOT set the OIS key, letting the HAL default handle it.
                if (isStillCapture) {
                    builder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON);
                }
            }
        }
    }

    /**
     * Captures a single linear RAW frame for spot metering without triggering the photo save pipeline.
     * Guarded against buffer contention and fully non-blocking in continuous ZSL mode.
     */
    public void captureSingleRawForMetering(RawFrameCallback callback) {
        if (mCameraDevice == null || mCaptureSession == null || mImageReaderRaw == null) {
            return;
        }
        // 1. Safety guard: reject measurement if camera is busy capturing or processing HDR bursts
        if (isProcessing || burst || mZslCapturing) {
            Log.w(TAG, "captureSingleRawForMetering: camera pipeline busy, skipping measurement");
            return;
        }

        // 2. In ZSL mode, RAW frames stream continuously: intercept the next streaming frame with 0ms freeze
        if (isZslMode()) {
            mPendingRawMeteringCallback = callback;
            return;
        }

        // 3. Non-ZSL mode (Photo / Night): submit isolated single-shot RAW capture preserving live AF/AE lock
        try {
            CaptureRequest.Builder builder;
            if (mPreviewRequestBuilder != null) {
                // Inherit exact live 3A state (locked AF mode, lens distance, regions, OIS) to avoid resetting focus
                builder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                
                Integer afMode = mPreviewRequestBuilder.get(CaptureRequest.CONTROL_AF_MODE);
                if (afMode != null) builder.set(CaptureRequest.CONTROL_AF_MODE, afMode);
                
                Float focusDist = mPreviewRequestBuilder.get(CaptureRequest.LENS_FOCUS_DISTANCE);
                if (focusDist != null) builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, focusDist);

                MeteringRectangle[] afRegs = mPreviewRequestBuilder.get(CaptureRequest.CONTROL_AF_REGIONS);
                if (afRegs != null) builder.set(CaptureRequest.CONTROL_AF_REGIONS, afRegs);

                MeteringRectangle[] aeRegs = mPreviewRequestBuilder.get(CaptureRequest.CONTROL_AE_REGIONS);
                if (aeRegs != null) builder.set(CaptureRequest.CONTROL_AE_REGIONS, aeRegs);

                Integer aeMode = mPreviewRequestBuilder.get(CaptureRequest.CONTROL_AE_MODE);
                if (aeMode != null) builder.set(CaptureRequest.CONTROL_AE_MODE, aeMode);
            } else {
                builder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            }

            builder.addTarget(mImageReaderRaw.getSurface());

            if (mPreviewExposureTime > 0) {
                builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, mPreviewExposureTime);
            }
            if (mPreviewIso > 0) {
                builder.set(CaptureRequest.SENSOR_SENSITIVITY, mPreviewIso);
            }

            mPendingRawMeteringCallback = callback;
            mCaptureSession.capture(builder.build(), null, mBackgroundHandler);
        } catch (Exception e) {
            mPendingRawMeteringCallback = null;
            Log.e(TAG, "captureSingleRawForMetering failed: " + e.getMessage());
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