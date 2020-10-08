package com.eszdman.photoncamera.control;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

public class Sensors {
    private static final String TAG = "Gyroscope";
    private final SensorManager mSensorManager;
    private final Sensor mGyroSensor;
    public float[] mAngles;

    public Sensors(SensorManager sensorManager) {
        mSensorManager = sensorManager;
        mGyroSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
    }

    public void register() {
        mSensorManager.registerListener(mGravityTracker, mGyroSensor, SensorManager.SENSOR_DELAY_FASTEST);
    }

    public void unregister() {
        if (mAngles != null)
            mAngles = mAngles.clone();
        mSensorManager.unregisterListener(mGravityTracker, mGyroSensor);
    }

    private final SensorEventListener mGravityTracker = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent sensorEvent) {
            mAngles = sensorEvent.values;
            getShakiness();//For filtering
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int i) {
        }
    };
    private int filter = -1;
    protected final float fk = 0.8f;

    public int getShakiness() {
        if (mAngles == null) {
            return 0;
        }
        int output = 0;
        for (float f : mAngles) {
            output += Math.abs((int) (f * 1000));
        }
        if (filter == -1) filter = output;
        output = (int) (output * (1.0f - fk) + filter * (fk));
        filter = output;
        return output;
    }
}
