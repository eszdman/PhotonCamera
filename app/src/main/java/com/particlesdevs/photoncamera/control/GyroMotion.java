package com.particlesdevs.photoncamera.control;

/** Rotation math shared by the timestamp recorder and raw-image prealignment. */
public final class GyroMotion {
    private GyroMotion() {}

    public static double[] multiply(double[] a, double[] b) {
        return new double[]{a[0]*b[0]-a[1]*b[1]-a[2]*b[2]-a[3]*b[3],
                a[0]*b[1]+a[1]*b[0]+a[2]*b[3]-a[3]*b[2],
                a[0]*b[2]-a[1]*b[3]+a[2]*b[0]+a[3]*b[1],
                a[0]*b[3]+a[1]*b[2]-a[2]*b[1]+a[3]*b[0]};
    }

    public static double[] normalize(double[] q) {
        double norm = Math.sqrt(q[0]*q[0]+q[1]*q[1]+q[2]*q[2]+q[3]*q[3]);
        for (int i = 0; i < 4; i++) q[i] /= norm;
        return q;
    }

    public static double[] delta(double x, double y, double z) {
        double angle = Math.sqrt(x*x+y*y+z*z);
        double scale = angle < 1e-12 ? 0.5 : Math.sin(angle*0.5)/angle;
        return new double[]{Math.cos(angle*0.5), x*scale, y*scale, z*scale};
    }

    /** Row-major matrix, column-vector convention. */
    public static double[] rotation(double[] q) {
        double w=q[0], x=q[1], y=q[2], z=q[3];
        return new double[]{1-2*(y*y+z*z), 2*(x*y-z*w), 2*(x*z+y*w),
                2*(x*y+z*w), 1-2*(x*x+z*z), 2*(y*z-x*w),
                2*(x*z-y*w), 2*(y*z+x*w), 1-2*(x*x+y*y)};
    }

    public static double[] product(double[] a, double[] b) {
        double[] out = new double[9];
        for (int y = 0; y < 3; y++) for (int x = 0; x < 3; x++)
            for (int k = 0; k < 3; k++) out[y*3+x] += a[y*3+k]*b[k*3+x];
        return out;
    }

    public static double[] transpose(double[] a) {
        return new double[]{a[0],a[3],a[6],a[1],a[4],a[7],a[2],a[5],a[8]};
    }

    /** Device axes -> unmirrored raw-camera axes (x right, y down, z forward). */
    public static double[] cameraAxes(int sensorOrientation, boolean front) {
        double angle = Math.toRadians(sensorOrientation);
        double c = Math.cos(angle), s = Math.sin(angle);
        double[] upright = front ? new double[]{-1,0,0, 0,-1,0, 0,0,1}
                : new double[]{1,0,0, 0,-1,0, 0,0,-1};
        // SENSOR_ORIENTATION rotates raw clockwise into upright; undo it here.
        return product(new double[]{c,s,0, -s,c,0, 0,0,1}, upright);
    }

    /** Inverse sampling map: reference pixel p -> source pixel H(p). */
    public static float[] homography(double[] reference, double[] source,
                                      double[] axes, double fx, double fy,
                                      double cx, double cy, double skew) {
        double[] relative = product(transpose(rotation(source)), rotation(reference));
        double[] camera = product(product(axes, relative), transpose(axes));
        double[] k = {fx,skew,cx, 0,fy,cy, 0,0,1};
        double[] inv = {1/fx,-skew/(fx*fy),(skew*cy/fy-cx)/fx,
                0,1/fy,-cy/fy, 0,0,1};
        double[] h = product(product(k, camera), inv);
        float[] out = new float[9];
        for (int i = 0; i < 9; i++) out[i] = (float)(h[i]/h[8]);
        return out;
    }
}
