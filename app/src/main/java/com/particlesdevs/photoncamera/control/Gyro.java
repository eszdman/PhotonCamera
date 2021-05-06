package com.particlesdevs.photoncamera.control;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import com.particlesdevs.photoncamera.api.CameraMode;
import com.particlesdevs.photoncamera.app.PhotonCamera;

import java.util.ArrayList;

public class Gyro {
    private static final String TAG = "Gyroscope";
    protected final float fk = 0.8f;
    private final SensorManager mSensorManager;
    private final Sensor mGyroSensor;
    public float[] mAngles;
    private boolean gyroburst = false;
    private float burstout = 0.f;
    private long timeCount = 0;
    private GyroBurst gyroBurst;
    private int filter = -1;
    public int tripodShakiness = 1000;
    public static final int delayUs = 1000;
    int tripodDetectCount = 600;
    int tripodCounter = 0;
    long temp = 0;
    private final SensorEventListener mGravityTracker = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent sensorEvent) {
            mAngles = sensorEvent.values;
            if (timeCount-System.nanoTime() > 0) {
                for (float f : mAngles) {
                    burstout += Math.abs((f));
                }
                gyroBurst.movements[0].add(mAngles[0]);
                gyroBurst.movements[1].add(mAngles[1]);
                gyroBurst.movements[2].add(mAngles[2]);
                //gyroBurst.timestamps.add(sensorEvent.timestamp);
            } else {
                getShakiness();//For filtering
                if(gyroburst) CompleteGyroBurst();
            }

        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int i) {
        }
    };

    public Gyro(SensorManager sensorManager) {
        mSensorManager = sensorManager;
        mGyroSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
    }

    public void register() {
        gyroBurst = new GyroBurst();
        mSensorManager.registerListener(mGravityTracker, mGyroSensor, delayUs);
    }

    public void unregister() {
        if (mAngles != null)
            mAngles = mAngles.clone();
        mSensorManager.unregisterListener(mGravityTracker, mGyroSensor);
    }

    long[] capturingTimes;
    int capturingNumber = 0;
    private ArrayList<GyroBurst> BurstShakiness;
    public void PrepareGyroBurst(long[] capturingTimes,ArrayList<GyroBurst> burstShakiness) {
        capturingNumber = 0;
        this.capturingTimes = new long[capturingTimes.length];
        System.arraycopy(capturingTimes, 0, this.capturingTimes, 0, capturingTimes.length);
        BurstShakiness = burstShakiness;
    }

    public void CaptureGyroBurst() {
        //Save previous
        if(gyroburst){
            CompleteGyroBurst();
        }
        timeCount = capturingTimes[capturingNumber]+System.nanoTime();
        gyroBurst.movements[0].clear();
        gyroBurst.movements[1].clear();
        gyroBurst.movements[2].clear();
        gyroBurst.timestamps.clear();
        burstout = 0;
        gyroburst = true;
        capturingNumber++;
    }

    public void CompleteGyroBurst() {
        if(gyroburst) {
            gyroburst = false;
            Log.d(TAG, "GyroBurst counter:" + BurstShakiness.size());
            gyroBurst.shakiness = Math.min(burstout * burstout, Float.MAX_VALUE);
            BurstShakiness.add(gyroBurst.clone());
        }
    }

    public int getShakiness() {
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
    public boolean getTripod(){
        return (tripodShakiness < 25) && PhotonCamera.getSettings().selectedMode == CameraMode.NIGHT;
    }
}
