package com.eszdman.photoncamera.Control;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import com.eszdman.photoncamera.api.Interface;

import java.nio.IntBuffer;

public class Gravity {
    private SensorManager mSensorManager;
    private Sensor mGravitySensor;
    public float[] mGravity;


    public Gravity(){
        mSensorManager = (SensorManager) Interface.i.mainActivity.getSystemService(Context.SENSOR_SERVICE);
        mGravitySensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);

    }
    public void run(){
        mSensorManager.registerListener(mGravityTracker,mGravitySensor,SensorManager.SENSOR_DELAY_FASTEST);
    }
    public void stop(){
        mSensorManager.unregisterListener(mGravityTracker,mGravitySensor);
    }
    private SensorEventListener mGravityTracker = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent sensorEvent) {
            if (mGravity == null) {
                mGravity = sensorEvent.values.clone();
            }

            for (int i = 0; i < sensorEvent.values.length; i++) {
                mGravity[i] = sensorEvent.values[i];
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int i) { }
    };
    public int getRotation() {
        if (mGravity == null) {
            return 0;
        }

        if (mGravity[2] > 9f) //pointing at the ground
            return 0;

        if (Math.abs(mGravity[0]) > Math.abs(mGravity[1])) {
            if (mGravity[0] > 0f)
                return 90;
            else
                return 270;
        } else {
            if (mGravity[1] > 0f)
                return 0;
            else
                return 180;
        }
    }
}
