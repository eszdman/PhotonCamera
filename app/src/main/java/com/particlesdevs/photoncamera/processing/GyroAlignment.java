package com.particlesdevs.photoncamera.processing;

import android.graphics.Rect;
import android.hardware.camera2.CameraCharacteristics;
import android.os.Build;
import android.util.Size;

import com.particlesdevs.photoncamera.control.GyroBurst;
import com.particlesdevs.photoncamera.control.GyroMotion;
import com.particlesdevs.photoncamera.processing.render.Parameters;
import com.particlesdevs.photoncamera.util.Log;

/** Reference-to-source gyro maps in full raw pixel coordinates, before CFA packing. */
public final class GyroAlignment {
    private GyroAlignment() {}

    public static final class Calibration {
        final double[] axes;
        final float[] intrinsics;
        final Size pixelArray;
        final Rect active;

        public Calibration(CameraCharacteristics camera) {
            Integer orientation = camera.get(CameraCharacteristics.SENSOR_ORIENTATION);
            Integer facing = camera.get(CameraCharacteristics.LENS_FACING);
            double[] cameraAxes = null;
            if (orientation != null && facing != null
                    && facing != CameraCharacteristics.LENS_FACING_EXTERNAL) {
                cameraAxes = GyroMotion.cameraAxes(orientation,
                        facing == CameraCharacteristics.LENS_FACING_FRONT);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Integer reference = camera.get(CameraCharacteristics.LENS_POSE_REFERENCE);
                float[] pose = camera.get(CameraCharacteristics.LENS_POSE_ROTATION);
                if (reference != null && reference == CameraCharacteristics.LENS_POSE_REFERENCE_GYROSCOPE
                        && pose != null && pose.length == 4) {
                    cameraAxes = GyroMotion.rotation(GyroMotion.normalize(
                            new double[]{pose[3],pose[0],pose[1],pose[2]}));
                }
            }
            axes = cameraAxes;
            float[] k = camera.get(CameraCharacteristics.LENS_INTRINSIC_CALIBRATION);
            intrinsics = k == null ? null : k.clone();
            pixelArray = camera.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE);
            active = camera.get(CameraCharacteristics.SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE);
        }
    }

    /** Null means no trusted prewarp for this pair; the ordinary local matcher still runs. */
    public static float[] between(ImageFrame reference, ImageFrame source, Parameters p) {
        if ((p.rawSize.x & 1) != 0 || (p.rawSize.y & 1) != 0) return null;
        GyroBurst a = reference.frameGyro, b = source.frameGyro;
        Calibration calibration = p.gyroCalibration;
        if (a == null || b == null || !a.orientationValid || !b.orientationValid
                || a.orientationSegment != b.orientationSegment || calibration == null
                || calibration.axes == null || a.frameTimestamp != reference.timestamp
                || b.frameTimestamp != source.timestamp) {
            Log.d("GyroAlignment", "Identity gyro fallback for ts="+source.timestamp+": missing timing/calibration");
            return null;
        }
        if (reference.width != source.width || reference.height != source.height
                || reference.cropOriginX != source.cropOriginX || reference.cropOriginY != source.cropOriginY
                || reference.binned != source.binned) return null;
        double bin = reference.binned ? 2.0 : 1.0;
        double fullW = reference.fullWidth > 0 ? reference.fullWidth : p.rawSize.x*bin;
        double fullH = reference.fullHeight > 0 ? reference.fullHeight : p.rawSize.y*bin;
        double sx = calibration.pixelArray == null ? 1.0/bin
                : fullW/calibration.pixelArray.getWidth()/bin;
        double sy = calibration.pixelArray == null ? 1.0/bin
                : fullH/calibration.pixelArray.getHeight()/bin;
        double fx, fy, cx, cy, skew = 0;
        float[] k = calibration.intrinsics;
        if (k != null && k.length >= 5 && k[0] > 0 && k[1] > 0) {
            fx = k[0]*sx; fy = k[1]*sy; skew = k[4]*sx;
            cx = (k[2]+(calibration.active == null ? 0 : calibration.active.left)+0.5)*sx-0.5;
            cy = (k[3]+(calibration.active == null ? 0 : calibration.active.top)+0.5)*sy-0.5;
        } else {
            if (p.sensorSize == null || p.focalLength <= 0
                    || p.sensorSize.getWidth() <= 0 || p.sensorSize.getHeight() <= 0) return null;
            fx = p.focalLength*fullW/p.sensorSize.getWidth()/bin;
            fy = p.focalLength*fullH/p.sensorSize.getHeight()/bin;
            cx = (fullW/bin-1)*0.5; cy = (fullH/bin-1)*0.5;
        }
        cx -= reference.cropOriginX/bin;
        cy -= reference.cropOriginY/bin;
        float[] h = GyroMotion.homography(a.orientation, b.orientation, calibration.axes,
                fx, fy, cx, cy, skew);
        // OIS shifts move the optical center. Compose both frame shifts, not a
        // scalar correction to the angular velocity: T(source) H T(-reference).
        boolean ois = a.oisValid && b.oisValid;
        if (ois) h = withOis(h, a.oisX*sx, a.oisY*sy, b.oisX*sx, b.oisY*sy);
        for (float v : h) if (!Float.isFinite(v)) return null;
        // Reject a mapping that crosses the projective horizon anywhere in the image.
        for (int y : new int[]{0,p.rawSize.y-1}) for (int x : new int[]{0,p.rawSize.x-1})
            if (h[6]*x+h[7]*y+h[8] <= 0.1f) return null;
        Log.d("GyroAlignment", "Prewarp ts="+source.timestamp+" ref="+reference.timestamp
                +" OIS="+ois+" centerDelta=("+(mapX(h,cx,cy)-cx)+","+(mapY(h,cx,cy)-cy)+")");
        return h;
    }

    public static float[] withOis(float[] h, double ax, double ay, double bx, double by) {
        double[] d = new double[9];
        for (int i = 0; i < 9; i++) d[i] = h[i];
        d = GyroMotion.product(GyroMotion.product(new double[]{1,0,bx,0,1,by,0,0,1}, d),
                new double[]{1,0,-ax,0,1,-ay,0,0,1});
        float[] out = new float[9];
        for (int i = 0; i < 9; i++) out[i] = (float)d[i];
        return out;
    }

    private static double mapX(float[] h, double x, double y) {
        return (h[0]*x+h[1]*y+h[2])/(h[6]*x+h[7]*y+h[8]);
    }
    private static double mapY(float[] h, double x, double y) {
        return (h[3]*x+h[4]*y+h[5])/(h[6]*x+h[7]*y+h[8]);
    }

    public static float[] columnMajor(float[] h) {
        return new float[]{h[0],h[3],h[6],h[1],h[4],h[7],h[2],h[5],h[8]};
    }
}
