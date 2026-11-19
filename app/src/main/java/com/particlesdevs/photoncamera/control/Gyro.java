package com.particlesdevs.photoncamera.control;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import com.particlesdevs.photoncamera.util.Log;
import com.particlesdevs.photoncamera.api.CameraMode;
import com.particlesdevs.photoncamera.app.PhotonCamera;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.LinkedHashMap;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.OisSample;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import java.util.Queue;

import com.particlesdevs.photoncamera.util.SimpleStorageHelper;

public class Gyro {
    private static final String TAG = "Gyroscope";
    protected final float fk = 0.8f;
    private final SensorManager mSensorManager;
    private final Sensor mGyroSensor;
    private Sensor mAccelSensor;
    private Sensor mMagSensor;

    // ---- Gyroscope recording state ----
    // ~8 min at 400 Hz gyro; excess samples are silently dropped
    private static final int MAX_REC_SAMPLES = 200_000;
    private volatile boolean isVideoRecording = false;
    private volatile long videoFirstFrameTs = Long.MIN_VALUE;
    private long videoStartWallTimeMs;
    private Path videoOutputPath;
    private double videoFrameRate;

    private long[]  recTimestamps;
    private float[] recGx, recGy, recGz;   // rad/s
    private float[] recAx, recAy, recAz;   // m/s²  (latest accel at each gyro tick)
    private float[] recMx, recMy, recMz;   // µT     (latest mag at each gyro tick)
    private volatile int   recCount;
    private volatile float latestAx, latestAy, latestAz;
    private volatile float latestMx, latestMy, latestMz;
    private volatile boolean recHasMag;

    private final SensorEventListener mVideoRecordingListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (!isVideoRecording) return;
            switch (event.sensor.getType()) {
                case Sensor.TYPE_GYROSCOPE: {
                    int idx = recCount;
                    if (idx < MAX_REC_SAMPLES) {
                        recTimestamps[idx] = event.timestamp;
                        recGx[idx] = event.values[0];
                        recGy[idx] = event.values[1];
                        recGz[idx] = event.values[2];
                        recAx[idx] = latestAx;
                        recAy[idx] = latestAy;
                        recAz[idx] = latestAz;
                        recMx[idx] = latestMx;
                        recMy[idx] = latestMy;
                        recMz[idx] = latestMz;
                        recCount = idx + 1;
                    }
                    break;
                }
                case Sensor.TYPE_ACCELEROMETER:
                    latestAx = event.values[0];
                    latestAy = event.values[1];
                    latestAz = event.values[2];
                    break;
                case Sensor.TYPE_MAGNETIC_FIELD:
                    latestMx = event.values[0];
                    latestMy = event.values[1];
                    latestMz = event.values[2];
                    recHasMag = true;
                    break;
            }
        }
        @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    };
    public volatile float[] mAngles;
    private volatile int filter = -1;
    public volatile int tripodShakiness = 1000;
    private static int delayPreview = 500;
    public static int delayUs = delayPreview;
    int tripodDetectCount = 600;
    int tripodCounter = 0;
    public int gyroCircle = 1024;
    public GyroBurst circleBurst = new GyroBurst(gyroCircle);
    public int circleCount = 0;
    long temp = 0;
    public static final float NS2S = 1.0f / 1000000000.0f;
    private long prevStamp = 0;
    private volatile boolean registered;
    // Gyro is application-owned. Reuse one idle thread across camera resumes.
    private HandlerThread gyroThread;
    private Handler gyroHandler;

    // ---- Rotation vector-based orientation with moving average ----
    private static final int MOVING_AVERAGE_SIZE = 10;
    private final Sensor mRotationVectorSensor;
    private final float[] mRotationVector = new float[5]; // Use 5 for compatibility
    private final Queue<Float> mYawHistory = new LinkedList<>();
    private final Queue<Float> mPitchHistory = new LinkedList<>();
    private final Queue<Float> mRollHistory = new LinkedList<>();
    private float mYawSum = 0f;
    private float mPitchSum = 0f;
    private float mRollSum = 0f;

    private final SensorEventListener mRotationVectorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
                System.arraycopy(event.values, 0, mRotationVector, 0, event.values.length);
                updateOrientationHistory();
            }
        }
        @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    };

    private final SensorEventListener mGravityTracker = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent sensorEvent) {
            if (!registered) return;
            // SensorEvent.values is reused by Android; never retain its backing array.
            mAngles = sensorEvent.values.clone();
            history.add(sensorEvent.timestamp, mAngles[0], mAngles[1], mAngles[2]);
            float dt = prevStamp == 0 ? 0 : (sensorEvent.timestamp-prevStamp)*NS2S;
            circleCount %= gyroCircle;
            for (int axis = 0; axis < 3; axis++)
                circleBurst.movementss[axis][circleCount] = mAngles[axis]*dt;
            circleBurst.timestampss[circleCount] = sensorEvent.timestamp;
            circleCount++;
            getShakiness();
            prevStamp = sensorEvent.timestamp;
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int i) {
        }
    };

    public Gyro(SensorManager sensorManager) {
        this(sensorManager, new GyroHistory(65536));
    }

    Gyro(SensorManager sensorManager, GyroHistory history) {
        this.history = history;
        mSensorManager = sensorManager;
        mGyroSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        mRotationVectorSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
    }

    public synchronized void register() {
        if (registered) return;
        if (gyroThread == null) {
            gyroThread = new HandlerThread("PhotonGyroscope");
            gyroThread.start();
            gyroHandler = new Handler(gyroThread.getLooper());
        }
        prevStamp = 0;
        history.breakSequence();
        registered = true;
        // UI work during a burst must not delay gyro delivery past frame finalization.
        mSensorManager.registerListener(mGravityTracker, mGyroSensor, delayUs, gyroHandler);
        if (mRotationVectorSensor != null) {
            mSensorManager.registerListener(mRotationVectorListener, mRotationVectorSensor, SensorManager.SENSOR_DELAY_FASTEST);
        }
        mYawHistory.clear();
        mPitchHistory.clear();
        mRollHistory.clear();
        mYawSum = 0f;
        mPitchSum = 0f;
        mRollSum = 0f;
    }

    public synchronized void unregister() {
        if (!registered) return;
        registered = false;
        history.breakSequence();
        if (mAngles != null)
            mAngles = mAngles.clone();
        mSensorManager.unregisterListener(mGravityTracker, mGyroSensor);
        mSensorManager.unregisterListener(mRotationVectorListener, mRotationVectorSensor);
    }

    private final GyroHistory history;
    public volatile int capturingNumber;
    private ArrayList<GyroBurst> BurstShakiness;
    private final LinkedHashMap<Long, FrameTiming> burstFrames = new LinkedHashMap<>();
    private final LinkedHashMap<Long, FrameTiming> recentFrames = new LinkedHashMap<>();

    private static class FrameTiming {
        final long timestamp, exposure, skew;
        final boolean sharedTimebase;
        boolean oisValid;
        float oisX, oisY;
        FrameTiming(long timestamp, long exposure, long skew, boolean sharedTimebase) {
            this.timestamp = timestamp;
            this.exposure = exposure;
            this.skew = skew;
            this.sharedTimebase = sharedTimebase;
        }
    }

    public synchronized void PrepareGyroBurst(ArrayList<GyroBurst> result) {
        capturingNumber = 0;
        burstFrames.clear();
        result.clear();
        BurstShakiness = result;
        // Keep the sensor registered: restarting here loses motion before the first exposure.
    }

    public synchronized void CaptureGyroBurst(long timestamp, long requestedExposure) {
        if (!burstFrames.containsKey(timestamp)) {
            burstFrames.put(timestamp, new FrameTiming(timestamp, requestedExposure, 0, false));
            capturingNumber++;
        }
    }

    /** Capture results may arrive late; only their sensor times determine the window. */
    public synchronized void recordCaptureResult(CaptureResult result,
                                                  CameraCharacteristics characteristics) {
        Long timestamp = result.get(CaptureResult.SENSOR_TIMESTAMP);
        Long exposure = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
        Long skew = result.get(CaptureResult.SENSOR_ROLLING_SHUTTER_SKEW);
        if (timestamp == null || exposure == null) return;
        Integer source = characteristics.get(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE);
        boolean realtime = source != null
                && source == CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME;
        FrameTiming timing = new FrameTiming(timestamp, exposure, skew == null ? 0 : skew, realtime);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            OisSample[] samples = result.get(CaptureResult.STATISTICS_OIS_SAMPLES);
            long midpoint = timestamp+(exposure+timing.skew)/2;
            if (samples != null) {
                OisSample before = null, after = null;
                for (OisSample sample : samples) {
                    if (sample.getTimestamp() <= midpoint && (before == null
                            || sample.getTimestamp() > before.getTimestamp())) before = sample;
                    if (sample.getTimestamp() >= midpoint && (after == null
                            || sample.getTimestamp() < after.getTimestamp())) after = sample;
                }
                if (before != null && after != null) {
                    double weight = after.getTimestamp() == before.getTimestamp() ? 0
                            : (double)(midpoint-before.getTimestamp())/(after.getTimestamp()-before.getTimestamp());
                    timing.oisX = (float)(before.getXshift()+(after.getXshift()-before.getXshift())*weight);
                    timing.oisY = (float)(before.getYshift()+(after.getYshift()-before.getYshift())*weight);
                    timing.oisValid = true;
                }
            }
        }
        recentFrames.put(timestamp, timing);
        while (recentFrames.size() > 256)
            recentFrames.remove(recentFrames.keySet().iterator().next());
        if (burstFrames.containsKey(timestamp)) burstFrames.put(timestamp, timing);
    }

    public synchronized void buildZslBurstShakiness(long[] timestamps, ArrayList<GyroBurst> result) {
        PrepareGyroBurst(result);
        for (long timestamp : timestamps) {
            FrameTiming timing = recentFrames.get(timestamp);
            burstFrames.put(timestamp, timing != null ? timing
                    : new FrameTiming(timestamp, 0, 0, false));
        }
        capturingNumber = burstFrames.size();
    }

    public synchronized void CompleteSequence() {
        if (BurstShakiness == null || burstFrames.isEmpty()) return;
        long lastEnd = 0;
        for (FrameTiming timing : burstFrames.values()) {
            if (timing.sharedTimebase)
                lastEnd = Math.max(lastEnd, timing.timestamp+timing.exposure+timing.skew);
        }
        // Sensor callbacks may trail the last result. Bound the wait; an uncovered
        // window stays invalid instead of extrapolating stale angular velocities.
        if (lastEnd > 0 && mGyroSensor != null) history.awaitTimestamp(lastEnd, 100);
        BurstShakiness.clear();
        for (FrameTiming timing : burstFrames.values()) {
            GyroBurst record = history.snapshot(timing.timestamp, timing.exposure,
                    timing.skew, timing.sharedTimebase);
            record.oisValid = timing.oisValid;
            record.oisX = timing.oisX;
            record.oisY = timing.oisY;
            BurstShakiness.add(record);
            Log.d(TAG, "Gyro frame ts="+record.frameTimestamp+" samples="+record.samples
                    +" valid="+record.orientationValid+" shake="+record.shakiness);
        }
        burstFrames.clear();
    }

    /** Request telemetry only on cameras which advertise it; does not change OIS mode. */
    public static void enableOisReporting(CaptureRequest.Builder request, CameraCharacteristics camera) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return;
        int[] modes = camera.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_OIS_DATA_MODES);
        if (modes == null) return;
        for (int mode : modes) if (mode == CaptureRequest.STATISTICS_OIS_DATA_MODE_ON) {
            request.set(CaptureRequest.STATISTICS_OIS_DATA_MODE, mode);
            return;
        }
    }

    private final Object shakinessLock = new Object();

    public int getShakiness() {
        synchronized (shakinessLock) {
            if (mAngles == null) {
                return 0;
            }
            int output = 0;
            for (float f : mAngles) {
                output += Math.abs((int) (f * 1000));
            }
            if (filter == -1) {
                filter = output;
            }
            output = (int) (output * (1.0f - fk) + filter * (fk));
            filter = output;
            tripodCounter++;
            tripodCounter%=tripodDetectCount;
            if(tripodCounter == tripodDetectCount-1){
                tripodShakiness = (int) (temp);
                temp = 0;
            } else {
                temp = Math.max(output,temp);
            }
            return output;
        }
    }
    public int getFilteredShakiness() {
        return filter;
    }
    public boolean getTripod(){
        return (tripodShakiness < 25) && PhotonCamera.getSettings().selectedMode == CameraMode.NIGHT;
    }

    private void updateOrientationHistory() {
        float[] rotationMatrix = new float[9];
        SensorManager.getRotationMatrixFromVector(rotationMatrix, mRotationVector);

        float[] remappedRotationMatrix = new float[9];
        SensorManager.remapCoordinateSystem(rotationMatrix,
                SensorManager.AXIS_X, SensorManager.AXIS_Z,
                remappedRotationMatrix);

        float[] orientationAngles = new float[3];
        SensorManager.getOrientation(remappedRotationMatrix, orientationAngles);

        float currentYaw = (float) Math.toDegrees(orientationAngles[0]);
        float currentPitch = (float) Math.toDegrees(orientationAngles[1]);
        float currentRoll = (float) Math.toDegrees(orientationAngles[2]);

        // Update Yaw history
        mYawHistory.add(currentYaw);
        mYawSum += currentYaw;
        if (mYawHistory.size() > MOVING_AVERAGE_SIZE) {
            mYawSum -= mYawHistory.poll();
        }

        // Update Pitch history
        mPitchHistory.add(currentPitch);
        mPitchSum += currentPitch;
        if (mPitchHistory.size() > MOVING_AVERAGE_SIZE) {
            mPitchSum -= mPitchHistory.poll();
        }

        // Update Roll history
        mRollHistory.add(currentRoll);
        mRollSum += currentRoll;
        if (mRollHistory.size() > MOVING_AVERAGE_SIZE) {
            mRollSum -= mRollHistory.poll();
        }
    }

    /**
     * @return The damped Yaw angle (azimuth/compass) in DEGREES.
     */
    public float getYaw() {
        if (mYawHistory.isEmpty()) return 0f;
        return mYawSum / mYawHistory.size();
    }

    /**
     * @return The damped Pitch angle (sky/ground tilt) in DEGREES.
     */
    public float getPitch() {
        if (mPitchHistory.isEmpty()) return 0f;
        return mPitchSum / mPitchHistory.size();
    }

    /**
     * @return The damped Roll angle (steering wheel motion) in DEGREES.
     */
    public float getRoll() {
        if (mRollHistory.isEmpty()) return 0f;
        return mRollSum / mRollHistory.size();
    }

    // -------------------------------------------------------------------------
    // Gyroscore GCSV recording
    // -------------------------------------------------------------------------

    /**
     * Begin buffering IMU data for a Gyroscore sidecar file.
     * Call this when RAW video recording starts (before the first frame).
     */
    public void startVideoRecording(Path outputFolder, double frameRate) {
        if (isVideoRecording) return;
        videoOutputPath      = outputFolder.resolve("GYROFLOW.gcsv");
        videoFrameRate       = frameRate;
        videoFirstFrameTs    = Long.MIN_VALUE;
        videoStartWallTimeMs = System.currentTimeMillis();

        recTimestamps = new long[MAX_REC_SAMPLES];
        recGx = new float[MAX_REC_SAMPLES];
        recGy = new float[MAX_REC_SAMPLES];
        recGz = new float[MAX_REC_SAMPLES];
        recAx = new float[MAX_REC_SAMPLES];
        recAy = new float[MAX_REC_SAMPLES];
        recAz = new float[MAX_REC_SAMPLES];
        recMx = new float[MAX_REC_SAMPLES];
        recMy = new float[MAX_REC_SAMPLES];
        recMz = new float[MAX_REC_SAMPLES];
        recCount  = 0;
        recHasMag = false;
        latestAx = latestAy = latestAz = 0f;
        latestMx = latestMy = latestMz = 0f;

        if (mAccelSensor == null)
            mAccelSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (mMagSensor == null)
            mMagSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        mSensorManager.registerListener(mVideoRecordingListener, mGyroSensor,
                SensorManager.SENSOR_DELAY_FASTEST);
        if (mAccelSensor != null)
            mSensorManager.registerListener(mVideoRecordingListener, mAccelSensor,
                    SensorManager.SENSOR_DELAY_FASTEST);
        if (mMagSensor != null)
            mSensorManager.registerListener(mVideoRecordingListener, mMagSensor,
                    SensorManager.SENSOR_DELAY_FASTEST);

        isVideoRecording = true;
        Log.d(TAG, "Gyroflow recording started, output: " + videoOutputPath);
    }

    /**
     * Mark the sensor timestamp of the first camera frame so the GCSV timestamps
     * are relative to it (t=0 = first frame).  Negative t values in the GCSV
     * represent data captured before the first frame, which Gyroflow ignores.
     *
     * @param cameraTimestampNs {@code Image.getTimestamp()} of the first captured frame
     */
    public void syncFirstFrame(long cameraTimestampNs) {
        if (videoFirstFrameTs == Long.MIN_VALUE)
            videoFirstFrameTs = cameraTimestampNs;
    }

    /** Gyro samples for embedding into a MediaCinemaRAW container. */
    public static final class MediaCinemaRawSamples {
        public final long[] timestampsNs;
        public final float[] x, y, z;
        public final int count;
        public MediaCinemaRawSamples(long[] timestampsNs, float[] x, float[] y, float[] z, int count) {
            this.timestampsNs = timestampsNs;
            this.x = x; this.y = y; this.z = z;
            this.count = count;
        }
    }

    /**
     * Stops buffering and returns the gyro samples for container embedding
     * instead of writing a GCSV sidecar. Timestamps share the camera
     * timestamp domain (the GCSV writer also mixes them directly).
     */
    public synchronized MediaCinemaRawSamples stopVideoRecordingForContainer() {
        if (!isVideoRecording) {
            return new MediaCinemaRawSamples(new long[0], new float[0], new float[0], new float[0], 0);
        }
        isVideoRecording = false;
        mSensorManager.unregisterListener(mVideoRecordingListener, mGyroSensor);
        if (mAccelSensor != null)
            mSensorManager.unregisterListener(mVideoRecordingListener, mAccelSensor);
        if (mMagSensor != null)
            mSensorManager.unregisterListener(mVideoRecordingListener, mMagSensor);
        MediaCinemaRawSamples samples = new MediaCinemaRawSamples(
                recTimestamps, recGx, recGy, recGz, recCount);
        recTimestamps = null;
        recGx = recGy = recGz = null;
        recAx = recAy = recAz = null;
        recMx = recMy = recMz = null;
        return samples;
    }

    /**
     * Stop buffering and write the GCSV sidecar file on a background thread.
     */
    public void stopVideoRecording() {
        if (!isVideoRecording) return;
        isVideoRecording = false;

        mSensorManager.unregisterListener(mVideoRecordingListener, mGyroSensor);
        if (mAccelSensor != null)
            mSensorManager.unregisterListener(mVideoRecordingListener, mAccelSensor);
        if (mMagSensor != null)
            mSensorManager.unregisterListener(mVideoRecordingListener, mMagSensor);

        final int    count       = recCount;
        final long   originTs    = videoFirstFrameTs;
        final long[] timestamps  = recTimestamps;
        final float[] gx = recGx, gy = recGy, gz = recGz;
        final float[] ax = recAx, ay = recAy, az = recAz;
        final float[] mx = recMx, my = recMy, mz = recMz;
        final boolean hasMag     = recHasMag;
        final Path    outPath    = videoOutputPath;
        final double  frameRate  = videoFrameRate;
        final long    wallTimeMs = videoStartWallTimeMs;

        recTimestamps = null;
        recGx = recGy = recGz = null;
        recAx = recAy = recAz = null;
        recMx = recMy = recMz = null;

        new Thread(() -> writeGcsv(outPath, timestamps, gx, gy, gz, ax, ay, az,
                mx, my, mz, hasMag, count, originTs, frameRate, wallTimeMs),
                "GyroflowWriter").start();
    }

    /**
     * Opens a {@link PrintWriter} for the GCSV file.
     * Primary: SAF-backed OutputStream via {@link SimpleStorageHelper} — required on
     * Android 11+ to bypass FUSE MediaProvider write restrictions in DCIM directories.
     * Fallback: {@link Files#newOutputStream} for app-specific or test paths.
     */
    private PrintWriter openGcsvWriter(Path outPath) {
        OutputStream os = SimpleStorageHelper.openOutputStreamByAbsPath(outPath.toString());
        if (os != null) {
            return new PrintWriter(new BufferedWriter(
                    new OutputStreamWriter(os, StandardCharsets.UTF_8)));
        }
        Log.w(TAG, "SAF open failed for GCSV, falling back to Files.newOutputStream");
        try {
            return new PrintWriter(new BufferedWriter(
                    new OutputStreamWriter(Files.newOutputStream(outPath), StandardCharsets.UTF_8)));
        } catch (IOException e) {
            Log.e(TAG, "openGcsvWriter fallback failed: " + e.getMessage());
            return null;
        }
    }

    private void writeGcsv(Path outPath,
                           long[] timestamps,
                           float[] gx, float[] gy, float[] gz,
                           float[] ax, float[] ay, float[] az,
                           float[] mx, float[] my, float[] mz,
                           boolean hasMag, int count,
                           long originTs, double frameRate, long wallTimeMs) {
        // Fall back to first sensor sample as origin if no frame was synced.
        long origin = (originTs != Long.MIN_VALUE) ? originTs
                : (count > 0 ? timestamps[0] : 0L);

        PrintWriter pw = openGcsvWriter(outPath);
        if (pw == null) {
            Log.e(TAG, "Cannot open GCSV output: " + outPath);
            return;
        }
        // PrintWriter silently swallows write errors; use checkError() at the end.
        try (PrintWriter p = pw) {
            p.println("GYROFLOW IMU LOG");
            p.println("version,1.3");
            p.println("id,photoncamera_android");
            // XYZ = identity mapping; user may need to adjust in Gyroflow's
            // IMU Orientation field to match their specific device.
            p.println("orientation,XYZ");
            p.println("note,PhotonCamera RAW Video");
            p.println("fwversion," + PhotonCamera.getVersion());
            p.println("timestamp," + (wallTimeMs / 1000L));
            p.println("vendor,PhotonCamera");
            p.println("videofilename," + outPath.getParent().getFileName());
            // tscale: timestamps are stored as nanoseconds → seconds = × 1e-9
            p.println("tscale,1.0e-9");
            // gscale: Android TYPE_GYROSCOPE already in rad/s → 1.0
            p.println("gscale,1.0");
            // ascale: Android TYPE_ACCELEROMETER in m/s²; Gyroflow expects g
            p.printf("ascale,%.10f%n", 1.0 / 9.80665);
            if (hasMag) {
                // mscale: Android TYPE_MAGNETIC_FIELD in µT; 1 gauss = 100 µT
                p.println("mscale,0.01");
                p.println("t,gx,gy,gz,ax,ay,az,mx,my,mz");
            } else {
                p.println("t,gx,gy,gz,ax,ay,az");
            }

            StringBuilder sb = new StringBuilder(128);
            for (int i = 0; i < count; i++) {
                sb.setLength(0);
                sb.append(timestamps[i] - origin);
                sb.append(',').append(gx[i]);
                sb.append(',').append(gy[i]);
                sb.append(',').append(gz[i]);
                sb.append(',').append(ax[i]);
                sb.append(',').append(ay[i]);
                sb.append(',').append(az[i]);
                if (hasMag) {
                    sb.append(',').append(mx[i]);
                    sb.append(',').append(my[i]);
                    sb.append(',').append(mz[i]);
                }
                p.println(sb);
            }

            if (p.checkError()) {
                Log.e(TAG, "GCSV write error (disk full or SAF fault): " + outPath);
            } else {
                Log.d(TAG, "GCSV written: " + count + " samples → " + outPath);
            }
        }
    }
}
