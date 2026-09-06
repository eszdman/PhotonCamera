package com.particlesdevs.photoncamera.processing.parameters;

import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.params.ColorSpaceTransform;
import android.hardware.camera2.params.RggbChannelVector;
import android.util.Rational;

import com.particlesdevs.photoncamera.capture.CaptureController;
import com.particlesdevs.photoncamera.processing.render.Converter;
import java.util.Locale;

/**
 * Practical DNG 1.4-adapted Color Temperature Converter for Android Camera2.
 *
 * Provides bidirectional conversion between Correlated Color Temperature
 * (Kelvin 2000K - 10000K)
 * and Camera2 manual controls (COLOR_CORRECTION_GAINS and
 * COLOR_CORRECTION_TRANSFORM).
 *
 * Engineering & Algorithmic Design:
 * - Continuous CIE 1931 xy Planckian blackbody locus analytical model (Kim et
 * al. 2002).
 * - Universal DNG Mired reciprocal temperature interpolation between factory
 * calibration points.
 * - Symmetrical Planckian CCT solving in CIE 1960 UCS (u, v) space for neutral
 * point reconstruction.
 * - Low-allocation static calibration caching optimized for 60fps live
 * viewfinder execution.
 * - Fail-safe emergency fallbacks for optional or missing metadata keys.
 */
public final class ColorTemperatureConverter {

    public static final int MIN_KELVIN = 2000;
    public static final int MAX_KELVIN = 10000;

    private static final int DEFAULT_TEMP_1 = 2856; // Standard Illuminant A
    private static final int DEFAULT_TEMP_2 = 6504; // CIE D65

    // Fast static cache for active sensor calibration to minimize GC churn in live
    // preview
    private static volatile CameraCharacteristics sCachedCharacteristics = null;
    private static volatile CalibrationData sCachedCalib = null;

    public static final class WhiteBalanceResult {
        public final int kelvin;
        public final double duv;
        public final String tintString;

        public WhiteBalanceResult(int kelvin, double duv, String tintString) {
            this.kelvin = kelvin;
            this.duv = duv;
            this.tintString = tintString;
        }
    }

    private ColorTemperatureConverter() {
    }

    /**
     * Converts target color temperature in Kelvin (2000K - 10000K) into Camera2
     * sensor RGB gains
     * using the active camera's characteristics.
     */
    public static RggbChannelVector kelvinToRggb(int kelvin) {
        return kelvinToRggb(kelvin, CaptureController.mCameraCharacteristics);
    }

    /**
     * Converts target color temperature in Kelvin (2000K - 10000K) into Camera2
     * sensor RGB gains
     * using the provided CameraCharacteristics.
     */
    public static RggbChannelVector kelvinToRggb(int kelvin, CameraCharacteristics characteristics) {
        int clampedKelvin = Math.max(MIN_KELVIN, Math.min(MAX_KELVIN, kelvin));
        double t = clampedKelvin;

        // 1. Calculate CIE 1931 xy chromaticity on continuous Planckian locus
        double[] xy = kelvinToCIExy(t);
        double x = xy[0];
        double y = Math.max(xy[1], 1e-4);
        float[] xyz = new float[] { (float) (x / y), 1.0f, (float) ((1.0 - x - y) / y) };

        CalibrationData calib = getCalibrationData(characteristics);
        if (calib == null) {
            return fallbackGains();
        }

        // 2. Compute DNG Mired-space factor g
        float gClamped = computeDngInterpolationFactor(t, calib.colorTemp1, calib.colorTemp2);

        // 3. Interpolate ColorMatrix: (1 - g) * Color2 + g * Color1
        float[] colorMatrix = new float[9];
        Converter.lerp(calib.color2, calib.color1, gClamped, colorMatrix);

        // 4. Sensor spectral response: [R, G, B]
        float[] cameraSensor = new float[3];
        Converter.map(colorMatrix, xyz, /* out */cameraSensor);

        if (cameraSensor[0] <= 1e-5f || cameraSensor[1] <= 1e-5f || cameraSensor[2] <= 1e-5f) {
            return fallbackGains();
        }

        // 5. White balance gains: R_gain = G / R, B_gain = G / B
        float rGain = cameraSensor[1] / cameraSensor[0];
        float bGain = cameraSensor[1] / cameraSensor[2];

        // Hardware safety clamp to protect HAL from zero/infinite values
        rGain = Math.max(0.1f, Math.min(20.0f, rGain));
        bGain = Math.max(0.1f, Math.min(20.0f, bGain));

        return new RggbChannelVector(rGain, 1.0f, 1.0f, bGain);
    }

    /**
     * Creates a calibrated ColorSpaceTransform (Sensor -> linear sRGB) for
     * CaptureRequest.
     */
    public static ColorSpaceTransform createColorTransform(int kelvin, CameraCharacteristics characteristics) {
        CalibrationData calib = getCalibrationData(characteristics);
        if (calib == null) {
            return fallbackTransform();
        }

        int clampedKelvin = Math.max(MIN_KELVIN, Math.min(MAX_KELVIN, kelvin));
        float gClamped = computeDngInterpolationFactor(clampedKelvin, calib.colorTemp1, calib.colorTemp2);

        return buildColorTransformFromFactor(gClamped, calib);
    }

    /**
     * Reconstructs Correlated Color Temperature (CCT) in Kelvin from
     * SENSOR_NEUTRAL_COLOR_POINT.
     */
    public static int neutralPointToKelvin(Rational[] neutralPoint) {
        return neutralPointToKelvin(neutralPoint, CaptureController.mCameraCharacteristics);
    }

    /**
     * Reconstructs Correlated Color Temperature (CCT) in Kelvin from
     * SENSOR_NEUTRAL_COLOR_POINT.
     */
    public static int neutralPointToKelvin(Rational[] neutralPoint, CameraCharacteristics characteristics) {
        if (neutralPoint == null || neutralPoint.length < 3) {
            return 5500;
        }

        double r = neutralPoint[0].doubleValue();
        double g = neutralPoint[1].doubleValue();
        double b = neutralPoint[2].doubleValue();

        if (Double.isNaN(r) || Double.isNaN(g) || Double.isNaN(b) || r <= 1e-4 || g <= 1e-4 || b <= 1e-4) {
            return 5500;
        }

        CalibrationData calib = getCalibrationData(characteristics);
        if (calib == null) {
            return 5500; // Standard D55 daylight fail-safe
        }

        float[] cameraNeutral = new float[] { (float) r, (float) g, (float) b };
        double[] outEstimatedKelvin = new double[1];
        solveInterpolationFactor(cameraNeutral, calib, outEstimatedKelvin);

        int roundedKelvin = (int) (Math.round(outEstimatedKelvin[0] / 50.0) * 50);
        return Math.max(MIN_KELVIN, Math.min(MAX_KELVIN, roundedKelvin));
    }

    /**
     * Reconstructs CCT (Kelvin) and signed physical Duv (Tint) directly from camera
     * sensor neutral point [R, G, B].
     */
    public static WhiteBalanceResult estimateWhiteBalance(float[] cameraNeutral,
            CameraCharacteristics characteristics) {
        if (cameraNeutral == null || cameraNeutral.length < 3) {
            return new WhiteBalanceResult(5500, 0.0, "");
        }

        float r = cameraNeutral[0];
        float g = cameraNeutral[1];
        float b = cameraNeutral[2];

        if (Float.isNaN(r) || Float.isNaN(g) || Float.isNaN(b) || r <= 1e-5f || g <= 1e-5f || b <= 1e-5f) {
            return new WhiteBalanceResult(5500, 0.0, "");
        }

        CalibrationData calib = getCalibrationData(characteristics);
        if (calib == null) {
            return new WhiteBalanceResult(5500, 0.0, "");
        }

        double[] outEstimatedKelvin = new double[1];
        double[] outMeasuredXy = new double[2];
        solveInterpolationFactorFull(cameraNeutral, calib, outEstimatedKelvin, outMeasuredXy);

        PlanckianProjection proj = projectToPlanckianLocus(outMeasuredXy[0], outMeasuredXy[1]);

        int exactKelvin = (int) Math.round(proj.cct);
        int clampedKelvin = Math.max(MIN_KELVIN, Math.min(MAX_KELVIN, exactKelvin));

        String tintStr = formatDuvToTintString(proj.duv);

        return new WhiteBalanceResult(clampedKelvin, proj.duv, tintStr);
    }

    /**
     * Damped fixed-point DNG neutral point solver with symmetrical Planckian CCT
     * projection.
     */
    private static float solveInterpolationFactor(float[] cameraNeutral, CalibrationData calib, double[] outKelvin) {
        return solveInterpolationFactorFull(cameraNeutral, calib, outKelvin, null);
    }

    /**
     * Damped fixed-point DNG neutral point solver returning converged CCT and
     * measured xy chromaticity.
     */
    private static float solveInterpolationFactorFull(float[] cameraNeutral, CalibrationData calib, double[] outKelvin,
            double[] outMeasuredXy) {
        if (calib.colorTemp1 == calib.colorTemp2) {
            if (outKelvin != null) {
                outKelvin[0] = calib.colorTemp1;
            }
            if (outMeasuredXy != null) {
                double[] xy = kelvinToCIExy(calib.colorTemp1);
                outMeasuredXy[0] = xy[0];
                outMeasuredXy[1] = xy[1];
            }
            return 1.0f;
        }

        double invT1 = 1.0 / calib.colorTemp1;
        double invT2 = 1.0 / calib.colorTemp2;
        double denom = invT1 - invT2;

        double factor = 0.5;
        double oldFactor = factor;
        double tolerance = 0.0001;
        int loopLimit = 25;

        float[] colorMatrix = new float[9];
        float[] invColorMatrix = new float[9];
        float[] neutralGuess = new float[3];
        double[] xy = new double[2];
        double lastCCT = 5500.0;
        double lastX = 0.3320;
        double lastY = 0.1858;

        while (loopLimit-- > 0) {
            Converter.lerp(calib.color2, calib.color1, (float) factor, colorMatrix);
            if (!Converter.invert(colorMatrix, invColorMatrix)) {
                break;
            }

            Converter.map(invColorMatrix, cameraNeutral, /* out */neutralGuess);
            Converter.calculateCIExyCoordinates(neutralGuess[0], neutralGuess[1], neutralGuess[2], /* out */xy);
            lastX = xy[0];
            lastY = xy[1];
            lastCCT = cieXyToKelvin(xy[0], xy[1]);

            double invCT = 1.0 / Math.max(MIN_KELVIN, Math.min(MAX_KELVIN, lastCCT));
            double targetFactor = (invCT - invT2) / denom;
            targetFactor = Math.max(0.0, Math.min(1.0, targetFactor));

            factor = (targetFactor + oldFactor) * 0.5;
            if (Math.abs(oldFactor - factor) < tolerance) {
                break;
            }
            oldFactor = factor;
        }

        if (outKelvin != null) {
            outKelvin[0] = lastCCT;
        }
        if (outMeasuredXy != null) {
            outMeasuredXy[0] = lastX;
            outMeasuredXy[1] = lastY;
        }

        return (float) Math.max(0.0, Math.min(1.0, factor));
    }

    public static final class PlanckianProjection {
        public final double cct;
        public final double duv;

        public PlanckianProjection(double cct, double duv) {
            this.cct = cct;
            this.duv = duv;
        }
    }

    /**
     * Projects a CIE 1931 xy chromaticity onto the Planckian locus in CIE 1960 UCS
     * space
     * and returns the corresponding CCT and signed Duv (CIE 15:2004 / Ohno 2013).
     *
     * CCT is the temperature of the closest point on the Planckian locus.
     * Duv is the signed orthogonal Euclidean distance in CIE 1960 (u,v) space,
     * with sign determined by central-difference tangent normal (positive for
     * Green, negative for Magenta).
     */
    public static PlanckianProjection projectToPlanckianLocus(double targetX, double targetY) {
        double denomMeas = -2.0 * targetX + 12.0 * targetY + 3.0;
        if (Math.abs(denomMeas) < 1e-6) {
            return new PlanckianProjection(5500.0, 0.0);
        }
        double targetU = (4.0 * targetX) / denomMeas;
        double targetV = (6.0 * targetY) / denomMeas;

        final double phi = 0.618033988749895;
        double a = 1667.0;
        double b = 25000.0;

        double t1 = b - phi * (b - a);
        double t2 = a + phi * (b - a);

        double d1 = distanceSqToPlanckianUCS(t1, targetU, targetV);
        double d2 = distanceSqToPlanckianUCS(t2, targetU, targetV);

        for (int i = 0; i < 28; i++) {
            if (d1 < d2) {
                b = t2;
                t2 = t1;
                d2 = d1;
                t1 = b - phi * (b - a);
                d1 = distanceSqToPlanckianUCS(t1, targetU, targetV);
            } else {
                a = t1;
                t1 = t2;
                d1 = d2;
                t2 = a + phi * (b - a);
                d2 = distanceSqToPlanckianUCS(t2, targetU, targetV);
            }
        }

        double optimalT = (a + b) * 0.5;

        // Closest point on the Planckian locus P(T*)
        double[] xy0 = kelvinToCIExy(optimalT);
        double denom0 = -2.0 * xy0[0] + 12.0 * xy0[1] + 3.0;
        double u0 = (4.0 * xy0[0]) / denom0;
        double v0 = (6.0 * xy0[1]) / denom0;

        double du = targetU - u0;
        double dv = targetV - v0;
        double distance = Math.sqrt(du * du + dv * dv);

        // Symmetric central difference tangent vector P(T* + 1K) - P(T* - 1K)
        double[] xyPlus = kelvinToCIExy(optimalT + 1.0);
        double denomPlus = -2.0 * xyPlus[0] + 12.0 * xyPlus[1] + 3.0;
        double uPlus = (4.0 * xyPlus[0]) / denomPlus;
        double vPlus = (6.0 * xyPlus[1]) / denomPlus;

        double[] xyMinus = kelvinToCIExy(optimalT - 1.0);
        double denomMinus = -2.0 * xyMinus[0] + 12.0 * xyMinus[1] + 3.0;
        double uMinus = (4.0 * xyMinus[0]) / denomMinus;
        double vMinus = (6.0 * xyMinus[1]) / denomMinus;

        double tu = uPlus - uMinus;
        double tv = vPlus - vMinus;

        // Strict 2D cross product with tangent to determine exact side of locus (Ohno
        // 2013):
        // Positive for Green (above locus), negative for Magenta (below locus)
        double side = du * tv - dv * tu;
        double signedDuv = (side >= 0.0) ? distance : -distance;

        return new PlanckianProjection(optimalT, signedDuv);
    }

    private static double distanceSqToPlanckianUCS(double T, double targetU, double targetV) {
        double[] xy = kelvinToCIExy(T);
        double denom = -2.0 * xy[0] + 12.0 * xy[1] + 3.0;
        if (Math.abs(denom) < 1e-9)
            return Double.MAX_VALUE;
        double u = (4.0 * xy[0]) / denom;
        double v = (6.0 * xy[1]) / denom;

        double du = u - targetU;
        double dv = v - targetV;
        return du * du + dv * dv;
    }

    /**
     * Formats Duv as an approximate photographic CC filter tint string (1 unit ≈
     * 0.002 Duv).
     */
    public static String formatDuvToTintString(double duv) {
        if (Math.abs(duv) < 0.0005) {
            return ""; // Below human perceptual threshold
        }
        double ccSteps = Math.abs(duv) / 0.002;
        if (duv > 0.0) {
        return String.format(Locale.ROOT, "M+%.1f", Math.abs(ccSteps));
    } else {
        return String.format(Locale.ROOT, "G+%.1f", Math.abs(ccSteps));
    }
    }

    public static double computeDuv(double targetX, double targetY) {
        return projectToPlanckianLocus(targetX, targetY).duv;
    }

    public static double computeDuv(double targetX, double targetY, double cct) {
        return projectToPlanckianLocus(targetX, targetY).duv;
    }

    public static double cieXyToKelvin(double targetX, double targetY) {
        return projectToPlanckianLocus(targetX, targetY).cct;
    }

    private static float computeDngInterpolationFactor(double tempKelvin, int temp1, int temp2) {
        if (temp1 == temp2) {
            return 1.0f;
        }
        double invT = 1.0 / Math.max(MIN_KELVIN, Math.min(MAX_KELVIN, tempKelvin));
        double invT1 = 1.0 / temp1;
        double invT2 = 1.0 / temp2;
        double denom = invT1 - invT2;
        if (Math.abs(denom) < 1e-9) {
            return 1.0f;
        }
        double g = (invT - invT2) / denom;
        return (float) Math.max(0.0, Math.min(1.0, g));
    }

    private static ColorSpaceTransform buildColorTransformFromFactor(float gClamped, CalibrationData calib) {
        float[] cameraToXYZ = new float[9];

        if (calib.hasForwardMatrix) {
            Converter.lerp(calib.forward2, calib.forward1, gClamped, /* out */cameraToXYZ);
        } else {
            float[] interpolatedColor = new float[9];
            Converter.lerp(calib.color2, calib.color1, gClamped, /* out */interpolatedColor);

            if (!Converter.invert(interpolatedColor, /* out */cameraToXYZ)) {
                identityMatrix(cameraToXYZ);
            }
        }

        float[] sensorToSRGB = new float[9];
        Converter.multiply(Converter.sXYZtoSRGB, cameraToXYZ, /* out */sensorToSRGB);

        Rational[] rationals = new Rational[9];
        for (int i = 0; i < 9; i++) {
            rationals[i] = new Rational(Math.round(sensorToSRGB[i] * 1024.0f), 1024);
        }
        return new ColorSpaceTransform(rationals);
    }

    public static double[] kelvinToCIExy(double T) {
        T = Math.max(1667.0, Math.min(25000.0, T));

        final double x;
        if (T <= 4000.0) {
            x = -0.2661239 * (1e9 / (T * T * T))
                    - 0.2343580 * (1e6 / (T * T))
                    + 0.8776956 * (1e3 / T)
                    + 0.179910;
        } else {
            x = -3.0258469 * (1e9 / (T * T * T))
                    + 2.1070379 * (1e6 / (T * T))
                    + 0.2226347 * (1e3 / T)
                    + 0.240390;
        }

        final double y;
        if (T <= 2222.0) {
            y = -1.1063814 * (x * x * x)
                    - 1.34811020 * (x * x)
                    + 2.18555832 * x
                    - 0.20219683;
        } else if (T <= 4000.0) {
            y = -0.9549476 * (x * x * x)
                    - 1.37418593 * (x * x)
                    + 2.09137015 * x
                    - 0.16748867;
        } else {
            y = +3.0817580 * (x * x * x)
                    - 5.87338670 * (x * x)
                    + 3.75112997 * x
                    - 0.37001483;
        }

        return new double[] { x, Math.max(y, 1e-4) };
    }

    private static CalibrationData getCalibrationData(CameraCharacteristics characteristics) {
        if (characteristics == null) {
            return null;
        }
        if (sCachedCharacteristics == characteristics && sCachedCalib != null) {
            return sCachedCalib;
        }
        synchronized (ColorTemperatureConverter.class) {
            if (sCachedCharacteristics == characteristics && sCachedCalib != null) {
                return sCachedCalib;
            }
            CalibrationData data = extractCalibrationData(characteristics);
            sCachedCharacteristics = characteristics;
            sCachedCalib = data;
            return data;
        }
    }

    private static CalibrationData extractCalibrationData(CameraCharacteristics characteristics) {
        Integer ref1Obj = characteristics.get(CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT1);
        int ref1 = (ref1Obj != null) ? ref1Obj : CameraMetadata.SENSOR_REFERENCE_ILLUMINANT1_STANDARD_A;

        Object ref2Obj = characteristics.get(CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT2);
        Integer ref2 = (ref2Obj != null) ? ((Number) ref2Obj).intValue() : null;

        int colorTemp1 = Converter.sStandardIlluminates.get(ref1, DEFAULT_TEMP_1);
        int colorTemp2 = (ref2 != null) ? Converter.sStandardIlluminates.get(ref2, colorTemp1) : colorTemp1;

        ColorSpaceTransform color1Xform = characteristics.get(CameraCharacteristics.SENSOR_COLOR_TRANSFORM1);
        ColorSpaceTransform color2Xform = characteristics.get(CameraCharacteristics.SENSOR_COLOR_TRANSFORM2);
        ColorSpaceTransform forward1Xform = characteristics.get(CameraCharacteristics.SENSOR_FORWARD_MATRIX1);
        ColorSpaceTransform forward2Xform = characteristics.get(CameraCharacteristics.SENSOR_FORWARD_MATRIX2);

        if (color1Xform == null) {
            return null;
        }

        float[] color1 = new float[9];
        float[] color2 = new float[9];
        Converter.convertColorspaceTransform(color1Xform, color1);
        if (color2Xform != null) {
            Converter.convertColorspaceTransform(color2Xform, color2);
        } else {
            System.arraycopy(color1, 0, color2, 0, 9);
        }

        boolean hasForward = (forward1Xform != null);
        float[] forward1 = new float[9];
        float[] forward2 = new float[9];
        if (hasForward) {
            Converter.convertColorspaceTransform(forward1Xform, forward1);
            if (forward2Xform != null) {
                Converter.convertColorspaceTransform(forward2Xform, forward2);
            } else {
                System.arraycopy(forward1, 0, forward2, 0, 9);
            }
        }

        return new CalibrationData(colorTemp1, colorTemp2, color1, color2, forward1, forward2, hasForward);
    }

    /**
     * Fail-safe unit gains fallback when camera metadata is absent.
     */
    private static RggbChannelVector fallbackGains() {
        return new RggbChannelVector(1.0f, 1.0f, 1.0f, 1.0f);
    }

    /**
     * Fail-safe identity transform fallback when camera metadata is absent.
     */
    private static ColorSpaceTransform fallbackTransform() {
        Rational[] identity = new Rational[] {
                new Rational(1, 1), new Rational(0, 1), new Rational(0, 1),
                new Rational(0, 1), new Rational(1, 1), new Rational(0, 1),
                new Rational(0, 1), new Rational(0, 1), new Rational(1, 1)
        };
        return new ColorSpaceTransform(identity);
    }

    private static void identityMatrix(float[] m) {
        m[0] = 1f;
        m[1] = 0f;
        m[2] = 0f;
        m[3] = 0f;
        m[4] = 1f;
        m[5] = 0f;
        m[6] = 0f;
        m[7] = 0f;
        m[8] = 1f;
    }

    private static final class CalibrationData {
        final int colorTemp1;
        final int colorTemp2;
        final float[] color1;
        final float[] color2;
        final float[] forward1;
        final float[] forward2;
        final boolean hasForwardMatrix;

        CalibrationData(int colorTemp1, int colorTemp2,
                float[] color1, float[] color2,
                float[] forward1, float[] forward2,
                boolean hasForwardMatrix) {
            this.colorTemp1 = colorTemp1;
            this.colorTemp2 = colorTemp2;
            this.color1 = color1;
            this.color2 = color2;
            this.forward1 = forward1;
            this.forward2 = forward2;
            this.hasForwardMatrix = hasForwardMatrix;
        }
    }
}