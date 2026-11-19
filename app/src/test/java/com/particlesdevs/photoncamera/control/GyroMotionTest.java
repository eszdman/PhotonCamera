package com.particlesdevs.photoncamera.control;

import com.particlesdevs.photoncamera.processing.GyroAlignment;
import org.junit.Test;
import static org.junit.Assert.*;

public class GyroMotionTest {
    private final double[] identity = {1,0,0,0};
    private final double[] axes = {1,0,0,0,1,0,0,0,1};

    private double[] map(float[] h, double x, double y) {
        double z = h[6]*x+h[7]*y+h[8];
        return new double[]{(h[0]*x+h[1]*y+h[2])/z,(h[3]*x+h[4]*y+h[5])/z};
    }

    @Test public void stationaryCameraIsIdentityEvenWithSkewAndOffCenterCrop() {
        double[] pose = GyroMotion.delta(0.1,-0.2,0.3);
        float[] h = GyroMotion.homography(pose,pose,axes,1000,1200,345,123,4);
        assertArrayEquals(new float[]{1,0,0,0,1,0,0,0,1},h,1e-5f);
    }

    @Test public void positiveCameraYawSamplesLeftOfReferenceCenter() {
        float[] h = GyroMotion.homography(identity,GyroMotion.delta(0,0.01,0),axes,1000,1000,500,400,0);
        assertArrayEquals(new double[]{500-1000*Math.tan(0.01),400},map(h,500,400),1e-4);
    }

    @Test public void referenceSwapInvertsMapping() {
        double[] a = GyroMotion.delta(0.03,0.02,-0.01), b = GyroMotion.delta(-0.02,0.01,0.04);
        float[] ab = GyroMotion.homography(a,b,axes,1000,1100,500,400,0);
        float[] ba = GyroMotion.homography(b,a,axes,1000,1100,500,400,0);
        double[] p = map(ab,200,300);
        assertArrayEquals(new double[]{200,300},map(ba,p[0],p[1]),1e-4);
    }

    @Test public void raw90BackCameraUsesDeviceAxesNotDisplayRotation() {
        double[] c = GyroMotion.cameraAxes(90,false);
        assertArrayEquals(new double[]{0,-1,0,-1,0,0,0,0,-1},c,1e-12);
        assertArrayEquals(axes,GyroMotion.product(c,GyroMotion.transpose(c)),1e-12);
        double[] front = GyroMotion.cameraAxes(270,true);
        assertArrayEquals(axes,GyroMotion.product(front,GyroMotion.transpose(front)),1e-12);
    }

    @Test public void residualMustBeAppliedBeforeProjectiveWarp() {
        float[] h = GyroMotion.homography(identity,GyroMotion.delta(0.02,0.01,0.1),axes,1000,1000,500,400,0);
        double[] composed = map(h,202.5,298.75);
        double[] firstWarp = map(h,200,300);
        assertTrue(Math.abs(composed[0]-(firstWarp[0]+2.5))>0.05);
    }

    @Test public void oisMovesBothPrincipalPoints() {
        float[] h = GyroMotion.homography(identity,GyroMotion.delta(0,0,0.1),axes,1000,1000,500,400,0);
        float[] corrected = GyroAlignment.withOis(h,3,-2,5,7);
        double[] expected = map(h,200-3,300+2);
        expected[0] += 5; expected[1] += 7;
        assertArrayEquals(expected,map(corrected,200,300),1e-4);
    }
}
