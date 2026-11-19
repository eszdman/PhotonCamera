package com.particlesdevs.photoncamera.util;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.HandlerThread;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;

/** Bounded, streaming IMU capture. No resampling accelerometer data onto gyro timestamps. */
public final class CrawImuRecorder implements SensorEventListener {
    private static final int MAX_SAMPLES = 16384;
    private final SensorManager manager;
    private final ArrayDeque<Sample> samples = new ArrayDeque<>();
    private HandlerThread thread;
    private boolean running;
    private long dropped;

    private static final class Sample {
        final int sensor;
        final long ts;
        final float x, y, z;
        Sample(SensorEvent event) {
            sensor = event.sensor.getType() == Sensor.TYPE_GYROSCOPE ? 1 : 2;
            ts = event.timestamp;
            x = event.values[0]; y = event.values[1]; z = event.values[2];
        }
    }

    public CrawImuRecorder(Context context) {
        manager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
    }

    public void start() {
        synchronized (samples) { samples.clear(); dropped = 0; running = true; }
        thread = new HandlerThread("CRAW-IMU");
        thread.start();
        Handler handler = new Handler(thread.getLooper());
        for (int type : new int[]{Sensor.TYPE_GYROSCOPE, Sensor.TYPE_ACCELEROMETER}) {
            Sensor sensor = manager.getDefaultSensor(type);
            if (sensor == null || !manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST, handler))
                Log.w("CrawImuRecorder", "Sensor unavailable: " + type);
        }
    }

    @Override public void onSensorChanged(SensorEvent event) {
        synchronized (samples) {
            if (!running) return;
            if (samples.size() == MAX_SAMPLES) { samples.removeFirst(); dropped++; }
            samples.addLast(new Sample(event));
        }
    }
    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    public void stop() {
        synchronized (samples) { running = false; }
        manager.unregisterListener(this);
        if (thread != null) { thread.quitSafely(); thread = null; }
        if (dropped > 0) Log.w("CrawImuRecorder", "IMU samples dropped during storage backlog: " + dropped);
    }

    /** Writer thread only; both camera and sensor timestamps are rebased to the first video frame. */
    public void drain(CrawWriter writer, long cameraOrigin, long sensorToCameraOffset) throws IOException {
        ArrayList<Sample> batch;
        synchronized (samples) { batch = new ArrayList<>(samples); samples.clear(); }
        for (int sensor = 1; sensor <= 2; sensor++) {
            int count = 0;
            for (Sample s : batch) if (s.sensor == sensor && s.ts + sensorToCameraOffset >= cameraOrigin) count++;
            if (count == 0) continue;
            long[] timestamps = new long[count];
            float[] xyz = new float[count * 3];
            int i = 0;
            for (Sample s : batch) {
                long ts = s.ts + sensorToCameraOffset - cameraOrigin;
                if (s.sensor != sensor || ts < 0) continue;
                timestamps[i] = ts;
                xyz[i * 3] = s.x; xyz[i * 3 + 1] = s.y; xyz[i * 3 + 2] = s.z;
                i++;
            }
            writer.writeImu(sensor, timestamps, xyz);
        }
    }
}
