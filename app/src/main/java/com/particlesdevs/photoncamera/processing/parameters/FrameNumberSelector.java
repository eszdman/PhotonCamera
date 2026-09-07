package com.particlesdevs.photoncamera.processing.parameters;

import com.particlesdevs.photoncamera.api.CameraMode;
import com.particlesdevs.photoncamera.app.PhotonCamera;


public class FrameNumberSelector {
    public static int frameCount;
    public static int throwCount;
    public static int getFrames() {
        if (PhotonCamera.getSettings().selectedMode == CameraMode.NIGHT) {
            int maximum = Math.max(1, PhotonCamera.getSettings().frameCount);
            // Preview the same slots used by capture, including final shutter
            // caps and user settings, without appending to pairs/fullpairs.
            IsoExpoSelector.ExpoPair normal = IsoExpoSelector.previewExpoPair(1,
                    PhotonCamera.getCaptureController());
            int shake = PhotonCamera.getGyro() != null
                    ? PhotonCamera.getGyro().getFilteredShakiness() : -1;
            boolean tripod = PhotonCamera.getGyro() != null && PhotonCamera.getGyro().getTripod();
            double gain = normal.iso * IsoExpoSelector.getMPY() / 100.0;
            NightBracketing policy = new NightBracketing(IsoExpoSelector.HDR,
                    com.particlesdevs.photoncamera.settings.PreferenceKeys.getBracketingMode(),
                    gain, shake, tripod);
            java.util.ArrayList<IsoExpoSelector.ExpoPair> plan = new java.util.ArrayList<>();
            double[] seconds = new double[maximum];
            for (int i = 0; i < maximum; i++) {
                IsoExpoSelector.ExpoPair pair = IsoExpoSelector.previewNightPair(
                        PhotonCamera.getCaptureController(), policy.isLong(i) ? policy.ratio : 1);
                plan.add(pair);
                seconds[i] = ExposureIndex.time2sec(pair.exposure);
            }
            frameCount = NightFrameCount.select(gain, shake, tripod, seconds);
            if (PhotonCamera.getSettings().DebugData) frameCount = maximum;
            IsoExpoSelector.setNightCapturePlan(new java.util.ArrayList<>(plan.subList(0, frameCount)));
            throwCount = 0; // Night merging rejects incompatible regions locally.
            com.particlesdevs.photoncamera.util.Log.d("FrameNumberSelector",
                    "Night frames=" + frameCount + "/" + maximum + " iso=" + normal.iso
                            + " shake=" + shake + " tripod=" + tripod
                            + " normalNs=" + normal.exposure + " requestedBracketRatio=" + policy.ratio);
            return frameCount;
        }
        double lightcycle = (Math.exp(1.3595 + 1.0020 * PhotonCamera.getCaptureController().mPreviewIso/IsoExpoSelector.getISOAnalog())) / 9;
        double target = (Math.exp(1.3595 + 1.0020 * PhotonCamera.getCaptureController().mPreviewIso/IsoExpoSelector.getISOAnalog())) / 14;
        int frames = PhotonCamera.getSettings().frameCount;
        lightcycle *= frames;
        target *= frames;
        frameCount = Math.min(Math.max((int) lightcycle, Math.min(8,frames)), frames);
        throwCount = Math.min(Math.max((int) target, Math.min(8,frames)), frames);
        if (PhotonCamera.getSettings().selectedMode == CameraMode.UNLIMITED || PhotonCamera.getSettings().selectedMode == CameraMode.RAWVIDEO) frameCount = -1;
        if(PhotonCamera.getSettings().DebugData) frameCount = frames;
        throwCount = (frameCount-throwCount);
        return frameCount;
    }
}
