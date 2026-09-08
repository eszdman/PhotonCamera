package com.particlesdevs.photoncamera.processing.parameters;

/** Converts source variance S*x+O to the common reference exposure domain. */
public final class NightMergeNoise {
    public final float sensorS, sensorO, radianceS, radianceO;

    public NightMergeNoise(double referenceS, double referenceO, double isoRatio, double exposureScale) {
        if (!Double.isFinite(isoRatio) || isoRatio <= 0 || !Double.isFinite(exposureScale) || exposureScale <= 0)
            throw new IllegalArgumentException("Invalid Night exposure normalization");
        double s = Double.isFinite(referenceS) && referenceS > 0 ? referenceS : .0005;
        double o = Double.isFinite(referenceO) && referenceO >= 0 ? referenceO : .000002;
        // Equal-ISO bracketing needs no gain extrapolation. Different ISO is an
        // approximation until per-frame calibrated profiles are retained upstream.
        sensorS = finiteCoefficient(s * isoRatio);
        sensorO = finiteCoefficient(o * isoRatio * isoRatio);
        // y = scale*x => Var(y) = (S*scale)*y + O*scale^2.
        radianceS = finiteCoefficient(sensorS * exposureScale);
        radianceO = finiteCoefficient(sensorO * exposureScale * exposureScale);
    }

    private static float finiteCoefficient(double value) {
        return (float) Math.max(0, Math.min(1e6, value));
    }
}
