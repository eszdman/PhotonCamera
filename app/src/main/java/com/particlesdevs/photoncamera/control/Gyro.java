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
    public static final int delayUs = 500;
    int tripodDetectCount = 600;
    int tripodCounter = 0;
    long temp = 0;
    public static final float NS2S = 1.0f / 1000000000.0f;
    private long prevStamp;
    private int counter = 0;
    private final SensorEventListener mGravityTracker = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent sensorEvent) {
            mAngles = sensorEvent.values;
            float anglex,angley,anglez;
            anglex = mAngles[0]*(sensorEvent.timestamp-prevStamp)*NS2S;
            angley = mAngles[1]*(sensorEvent.timestamp-prevStamp)*NS2S;
            anglez = mAngles[2]*(sensorEvent.timestamp-prevStamp)*NS2S;
            if(integrate){
                x+=anglex;
                y+=angley;
                z+=anglez;
            }
            if (timeCount-System.nanoTime() > 0) {
                burstout +=anglex;
                burstout +=angley;
                burstout +=anglez;
                if(counter < gyroBurst.movementss[0].length) {
                    gyroBurst.movementss[0][counter] = anglex;
                    gyroBurst.movementss[1][counter] = angley;
                    gyroBurst.movementss[2][counter] = anglez;
                }
                counter++;
            } else {
                getShakiness();//For filtering
                if(gyroburst) CompleteGyroBurst();
            }
            prevStamp = sensorEvent.timestamp;
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
        mSensorManager.registerListener(mGravityTracker, mGyroSensor, delayUs);
    }

    public void unregister() {
        if (mAngles != null)
            mAngles = mAngles.clone();
        mSensorManager.unregisterListener(mGravityTracker, mGyroSensor);
    }

    long[] capturingTimes;
    int capturingNumber = 0;
    boolean integrate = false;
    float x,y,z;
    private ArrayList<GyroBurst> BurstShakiness;
    public void PrepareGyroBurst(long[] capturingTimes,ArrayList<GyroBurst> burstShakiness) {
        capturingNumber = 0;
        x = 0.f;
        y = 0.f;
        z = 0.f;
        this.capturingTimes = new long[capturingTimes.length];
        long maxTime = Long.MIN_VALUE;
        for(long time : capturingTimes){
            if(time > maxTime) maxTime = time;
        }
        gyroBurst = new GyroBurst((int) (maxTime/(10*delayUs)));
        System.arraycopy(capturingTimes, 0, this.capturingTimes, 0, capturingTimes.length);

        BurstShakiness = burstShakiness;
    }

    public void CaptureGyroBurst() {
        //Save previous
        if(gyroburst){
            CompleteGyroBurst();
        }
        counter = 0;
        integrate = true;
        timeCount = capturingTimes[capturingNumber]+System.nanoTime();
        //gyroBurst.timestamps.add(System.nanoTime());
        burstout = 0;
        gyroburst = true;
        capturingNumber++;
    }

    public void CompleteGyroBurst() {
        if(gyroburst) {
            gyroburst = false;
            Log.d(TAG, "GyroBurst counter:" + BurstShakiness.size());
            gyroBurst.shakiness = Math.min(burstout * burstout, Float.MAX_VALUE);
            gyroBurst.integrated[0] = -x;
            gyroBurst.integrated[1] = y;
            gyroBurst.integrated[2] = z;
            BurstShakiness.add(gyroBurst.clone());
        }
    }
    public void CompleteSequence() {
        integrate = false;
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
