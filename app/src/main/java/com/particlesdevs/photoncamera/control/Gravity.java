package com.particlesdevs.photoncamera.control;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

public class Gravity {
    private static final String TAG = "Gravity";
    private final SensorManager mSensorManager;
    private final Sensor mGravitySensor;
    public float[] mGravity;
    private final SensorEventListener mGravityTracker = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent sensorEvent) {
            mGravity = sensorEvent.values;
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int i) {
        }
    };

    public Gravity(SensorManager sensorManager) {
        mSensorManager = sensorManager;
        mGravitySensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
    }

    public void register() {
        mSensorManager.registerListener(mGravityTracker, mGravitySensor, SensorManager.SENSOR_DELAY_FASTEST);
    }

    public void unregister() {
        if (mGravity != null)
            mGravity = mGravity.clone();
        mSensorManager.unregisterListener(mGravityTracker, mGravitySensor);
    }

    public int getRotation() {

        if (mGravity == null) {
            return 90;
        }
        if (mGravity[2] > 9f) //pointing at the ground
            return 90;

        if (Math.abs(mGravity[0]) > Math.abs(mGravity[1])) {
            if (mGravity[0] > 0f)
                return 0;
            else
                return 180;
        } else {
            if (mGravity[1] > 1.5f)
                return 90;
            else
                return 270;
        }
    }

    public int getCameraRotation(int sensorOrientation) {
        return (sensorOrientation + getRotation() + 270) % 360;
    }
}
