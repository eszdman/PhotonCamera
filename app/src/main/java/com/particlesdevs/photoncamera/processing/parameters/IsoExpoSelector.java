package com.particlesdevs.photoncamera.processing.parameters;

import android.content.Context;
import android.graphics.Rect;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import com.particlesdevs.photoncamera.util.Log;
import android.util.Range;
import android.util.SizeF;

import com.particlesdevs.photoncamera.api.CameraMode;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.capture.CaptureController;
import com.particlesdevs.photoncamera.settings.PreferenceKeys;
import com.particlesdevs.photoncamera.api.VendorTagUtils;
import com.particlesdevs.photoncamera.settings.SensorConfigPreferenceGenerator;

import java.util.ArrayList;
import java.util.Locale;

public class IsoExpoSelector {
    public static final int baseFrame = 1;
    private static final String TAG = "IsoExpoSelector";
    public static boolean HDR = false;
    public static boolean useTripod = false;
    public static final int patternSize = 3;
    public static ArrayList<ExpoPair> pairs = new ArrayList<>();
    public static ArrayList<ExpoPair> fullpairs = new ArrayList<>();
    public static long lastSelectedExposure = 0;

    // ---- Shutter-Priority / Dynamic Low-Light AE Curve ----
    // Instead of letting stock 3A pick a fast shutter + high ISO, we keep the SAME
    // total exposure the platform metered (exposure_time * iso is still a valid
    // brightness target) and re-split it: push shutter time up first - more real
    // photons land on the sensor per frame, which is a genuine shot-noise SNR win
    // even at identical brightness - and only fall back to ISO once a per-frame
    // time cap is hit. That cap is not one fixed number: it slides between a
    // "start extending here" value and a darker-scene "ceiling" as metered scene
    // darkness increases (see ExpoPair#applyShutterPriorityCurve), so behavior
    // changes smoothly with light level instead of jumping between presets.
    //
    // These are tuned starting points, not measured hardware limits - adjust to taste.
    private static final int MIN_ISO_NORMALIZED = 100; // floor we always try first (ISO-100 basis)
    private static final double CAP_RAMP_STOPS = 4.0;  // stops of extra darkness to slide *_START -> *_END
    private static final double CLEAN_ISO_STEP_FACTOR = 2.0; // hardware analog gain stages are conventionally doublings of the base ISO

    private static final long PHOTO_HANDHELD_CAP_START = ExposureIndex.sec / 30; // 1/30s
    private static final long PHOTO_HANDHELD_CAP_END   = ExposureIndex.sec / 15; // 1/15s

    private static final long MOTION_HANDHELD_CAP_START = ExposureIndex.sec / 250; // 1/250s
    private static final long MOTION_HANDHELD_CAP_END   = ExposureIndex.sec / 125; // 1/125s

    private static final long NIGHT_HANDHELD_CAP_START = ExposureIndex.sec / 8;  // 1/8s
    private static final long NIGHT_HANDHELD_CAP_END   = ExposureIndex.sec / 3;  // 1/3s

    private static final long TRIPOD_CAP_START = ExposureIndex.sec / 4;          // 1/4s
    private static final long TRIPOD_CAP_END   = ExposureIndex.sec * 2;          // 2s

    public static void setExpo(CaptureRequest.Builder builder, int step, CaptureController captureController) {
        Log.v(TAG, "InputParams: " +
                "expo time:" + ExposureIndex.sec2string(ExposureIndex.time2sec(captureController.mPreviewExposureTime)) +
                " iso:" + captureController.mPreviewIso+ " analog:"+getISOAnalog());
        if(step == 0) fullpairs.clear();
        ExpoPair pair = GenerateExpoPair(step,captureController);
        fullpairs.add(pair);
        Log.v(TAG, "IsoSelected:" + pair.iso +
                " ExpoSelected:" + ExposureIndex.sec2string(ExposureIndex.time2sec(pair.exposure)) + " sec step:" + step + " HDR:" + HDR + " total exposure:" + ExposureIndex.time2sec(pair.exposure)*pair.iso);

        builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
        builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, pair.exposure);
        builder.set(CaptureRequest.SENSOR_SENSITIVITY, (int)pair.iso);
        lastSelectedExposure = pair.exposure;
    }
    public static ExpoPair GenerateExpoPair(int step, CaptureController captureController) {
        ExpoPair pair = new ExpoPair(captureController.mPreviewExposureTime, getEXPLOW(), getEXPHIGH(),
                captureController.mPreviewIso, getISOLOW(), getISOHIGH(),getISOAnalog());
        double compensation = Math.pow(2.0,PhotonCamera.getSettings().exposureCompensation);
        pair.normalizeiso100();
        pair.ExpoCompensateLower(1.0/compensation);
        double mpy1;
        if (PhotonCamera.getSettings().selectedMode == CameraMode.NIGHT)
        {
            mpy1 = 7000.0;
            //if(step%3 == 2) mpy = 1.1;
            //mpy = mpy*1.5;
        } else {
             /*else if(PhotonCamera.getSettings().alignAlgorithm == 1){
                if(step%3 == 1) {
                    pair.curlayer = ExpoPair.exposureLayer.High;
                    mpy = 1.0/1.5;
                }
                if(step%3 == 2) {
                    pair.curlayer = ExpoPair.exposureLayer.Normal;
                    mpy = 1.0;
                }
                if(step%3 == 0) {
                    pair.curlayer = ExpoPair.exposureLayer.Low;
                    mpy = 1.5;
                }
            }*/
            mpy1 = 3000.0;
        }
        if(PhotonCamera.getSettings().selectedMode == CameraMode.RAWVIDEO){
            //mpy1 = 0.0;
            pair.denormalizeSystem();
            return pair;
        }

        // Dynamically update tripod state from gyro to avoid stale exposure caching across modes
        if (PhotonCamera.getGyro() != null) {
            useTripod = PhotonCamera.getGyro().getTripod();
        }

        // Shutter-Priority / Dynamic Low-Light AE Curve - PHOTO and NIGHT only.
        // MOTION/RAWVIDEO already returned above, so framerate-sensitive capture is
        // never affected. Tripod overrides mode when active since it removes the
        // handshake concern that motivates the (shorter) handheld ceilings below.
        long capStart, capEnd;
        if (useTripod) {
            capStart = TRIPOD_CAP_START;
            capEnd = TRIPOD_CAP_END;
        } else if (PhotonCamera.getSettings().selectedMode == CameraMode.NIGHT) {
            capStart = NIGHT_HANDHELD_CAP_START;
            capEnd = NIGHT_HANDHELD_CAP_END;
        } else if (PhotonCamera.getSettings().selectedMode == CameraMode.MOTION) {
            capStart = MOTION_HANDHELD_CAP_START;
            capEnd = MOTION_HANDHELD_CAP_END;
        } else {
            capStart = PHOTO_HANDHELD_CAP_START;
            capEnd = PHOTO_HANDHELD_CAP_END;
        }

        double dynamicFactor = getDynamicScalingFactor();
        capStart = (long) (capStart * dynamicFactor);
        capEnd = (long) (capEnd * dynamicFactor);

        if (PhotonCamera.getSettings().selectedMode == CameraMode.PHOTO && !useTripod) {
            capEnd = Math.min(capEnd, ExposureIndex.sec / 15);
            capStart = Math.min(capStart, capEnd);
        }
        if (PhotonCamera.getSettings().selectedMode == CameraMode.MOTION && !useTripod) {
            capEnd = Math.min(capEnd, ExposureIndex.sec / 60);
            capStart = Math.min(capStart, capEnd);
        }

        pair.applyShutterPriorityCurve(capStart, capEnd, CAP_RAMP_STOPS);

        if (pair.normalizedIso() >= 12700.0/mpy1) {
            pair.ReduceIso();
        }
        if (useTripod) {
            // pair.UseIso(Math.max(pair.isoanalog/6.0,101)); // Replaced by applyShutterPriorityCurve
        }

        // Apply dynamic exposure balance shifting and hard limits (shutter/ISO priority)
        if (captureController != null) {
            float mult = captureController.exposureBalanceMultiplier;
            int isoLimit = captureController.exposureBalanceIsoLimit;
            float shutterLimit = captureController.exposureBalanceShutterLimit;
            CameraMode mode = PhotonCamera.getSettings().selectedMode;
            
            boolean hasMultiplier = (mult != 1.0f);
            boolean hasIsoLimit = (isoLimit != -1);
            boolean hasShutterLimit = (shutterLimit > 0.0f || shutterLimit == -2.0f);

            if ((hasMultiplier || hasIsoLimit || hasShutterLimit) && (mode == CameraMode.PHOTO || mode == CameraMode.NIGHT)) {
                pair.applyExposureBalance(mult, isoLimit, shutterLimit);
            }
        }

        double currentManExp = 0.0;
        double currentManISO = 0.0;
        if (captureController != null && captureController.getParamController() != null) {
            currentManExp = captureController.getParamController().getCurrentExposureValue();
            currentManISO = captureController.getParamController().getCurrentISOValue();
        }

        if (currentManExp != 0) {
            pair.exposure = (long) currentManExp;
            pair.isShutterLimited = false;
            pair.isShutterTripodBypassed = false;
            if (!useTripod && captureController != null) {
                long limit = pair.resolveShutterLimit(captureController.exposureBalanceShutterLimit, captureController);
                if (limit < pair.exposurehigh && pair.exposure > limit) {
                    pair.isShutterManualOverLimit = true;
                }
            }
        }

        if (currentManISO != 0) {
            pair.iso = (int) (currentManISO * 100.0 / pair.isolow);
            pair.isIsoLimited = false;
            if (captureController != null && captureController.exposureBalanceIsoLimit != -1) {
                if (pair.iso > pair.resolveIsoLimit(captureController.exposureBalanceIsoLimit)) {
                    pair.isIsoManualOverLimit = true;
                }
            }
        }

        pair.curlayer = ExpoPair.exposureLayer.Normal;
        /*if (step%patternSize == 1 && HDR) {
            pair.ExpoCompensateLower(2.0 / 1.0);
            pair.curlayer = ExpoPair.exposureLayer.Low;
        }*/
        /*if(HDR) {
            pair.ExpoCompensateLowerExpo(2.f);
            pair.ExpoCompensateLower(1.f/2.f);
        }*/
        if (step % patternSize == 0 && HDR) {
            // Set multiplier based on bracketing mode (0=Off, 1=Normal, 2=High)
            int bracketingMode = PreferenceKeys.getBracketingMode();
            pair.layerMpy = 1.f;
            if (bracketingMode == 1) {
                // Normal bracketing (1x, 4x)
                pair.layerMpy = 4.f;
            } else if (bracketingMode == 2) {
                // High bracketing (1x, 8x)
                pair.layerMpy = 8.f;
            }

            if (pair.layerMpy > 1.f) {
                pair.curlayer = ExpoPair.exposureLayer.High;
                if (pair.ExpoCompensateLowerExpo2(1.0 / pair.layerMpy)) {
                    pair.layerMpy = 1.f;
                    pair.curlayer = ExpoPair.exposureLayer.Normal;
                }
            } else {
                pair.curlayer = ExpoPair.exposureLayer.Normal;
            }
        } else if (HDR) {
            pair.layerMpy = 1.f;
            pair.curlayer = ExpoPair.exposureLayer.Normal;
        }

        if (pair.exposure < ExposureIndex.sec / 90 && PhotonCamera.getSettings().eisPhoto) {
            //HDR = true;
        }

        if(step != -1) {
            if (step == 0) pairs.clear();
            if (pairs.size() < patternSize) {
                Log.d(TAG, "Added pair:" + pairs.size());
                pairs.add(pair);
            }
        }
        pair.denormalizeSystem();
        return pair;
    }

    private static <T> T getCameraCharacteristic(CameraCharacteristics.Key<T> key, T defaultValue) {
        CameraCharacteristics characteristics = CaptureController.mCameraCharacteristics;
        if (characteristics == null) return defaultValue;
        T value = characteristics.get(key);
        return value != null ? value : defaultValue;
    }

    public static double getMPY() {
        int isoLow = getISOLOW();
        return (isoLow > 0) ? (100.0 / isoLow) : 1.0;
    }

    private static int mpyIso(int in) {
        return (int) (in * getMPY());
    }

    private static int getISOHIGH() {
        Range<Integer> range = getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE, null);
        return range != null ? range.getUpper() : 3200;
    }

    public static int getISOHIGHExt() {
        return mpyIso(getISOHIGH());
    }

    private static int getISOLOW() {
        Range<Integer> range = getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE, null);
        return (range != null && range.getLower() > 0) ? range.getLower() : 100;
    }

    public static int getISOAnalog() {
        return getCameraCharacteristic(CameraCharacteristics.SENSOR_MAX_ANALOG_SENSITIVITY, 100);
    }

    public static int getISOLOWExt() {
        return mpyIso(getISOLOW());
    }

    public static long getEXPHIGH() {
        Range<Long> range = getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE, null);
        return range != null ? range.getUpper() : ExposureIndex.sec;
    }

    public static long getEXPLOW() {
        Range<Long> range = getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE, null);
        return range != null ? range.getLower() : (ExposureIndex.sec / 1000);
    }

    private static double getDynamicScalingFactor() {
        // 1. Focal Length Scaling
        double focalLength35mm = 24.0;
        CameraCharacteristics characteristics = CaptureController.mCameraCharacteristics;
        if (characteristics != null) {
            float[] focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
            SizeF sensorSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
            if (focalLengths != null && focalLengths.length > 0 && sensorSize != null) {
                // Approximate 35mm equivalent: (36mm / sensorWidth) * focalLength
                focalLength35mm = (36.0f / sensorSize.getWidth()) * focalLengths[0];
            }
        }

        // Digital zoom factor
        float zoom = 1.0f;
        CaptureResult result = CaptureController.mPreviewCaptureResult;
        if (result != null) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                Float zoomRatio = result.get(CaptureResult.CONTROL_ZOOM_RATIO);
                if (zoomRatio != null) zoom = zoomRatio;
            } else {
                Rect crop = result.get(CaptureResult.SCALER_CROP_REGION);
                Rect activeArray = characteristics != null ? characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) : null;
                if (crop != null && activeArray != null && crop.width() > 0) {
                    zoom = (float) activeArray.width() / crop.width();
                }
            }
        }
        double effectiveFocalLength = focalLength35mm * zoom;
        // Reciprocal rule baseline (24mm wide). Longer focal length -> smaller factor -> faster shutter.
        double focalFactor = 24.0 / Math.max(effectiveFocalLength, 10.0);

        // 2. Stability Scaling (only if not on a tripod)
        double stabilityFactor = 1.0;
        if (!useTripod && PhotonCamera.getGyro() != null) {
            int shakiness = PhotonCamera.getGyro().getFilteredShakiness();
            if (shakiness > 0) {
                // Steady hands (shakiness ~25) -> up to 4x factor.
                // Shaky hands (shakiness ~400) -> down to 0.25x factor.
                stabilityFactor = 100.0 / Math.max(shakiness, 25);

                // For Motion mode, we must be conservative to avoid subject blur.
                if (PhotonCamera.getSettings().selectedMode == CameraMode.MOTION) {
                    stabilityFactor = Math.min(stabilityFactor, 1.2);
                }
            }
        }

        double combined = focalFactor * stabilityFactor;
        // Clamp total scaling to [0.2x, 2.5x] range to avoid extreme/impossible shutter speeds.
        double finalFactor = Math.max(0.2, Math.min(combined, 2.5));
        Log.v(TAG, "Dynamic AE Factor: " + String.format(Locale.US, "%.2f", finalFactor) +
                " (Focal=" + String.format(Locale.US, "%.2f", effectiveFocalLength) + "mm, " +
                "Stability=" + (PhotonCamera.getGyro() != null ? PhotonCamera.getGyro().getFilteredShakiness() : "N/A") + ")");
        return finalFactor;
    }

    private static long getAutoSafeShutterNs(CaptureController captureController) {
        double efl = 24.0;
        boolean oisActive = false;

        CameraCharacteristics characteristics = CaptureController.mCameraCharacteristics;
        if (characteristics != null) {
            float fl = 4.75f;
            float[] focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
            if (focalLengths != null && focalLengths.length > 0) {
                fl = focalLengths[0];
            }

            SizeF sensorSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
            if (sensorSize != null && sensorSize.getWidth() > 0) {
                efl = (36.0f / sensorSize.getWidth()) * fl;
            }

            // Explicit and safe OIS capability check via unified VendorTagUtils
            Context context = PhotonCamera.getSettingsManagerStatic() != null
                    ? PhotonCamera.getSettingsManagerStatic().getContext() : null;
            String physicalId = SensorConfigPreferenceGenerator.toPhysicalId(PhotonCamera.getSettings().mCameraID);
            boolean hasHardwareOis = VendorTagUtils.isOisSupported(context, characteristics, physicalId);

            if (hasHardwareOis) {
                oisActive = (captureController == null || captureController.oisMode != 2);
            }
        }

        if (efl <= 0.0) efl = 24.0;

        // Reciprocal rule: 8/EFL if OIS is enabled (+3 stops), 1/EFL without OIS
        double safeSec = (oisActive ? 8.0 : 1.0) / efl;
        return (long) (safeSec * ExposureIndex.sec);
    }


    //==================================Class : ExpoPair==================================//

    public static class ExpoPair {
        public enum exposureLayer{
            Low,
            Normal,
            High
        }
        public exposureLayer curlayer;
        public float layerMpy = 1.f;
        public long exposure;
        public int iso;
        long exposurehigh, exposurelow;
        int isolow, isohigh,isoanalog;

        public boolean isIsoLimited = false;
        public boolean isShutterLimited = false;
        public boolean isShutterTripodBypassed = false;
        public boolean isIsoManualOverLimit = false;
        public boolean isShutterManualOverLimit = false;

        public ExpoPair(ExpoPair pair) {
            copyfrom(pair);
        }

        public ExpoPair(long expo, long expl, long exph, int is, int islow, int ishigh, int analog) {
            exposure = expo;
            iso = is;
            exposurehigh = exph;
            exposurelow = expl;
            isolow = islow;
            isohigh = ishigh;
            isoanalog = analog;
        }
        public double Exposure(){
            return ExposureIndex.time2sec(exposure)*iso;
        }
        public void copyfrom(ExpoPair pair) {
            exposure = pair.exposure;
            exposurelow = pair.exposurelow;
            exposurehigh = pair.exposurehigh;
            iso = pair.iso;
            isolow = pair.isolow;
            isohigh = pair.isohigh;
            isoanalog = pair.isoanalog;
        }

        public double normalizedIsoHigh() {
            return (isolow > 0) ? isohigh * (100.0 / isolow) : isohigh;
        }

        public double normalizedIsoLow() {
            return 100.0;
        }

        public void normalizeiso100() {
            double mpy = (isolow > 0) ? (100.0 / isolow) : 1.0;
            iso = (int) Math.round(iso * mpy);
            isoanalog = (int) Math.round(isoanalog * mpy);
        }

        public void denormalizeSystem() {
            double div = (isolow > 0) ? (100.0 / isolow) : 1.0;
            iso = (int) Math.round(iso / div);
            isoanalog = (int) Math.round(isoanalog / div);
        }
        public float normalizedIso(){
            return (float)iso/isoanalog;
        }
        
        public void normalize() {
            double isoHigh = normalizedIsoHigh();
            if (iso > isoHigh) iso = (int) Math.round(isoHigh);
            if (iso < 100) iso = 100;
            if (exposure > exposurehigh) exposure = exposurehigh;
            if (exposure < exposurelow) exposure = exposurelow;
        }

        public boolean normalizeCheck() {
            double isoHigh = normalizedIsoHigh();
            return iso > isoHigh
                    || iso < 100
                    || exposure > exposurehigh
                    || exposure < exposurelow;
        }

        public void normalizeISO() {
            double isoHigh = normalizedIsoHigh();
            if (iso > isoHigh) {
                double mpy = (double) iso / isoHigh;
                exposure = (long) (exposure * mpy);
                iso = (int) Math.round(isoHigh);
            }
        }

        public void ExpoCompensateLower(double k) {
            int origIso = iso;
            long origExposure = exposure;

            iso = (int) Math.round(iso / k);
            normalizeISO();
            if (normalizeCheck()) {
                iso = origIso;
                exposure = (long) Math.round(origExposure / k);
                if (normalizeCheck()) {
                    exposure = origExposure;
                    layerMpy = 1.f;
                }
            }
        }

        /**
         * Shutter-Priority / Dynamic Low-Light AE curve.
         *
         * Keeps the platform's own metered brightness target (exposure * iso stays
         * constant) but re-splits it between shutter time and ISO gain: ISO is tried
         * at its minimum first, and only raised once the per-frame shutter time would
         * need to exceed a cap. That cap itself is not fixed - it slides from capStart
         * up to capEnd as the metered scene gets darker (rampStops controls how many
         * stops of extra darkness the full slide takes), which is what makes this a
         * *dynamic* low-light strategy rather than a single handheld/night/tripod
         * threshold switch. The per-mode+tripod shutter ceiling is never exceeded,
         * even in extreme edge cases (e.g. large +exposure compensation in near-total
         * darkness) - if max ISO still isn't enough at that point, the frame comes out
         * a little short of the requested brightness rather than surprising the user
         * with a handheld shot far slower than the active mode calls for.
         *
         * When ISO does need to rise above minimum, it's snapped to the nearest "clean"
         * hardware gain point at or above the bare minimum required - see
         * {@link #snapToCleanIso} - rather than left at whatever continuous value the
         * arithmetic produces, since an off-grid ISO is frequently not pure analog gain
         * on the sensor and reads noisier than a clean stage for no benefit. The trade-off
         * is a slightly shorter exposure than the theoretical maximum (a clean rung is
         * never below the continuous optimum, only ever a bit above it), in exchange for
         * a real, hardware-backed SNR win at whatever ISO we actually land on.
         *
         * @param capStart  per-frame shutter time where we start extending past minimum ISO
         * @param capEnd    per-frame shutter time ceiling in the darkest scenes
         * @param rampStops how many stops darker than capStart's "just enough" point it
         *                  takes to reach capEnd
         */
        public void applyShutterPriorityCurve(long capStart, long capEnd, double rampStops) {
            double totalExposureEnergy = (double) exposure * iso; // proxy for scene darkness: bigger = darker

            // Energy capStart can already deliver at minimum ISO - past this point,
            // minimum ISO alone is no longer enough to hit the metered brightness.
            double energyAtCapStart = (double) capStart * MIN_ISO_NORMALIZED;

            long dynamicCap;
            if (totalExposureEnergy <= energyAtCapStart) {
                dynamicCap = capStart; // plenty of light, no need to extend the shutter at all
            } else {
                double stopsPastStart = log2(totalExposureEnergy / energyAtCapStart);
                double t = Math.max(0.0, Math.min(1.0, stopsPastStart / rampStops));
                dynamicCap = (long) (capStart * Math.pow((double) capEnd / capStart, t)); // geometric slide
            }
            long effectiveCap = Math.min(dynamicCap, exposurehigh); // never ask for more than the sensor allows either

            // Smallest (continuous) ISO that still hits the metered brightness within effectiveCap.
            double isoMinToFit = totalExposureEnergy / effectiveCap;

            if (isoMinToFit <= MIN_ISO_NORMALIZED) {
                iso = MIN_ISO_NORMALIZED; // plenty of light, minimum ISO alone already fits under the cap
            } else {
                long cleanIso = snapToCleanIso(isoMinToFit, true);
                double shutterAtCleanIso = totalExposureEnergy / cleanIso;

                // If snapping up to a "clean" hardware gain stage would drop our shutter time
                // by more than 5% below the cap, prioritize the photon collection (shutter duration)
                // and use the exact ISO required instead.
                if (shutterAtCleanIso < effectiveCap * 0.95) {
                    iso = (int) Math.ceil(isoMinToFit);
                } else {
                    iso = (int) cleanIso;
                }
            }
            exposure = (long) (totalExposureEnergy / iso);

            // Safety clamp, done by hand in normalized-ISO-100 units. Deliberately NOT
            // calling normalize()/normalizeISO() here: those assign the raw isohigh bound
            // straight into this normalized field, which only happens to be unit-correct
            // when the sensor's isolow is exactly 100. Bounding exposure by effectiveCap
            // (not just exposurehigh) keeps the "never exceed the policy cap" guarantee
            // even when snapToCleanIso has to fall back to the sensor's true ISO ceiling.
            if (exposure > effectiveCap) exposure = effectiveCap;
            if (exposure < exposurelow) exposure = exposurelow;
            double isoHighNormalized = normalizedIsoHigh();
            if (iso > isoHighNormalized) iso = (int) Math.round(isoHighNormalized);
            if (iso < MIN_ISO_NORMALIZED) iso = MIN_ISO_NORMALIZED;

            Log.v(TAG, "ShutterPriorityCurve: energy=" + (long) totalExposureEnergy +
                    " dynamicCap=" + ExposureIndex.sec2string(ExposureIndex.time2sec(dynamicCap)) +
                    " -> exposure=" + ExposureIndex.sec2string(ExposureIndex.time2sec(exposure)) +
                    " iso=" + iso);
        }        

        /**
         * Resolves the effective normalized ISO ceiling based on the configured limit flag/number.
         */
        public double resolveIsoLimit(int isoLimit) {
            if (isoLimit == -4) return Math.max(100.0, (double) isoanalog / 4.0);
            if (isoLimit == -3) return Math.max(100.0, (double) isoanalog / 2.0);
            if (isoLimit == -2) return (double) isoanalog;
            if (isoLimit == -1) return normalizedIsoHigh();
            return Math.min((double) isohigh, (double) isoLimit) * (100.0 / isolow);
        }

        /**
         * Resolves the effective shutter duration limit in nanoseconds.
         */
        public long resolveShutterLimit(float shutterLimitSec, CaptureController cc) {
            if (shutterLimitSec == -2.0f) return getAutoSafeShutterNs(cc);
            if (shutterLimitSec > 0.0f) return (long) (shutterLimitSec * ExposureIndex.sec);
            return exposurehigh;
        }

        /**
         * Resolves the optimal ISO and Shutter pair when exposure exceeds maxExposure:
         * 1. Snap UP: if within 10% shutter loss, raises to clean analog ISO and shortens shutter (100% energy).
         * 2. Snap DOWN: if within 5% brightness drop, lowers to clean analog ISO at maxExposure.
         * 3. Fallback: unquantized continuous sensor ISO (100% energy preservation).
         */
        private void applyHeadroomAwareQuantization(double targetEnergy, long minExposure, long maxExposure, double maxIso, int isoLimit) {
            // 1. Calculate required continuous ISO strictly relative to maxExposure ceiling
            double continuousIso = targetEnergy / (double) maxExposure;
            long isoDown = snapToCleanIso(continuousIso, false);
            long isoUp = snapToCleanIso(continuousIso, true);

            // 2. Try snapping UP to higher clean ISO (shortens shutter, 100% energy)
            double shutterAtIsoUp = targetEnergy / (double) isoUp;
            if ((double) isoUp <= maxIso && shutterAtIsoUp >= (double) minExposure && (shutterAtIsoUp >= (double) maxExposure * 0.90)) {
                iso = (int) isoUp;
                exposure = (long) Math.round(shutterAtIsoUp);
                return;
            }

            // 3. Try snapping DOWN to lower clean ISO (holds maxExposure, loses < 5% brightness)
            if (isoDown <= continuousIso && (double) isoDown >= continuousIso * 0.95 && isoDown >= 100 && (double) isoDown <= maxIso) {
                iso = (int) isoDown;
                exposure = maxExposure;
                return;
            }

            // 4. Fallback: exact unquantized sensor gain (100% energy preservation)
            double boundedIso = Math.max(100.0, Math.min(maxIso, continuousIso));
            iso = (int) Math.ceil(boundedIso);
            exposure = (long) Math.round(Math.max((double) minExposure, Math.min((double) maxExposure, targetEnergy / (double) iso)));

            // Set ISO limit flag if continuous required ISO exceeded user ceiling
            if (isoLimit != -1 && continuousIso > maxIso) {
                isIsoLimited = true;
            }
        }

        /**
         * Shifts the exposure balance by the given multiplier k (shutter/ISO trade-off).
         * A multiplier > 1.0 reduces shutter duration and increases ISO (freezing motion).
         * A multiplier < 1.0 increases shutter duration and reduces ISO (cleaner image).
         *
         * Uses a Dual-Axis Backtracking Clamping algorithm with Tripod Awareness.
         *
         * @param k               the multiplier to adjust balance
         * @param isoLimit        the configured ISO limit (-1 = Sensor Max, -2 = Max Analog, -3 = Max Analog / 2, -4 = Max Analog / 4, >0 = Custom limit)
         * @param shutterLimitSec the configured shutter duration limit in seconds (-1.0f = Sensor Max, >0 = Custom limit in seconds)
         */
        public void applyExposureBalance(double k, int isoLimit, float shutterLimitSec) {
            isIsoLimited = false;
            isShutterLimited = false;
            isShutterTripodBypassed = false;
            isIsoManualOverLimit = false;
            isShutterManualOverLimit = false;

            // 1. Save target exposure energy
            double targetEnergy = (double) exposure * iso;

            // 2. Apply theoretical shift (bypassed on tripod to prioritize maximum light gathering)
            double effectiveK = useTripod ? 1.0 : k;
            exposure = (long) (exposure / effectiveK);
            iso = (int) (iso * effectiveK);

            // 3. Resolve bounds using helper methods
            double isoHighNormalized = resolveIsoLimit(isoLimit);
            long userShutterNs = resolveShutterLimit(shutterLimitSec, PhotonCamera.getCaptureController());
            long effectiveExposureHigh = useTripod ? exposurehigh : Math.min(exposurehigh, userShutterNs);

            // 4. ISO limits check with backtracking to exposure
            if (iso > isoHighNormalized) {
                iso = (int) Math.round(isoHighNormalized);
                if (isoLimit != -1) isIsoLimited = true;
                exposure = (long) (targetEnergy / iso);
            } else if (iso < 100) {
                iso = 100;
                exposure = (long) (targetEnergy / iso);
            }

            // 5. Exposure limits check with clean ISO snapping down
            if (exposure > effectiveExposureHigh) {
                applyHeadroomAwareQuantization(targetEnergy, exposurelow, effectiveExposureHigh, isoHighNormalized, isoLimit);
                if (exposure >= effectiveExposureHigh && (shutterLimitSec > 0.0f || shutterLimitSec == -2.0f) && !useTripod) {
                    isShutterLimited = true;
                }
            } else if (exposure < exposurelow) {
                exposure = exposurelow;
                double continuousIso = targetEnergy / (double) exposure;
                iso = (int) Math.ceil(Math.max(100.0, Math.min(isoHighNormalized, continuousIso)));
            }

            // 6. Final safety clamps
            if (iso > isoHighNormalized) {
                iso = (int) Math.round(isoHighNormalized);
                if (isoLimit != -1) isIsoLimited = true;
            }
            if (iso < 100) iso = 100;

            if (exposure > effectiveExposureHigh) {
                exposure = effectiveExposureHigh;
                if ((shutterLimitSec > 0.0f || shutterLimitSec == -2.0f) && !useTripod) isShutterLimited = true;
            }
            if (exposure < exposurelow) exposure = exposurelow;

            // 7. Check if tripod mode bypassed the user's handheld shutter limit
            if (useTripod && userShutterNs < exposurehigh && exposure > userShutterNs) {
                isShutterTripodBypassed = true;
                isShutterLimited = false;
            }
        }

        /**
         * Snaps to the nearest ISO the sensor can realize as a clean hardware gain step (normalized ISO-100 basis):
         * the base ISO doubled some number of times, plus the sensor's own reported max-pure-analog gain point
         * ({@code isoanalog} / SENSOR_MAX_ANALOG_SENSITIVITY) inserted as an extra rung even when it doesn't fall
         * on a doubling, since that boundary is real hardware data rather than an assumption about gain-stage spacing.
         * Falls back to the sensor's true ISO ceiling if nothing smaller fits.
         *
         * @param targetIso target normalized ISO value to snap
         * @param snapUp    if true, snaps UP (ceiling, >= targetIso) to guarantee safe exposure duration in auto curves;
         *                  if false, snaps DOWN (floor, <= targetIso) to guarantee pure analog gain without HAL digital
         *                  scaling noise when bounded by shutter limits.
         * @return snapped clean normalized ISO value
         */
        private long snapToCleanIso(double targetIso, boolean snapUp) {
            double isoHighNormalized = normalizedIsoHigh();
            double isoAnalogNormalized = isoanalog;

            double[] ladder = new double[16];
            int n = 0;
            for (double rung = MIN_ISO_NORMALIZED; rung <= isoHighNormalized && n < 14; rung *= CLEAN_ISO_STEP_FACTOR) {
                ladder[n++] = rung;
            }
            if (isoAnalogNormalized > MIN_ISO_NORMALIZED && isoAnalogNormalized < isoHighNormalized && !containsRung(ladder, n, isoAnalogNormalized)) {
                ladder[n++] = isoAnalogNormalized;
            }
            if (!containsRung(ladder, n, isoHighNormalized)) {
                ladder[n++] = isoHighNormalized; // true sensor ceiling, always available as a last resort
            }
            java.util.Arrays.sort(ladder, 0, n);

            if (snapUp) {
                for (int i = 0; i < n; i++) {
                    if (ladder[i] >= targetIso) return Math.round(ladder[i]);
                }
                return Math.round(isoHighNormalized);
            } else {
                long result = Math.round(ladder[0]);
                for (int i = 0; i < n; i++) {
                    if (ladder[i] <= targetIso) {
                        result = Math.round(ladder[i]);
                    } else {
                        break;
                    }
                }
                return result;
            }
        }

        private boolean containsRung(double[] ladder, int count, double value) {
            for (int i = 0; i < count; i++) {
                if (Math.abs(ladder[i] - value) < 0.01) return true;
            }
            return false;
        }

        private static double log2(double x) {
            return Math.log(x) / Math.log(2.0);
        }

        public void ExpoCompensateLowerExpo(double k) {
            int origIso = iso;
            long origExposure = exposure;

            iso = (int) Math.round(origIso / k);
            if (normalizeCheck()) {
                iso = origIso;
                exposure = (long) Math.round(origExposure / k);
                if (normalizeCheck()) {
                    double sqrtK = Math.sqrt(k);
                    exposure = (long) Math.round(origExposure / sqrtK);
                    iso = (int) Math.round(origIso / sqrtK);
                    if (normalizeCheck()) {
                        exposure = origExposure;
                        iso = origIso;
                    }
                }
            }
        }

        public boolean ExpoCompensateLowerExpo2(double k) {
            long origExposure = exposure;
            int origIso = iso;

            exposure = (long) Math.round(origExposure / k);
            if (normalizeCheck()) {
                exposure = origExposure;
                iso = (int) Math.round(origIso / k);
                if (normalizeCheck()) {
                    double sqrtK = Math.sqrt(k);
                    iso = (int) Math.round(origIso / sqrtK);
                    exposure = (long) Math.round(origExposure / sqrtK);
                    if (normalizeCheck()) {
                        iso = origIso;
                        exposure = origExposure;
                    }
                }
            }
            return normalizeCheck();
        }

        public void MinIso() {
            UseIso(100);
        }

        public void UseIso(double isoUsed) {
            if (isoUsed <= 0) return;
            int origIso = iso;
            long origExposure = exposure;

            double k = (double) iso / isoUsed;
            ReduceIso(k);
            if (normalizeCheck()) {
                iso = (int) Math.round((double) origIso * origExposure / exposurehigh);
                exposure = exposurehigh;
                if (normalizeCheck()) {
                    double isoHigh = normalizedIsoHigh();
                    if (iso > isoHigh) iso = (int) Math.round(isoHigh);
                    if (iso < 100) iso = 100;
                    if (exposure > exposurehigh) exposure = exposurehigh;
                    if (exposure < exposurelow) exposure = exposurelow;
                }
            }
        }

        public void ReduceIso() {
            int origIso = iso;
            long origExposure = exposure;
            ReduceIso(2.0);
            if (normalizeCheck()) {
                iso = origIso;
                exposure = origExposure;
            }
        }

        public void ReduceIso(double k) {
            iso = (int) Math.round(iso / k);
            exposure = (long) Math.round(exposure * k);
        }

        public void ReduceExpo() {
            int origIso = iso;
            long origExposure = exposure;
            ReduceExpo(2.0);
            if (normalizeCheck()) {
                iso = origIso;
                exposure = origExposure;
            }
        }

        public void ReduceExpo(double k) {
            Log.d(TAG, "ExpoReducing iso:" + iso + " expo:" + ExposureIndex.sec2string(ExposureIndex.time2sec(exposure)));
            iso = (int) Math.round(iso * k);
            exposure = (long) Math.round(exposure / k);
            Log.d(TAG, "ExpoReducing done iso:" + iso + " expo:" + ExposureIndex.sec2string(ExposureIndex.time2sec(exposure)));
        }

        public void FixedExpo(double expo) {
            long expol = ExposureIndex.sec2time(expo);
            if (expol <= 0) return;
            int origIso = iso;
            long origExposure = exposure;
            double k = (double) exposure / expol;
            ReduceExpo(k);
            Log.d(TAG, "ExpoFixating iso:" + iso + " expo:" + ExposureIndex.sec2string(ExposureIndex.time2sec(exposure)));
            if (normalizeCheck()) {
                iso = origIso;
                exposure = origExposure;
            }
        }

        public String ExposureString() {
            return ExposureIndex.sec2string(ExposureIndex.time2sec(exposure));
        }
    }
}
