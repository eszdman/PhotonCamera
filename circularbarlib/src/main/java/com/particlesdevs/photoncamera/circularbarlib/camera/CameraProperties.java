package com.particlesdevs.photoncamera.circularbarlib.camera;

import android.hardware.camera2.CameraCharacteristics;
import android.util.Log;
import android.util.Range;

/**
 * Created by vibhorSrv
 */
public class CameraProperties {
    private static final String TAG = CameraProperties.class.getSimpleName();
    private final Float maxFocal;
    private final Float minFocal;
    public Range<Float> focusRange;
    public Range<Integer> isoRange;
    public Range<Long> expRange;
    public Range<Float> evRange;

    public CameraProperties(CameraCharacteristics cameraCharacteristics) {
        this.minFocal = cameraCharacteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);
        this.maxFocal = cameraCharacteristics.get(CameraCharacteristics.LENS_INFO_HYPERFOCAL_DISTANCE);
        this.focusRange = (!(minFocal == null || maxFocal == null || minFocal == 0.0f)) ? new Range<>(Math.min(minFocal, maxFocal), Math.max(minFocal, maxFocal)) : null;
        float evStep = cameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP).floatValue();
        this.isoRange = new Range<>(IsoExpoSelector.getISOLOWExt(cameraCharacteristics), IsoExpoSelector.getISOHIGHExt(cameraCharacteristics));
        this.expRange = new Range<>(IsoExpoSelector.getEXPLOW(cameraCharacteristics), IsoExpoSelector.getEXPHIGH(cameraCharacteristics));
        this.evRange = new Range<>((cameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE).getLower() * evStep),
                (cameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE).getUpper() * evStep));
        logIt();
    }

    private void logIt() {
        Log.d(TAG, "focusRange : " + (focusRange == null ? "Fixed [" + maxFocal + "]" : focusRange.toString()));
        Log.d(TAG, "isoRange : " + isoRange.toString());
        Log.d(TAG, "expRange : " + expRange.toString());
        Log.d(TAG, "evCompRange : " + evRange.toString());
    }

}