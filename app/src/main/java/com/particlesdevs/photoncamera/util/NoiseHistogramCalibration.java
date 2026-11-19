package com.particlesdevs.photoncamera.util;

/** Physical response and histogram resolution of the ESD4D noise measurement. */
public final class NoiseHistogramCalibration {
    private NoiseHistogramCalibration() {}

    /**
     * STD of signed luma - Gaussian(luma), before taking the absolute value,
     * relative to per-frame,
     * per-pixel sensor sigma. Independent Bayer channels contribute a factor
     * 1/2 to the luma STD. Native four-tap binning overlaps taps, so its exact
     * spatial response differs by CFA (including merge00's packing shift).
     */
    public static double residualStdResponse(int frames, boolean binned, int cfa) {
        if (frames < 1 || frames > 9) throw new IllegalArgumentException("Expected 1..9 frames");
        double sum = 0, sumSquares = 0;
        for (int i = 0; i < frames; i++) {
            double weight = i == 0 ? 1.0 : Math.exp(i <= 4 ? -0.5 : -1.0);
            sum += weight;
            sumSquares += weight * weight;
        }
        double spatial = 0.4234514529592069;
        if (binned && cfa >= 0 && cfa <= 3) {
            spatial = cfa == 0 || cfa == 3 ? 0.4114817648384735 : 0.3732768036438227;
        }
        return spatial * Math.sqrt(sumSquares) / sum;
    }

    /**
     * Preserve 64 residual bins but concentrate them around the expected noise
     * in the lower RAW fitting range. Texture can overflow the last bin; the
     * row fitter treats that bin as censored data rather than a small residual.
     * A RAW quantization floor avoids stretching the axis beyond input precision.
     */
    public static double varianceScale(int bins, double response, double s, double o,
                                       double rawStep, double rangeSigmas) {
        if (bins < 2 || !Double.isFinite(response) || response <= 0)
            throw new IllegalArgumentException("Invalid histogram calibration");
        if (!Double.isFinite(rangeSigmas)) rangeSigmas = 4.0;
        double priorVariance = s / 3.0 + o;
        double sensorRange = Double.isFinite(priorVariance) && priorVariance > 0
                ? Math.sqrt(priorVariance) * Math.max(2.0, rangeSigmas) : 0.12;
        double quantizationFloor = Double.isFinite(rawStep) && rawStep > 0 ? 8 * rawStep : 0;
        sensorRange = Math.max(Math.min(sensorRange, 1.0), Math.max(0.00025, quantizationFloor));
        return (bins - 1.0) / (response * sensorRange);
    }
}
