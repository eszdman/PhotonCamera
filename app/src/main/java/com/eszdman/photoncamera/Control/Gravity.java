package com.eszdman.photoncamera.Control;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import com.eszdman.photoncamera.api.Interface;

public class Gravity {
    private final SensorManager mSensorManager;
    private final Sensor mGravitySensor;
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
    private final SensorEventListener mGravityTracker = new SensorEventListener() {
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
            return 90;
        }
        String TAG = "Gravity";
        for(float f:mGravity) Log.d(TAG,"gravity:"+f);
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
    public int getCameraRotation(){
       return (Interface.i.camera.mSensorOrientation+Interface.i.gravity.getRotation()+270) % 360;
    }
}
