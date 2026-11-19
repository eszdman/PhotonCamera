package com.particlesdevs.photoncamera.control;

import androidx.annotation.NonNull;

public class GyroBurst {
    public float shakiness;
    public int samples;
    public float[][] movementss;
    public long[] timestampss;
    public float[] integrated;
    /** Camera SENSOR_TIMESTAMP: start of first-row exposure, in nanoseconds. */
    public long frameTimestamp;
    public long exposureTimeNs;
    public long rollingShutterSkewNs;
    public long orientationTimestamp;
    /** Device-to-world unit quaternion (w,x,y,z), at the exposure midpoint. */
    public double[] orientation = {1, 0, 0, 0};
    public boolean orientationValid;
    /** Different segments must never be compared (sensor stopped or samples were lost). */
    public long orientationSegment;
    /** OIS optical-center displacement at orientationTimestamp, in sensor pixels. */
    public boolean oisValid;
    public float oisX, oisY;
    private final int maxSamples;
    public GyroBurst(int maxSamples){
        this.maxSamples = maxSamples;
        movementss = new float[3][maxSamples];
        timestampss = new long[maxSamples];
        integrated = new float[3];
        samples = 0;
    }

    @NonNull
    @Override
    public GyroBurst clone() {
        GyroBurst out = new GyroBurst(maxSamples);
        for (int axis = 0; axis < 3; axis++) out.movementss[axis] = movementss[axis].clone();
        out.timestampss = timestampss.clone();
        out.integrated = integrated.clone();
        out.shakiness = shakiness;
        out.samples = samples;
        out.frameTimestamp = frameTimestamp;
        out.exposureTimeNs = exposureTimeNs;
        out.rollingShutterSkewNs = rollingShutterSkewNs;
        out.orientationTimestamp = orientationTimestamp;
        out.orientation = orientation.clone();
        out.orientationValid = orientationValid;
        out.orientationSegment = orientationSegment;
        out.oisValid = oisValid;
        out.oisX = oisX;
        out.oisY = oisY;
        return out;
    }
}
