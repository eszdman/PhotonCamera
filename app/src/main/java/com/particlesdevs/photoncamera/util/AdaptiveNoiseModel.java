package com.particlesdevs.photoncamera.util;

/** CPU-side validation and fitting for the adaptive noise histogram. */
public final class AdaptiveNoiseModel {
    private static final int MIN_SAMPLES = 128;
    private static final int MIN_BRIGHTNESS_ROWS = 3;
    private static final double MIN_BRIGHTNESS_SPAN = 0.04;
    private static final double MIN_BRIGHTNESS_VARIANCE = 0.005 * 0.005;

    private AdaptiveNoiseModel() {}

    /** Choose a homogeneous sensor-domain group for the calibrated blend. */
    public static int[] selectFrames(float[] layers, int[] isos, int modelIso, int maximum) {
        if (layers == null || isos == null || layers.length == 0 || layers.length != isos.length
                || maximum < 1 || modelIso <= 0 || isos[0] != modelIso
                || !Float.isFinite(layers[0]) || Math.abs(layers[0] - 1.0f) > 1e-4f) return new int[0];
        int[] eligible = new int[layers.length];
        int count = 0;
        for (int i = 0; i < layers.length; i++) {
            if (isos[i] == modelIso && Float.isFinite(layers[i])
                    && Math.abs(layers[i] - layers[0]) <= 1e-4f) eligible[count++] = i;
        }
        int size = Math.min(count, maximum);
        int[] selected = new int[size];
        for (int i = 0; i < size; i++) {
            selected[i] = eligible[size == 1 ? 0 : (int) Math.round((double) i * (count - 1) / (size - 1))];
        }
        return selected;
    }

    public static final class Model {
        public final double s;
        public final double o;
        public final int points;
        /** Populated brightness rows retained by the spatial row estimator. */
        public final int rows;
        /** Populated rows rejected as elevated or mostly above the residual axis. */
        public final int textureRows;

        public Model(double s, double o, int points) {
            this(s, o, points, 0, 0);
        }

        private Model(double s, double o, int points, int rows, int textureRows) {
            this.s = s;
            this.o = o;
            this.points = points;
            this.rows = rows;
            this.textureRows = textureRows;
        }
    }

    private static final double HALF_NORMAL_MEDIAN = 0.6744897501960817;
    private static final int MIN_ROW_SAMPLES = 24;
    private static final int MIN_SPATIAL_ROWS = 5;

    private static final class NoiseRow {
        final int index;
        final double brightness;
        final double variance;
        final int samples;
        final int occupied;
        boolean supported;

        NoiseRow(int index, double brightness, double variance, int samples, int occupied) {
            this.index = index;
            this.brightness = brightness;
            this.variance = variance;
            this.samples = samples;
            this.occupied = occupied;
        }
    }

    /**
     * Estimate the sensor-domain noise curve from supported brightness rows.
     * The histogram contains absolute Gaussian spatial residuals; response is
     * their physical standard-deviation response to unit per-frame noise,
     * not the legacy gate-dependent VAR_STAT calibration.
     *
     * A row median rejects large residual tails. Nearby rows must support a
     * plausible nonnegative noise curve, so an isolated low fit cannot become
     * the noise floor. A lower-quantile curve then identifies elevated textured
     * brightness regions before refitting all consistent rows without a tail
     * truncation. The caller controls the brightness domain (including sqrt(3)).
     */
    public static Model fitSpatialHistogram(int[] histogram, int brightnessBins, int varianceBins,
                                            double brightnessScale, double varianceScale,
                                            double residualStdResponse) {
        return fitSpatialHistogram(histogram, brightnessBins, varianceBins, brightnessScale,
                varianceScale, residualStdResponse, 1.8);
    }

    public static Model fitSpatialHistogram(int[] histogram, int brightnessBins, int varianceBins,
                                            double brightnessScale, double varianceScale,
                                            double residualStdResponse, double textureGate) {
        if (histogram == null || brightnessBins < 2 || varianceBins < 3
                || histogram.length != brightnessBins * varianceBins
                || !positiveFinite(brightnessScale) || !positiveFinite(varianceScale)
                || !positiveFinite(residualStdResponse) || !Double.isFinite(textureGate)
                || textureGate < 1.2) return null;

        java.util.ArrayList<NoiseRow> rows = new java.util.ArrayList<>();
        int overflowRows = 0;
        for (int row = 1; row < brightnessBins - 1; row++) {
            int offset = row * varianceBins;
            long total = 0;
            int occupied = 0;
            for (int column = 0; column < varianceBins; column++) {
                int count = histogram[offset + column];
                if (count > 0) {
                    total += count;
                    occupied++;
                }
            }
            // Overflow is right-censored, not zero noise. Counting it in the
            // population prevents heavily textured rows appearing artificially
            // quiet after their large residuals have been discarded.
            if (total < MIN_ROW_SAMPLES || total > Integer.MAX_VALUE) continue;
            if (histogram[offset + varianceBins - 1] > 0.15 * total) {
                overflowRows++;
                continue;
            }
            double target = 0.5 * total;
            long before = 0;
            double medianBin = Double.NaN;
            for (int column = 0; column < varianceBins - 1; column++) {
                int count = Math.max(0, histogram[offset + column]);
                if (count > 0 && before + count >= target) {
                    medianBin = column + (target - before) / count;
                    break;
                }
                before += count;
            }
            // A sub-bin median cannot distinguish noise from quantization or
            // censored black pixels. Retain the prior instead of inventing O.
            if (!Double.isFinite(medianBin) || medianBin < 1.0) continue;
            double sigma = medianBin / (varianceScale * HALF_NORMAL_MEDIAN * residualStdResponse);
            double brightness = (row + 0.5) / brightnessScale;
            brightness *= brightness;
            if (!positiveFinite(sigma) || brightness <= 3.0 * sigma) continue;
            rows.add(new NoiseRow(row, brightness, sigma * sigma, (int) total, occupied));
        }
        if (rows.size() < MIN_SPATIAL_ROWS) return null;

        for (NoiseRow row : rows) {
            int support = 0;
            for (NoiseRow neighbor : rows) {
                if (row == neighbor || Math.abs(row.index - neighbor.index) > 4) continue;
                double lowB = Math.min(row.brightness, neighbor.brightness);
                double highB = Math.max(row.brightness, neighbor.brightness);
                double lowV = row.brightness < neighbor.brightness ? row.variance : neighbor.variance;
                double highV = row.brightness < neighbor.brightness ? neighbor.variance : row.variance;
                double tolerance = 1.0 + Math.max(0.2,
                        3.0 / Math.sqrt(Math.min(row.samples, neighbor.samples)));
                // With S,O>=0, V must be nondecreasing and can grow no faster
                // than brightness. Allow finite-sample median uncertainty.
                if (highV * tolerance >= lowV && highV <= lowV * highB / lowB * tolerance) support++;
            }
            // Two compatible neighbors prevent a lone dip, or a pair of
            // mutually supporting dips, from defining a lower noise anchor.
            row.supported = support >= 2;
        }

        Model best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (int i = 0; i < rows.size(); i++) {
            NoiseRow a = rows.get(i);
            if (!a.supported) continue;
            // Constant-variance candidates cover read-noise-dominated inputs.
            Model constant = new Model(0, a.variance, 0);
            double score = curveScore(rows, constant, textureGate);
            if (score < bestScore) { best = constant; bestScore = score; }
            for (int j = i + 1; j < rows.size(); j++) {
                NoiseRow b = rows.get(j);
                if (!b.supported || b.brightness - a.brightness < MIN_BRIGHTNESS_SPAN) continue;
                double s = (b.variance - a.variance) / (b.brightness - a.brightness);
                double o = a.variance - s * a.brightness;
                if (s < 0 || o < 0) continue;
                Model candidate = new Model(s, o, 0);
                score = curveScore(rows, candidate, textureGate);
                if (score < bestScore) { best = candidate; bestScore = score; }
            }
        }
        if (best == null) return null;
        for (int pass = 0; pass < 3; pass++) {
            Model refined = refitRows(rows, best, textureGate);
            if (refined == null) return null;
            best = refined;
        }
        return new Model(best.s, best.o, best.points, best.rows, best.textureRows + overflowRows);
    }

    private static double curveScore(java.util.List<NoiseRow> rows, Model model, double gate) {
        double score = 0, weight = 0;
        double minB = Double.POSITIVE_INFINITY, maxB = 0;
        int consistent = 0;
        for (NoiseRow row : rows) {
            if (!row.supported) continue;
            double predicted = model.s * row.brightness + model.o;
            if (!positiveFinite(predicted)) return Double.POSITIVE_INFINITY;
            double residual = Math.log(row.variance / predicted);
            double w = Math.sqrt(Math.min(row.samples, 128));
            // Lower 20% quantile is an initializer only: final NNLS includes
            // the complete residual distributions of all consistent rows.
            score += w * (residual >= 0 ? 0.2 * residual : -0.8 * residual);
            weight += w;
            if (row.variance <= predicted * gate && row.variance * gate >= predicted) {
                consistent++;
                minB = Math.min(minB, row.brightness);
                maxB = Math.max(maxB, row.brightness);
            }
        }
        return consistent >= MIN_SPATIAL_ROWS && maxB - minB >= MIN_BRIGHTNESS_SPAN
                ? score / weight : Double.POSITIVE_INFINITY;
    }

    private static Model refitRows(java.util.List<NoiseRow> rows, Model prior, double gate) {
        double sw = 0, swb = 0, swv = 0, swb2 = 0, swbv = 0;
        double minB = Double.POSITIVE_INFINITY, maxB = 0;
        int retained = 0, texture = 0, points = 0;
        long samples = 0;
        for (NoiseRow row : rows) {
            double predicted = prior.s * row.brightness + prior.o;
            if (row.variance > predicted * gate) { texture++; continue; }
            if (!row.supported || row.variance * gate < predicted) continue;
            double w = Math.min(row.samples, 128) / (predicted * predicted);
            sw += w;
            swb += w * row.brightness;
            swv += w * row.variance;
            swb2 += w * row.brightness * row.brightness;
            swbv += w * row.brightness * row.variance;
            minB = Math.min(minB, row.brightness);
            maxB = Math.max(maxB, row.brightness);
            retained++;
            points += row.occupied;
            samples += row.samples;
        }
        if (retained < MIN_SPATIAL_ROWS || samples < MIN_SAMPLES
                || maxB - minB < MIN_BRIGHTNESS_SPAN || !positiveFinite(sw)) return null;
        double meanB = swb / sw;
        double vb = swb2 / sw - meanB * meanB;
        if (vb < MIN_BRIGHTNESS_VARIANCE) return null;
        double s = (swbv / sw - meanB * swv / sw) / vb;
        double o = (swv - s * swb) / sw;
        if (s < 0) { s = 0; o = swv / sw; }
        else if (o < 0) { o = 0; s = swbv / swb2; }
        return Double.isFinite(s) && Double.isFinite(o) && s >= 0 && o >= 0
                ? new Model(s, o, points, retained, texture) : null;
    }

    /**
     * Fit variance = S * brightness + O, followed by an optional second pass
     * that rejects histogram cells inconsistent with the first fit. Clipped
     * brightness/variance cells contain an unknown value and cannot be fitted.
     * A narrow brightness range cannot distinguish read noise from shot noise;
     * return null so the caller can retain a calibrated model instead.
     */
    public static Model fitHistogram(int[] histogram, int brightnessBins, int varianceBins,
                                     double brightnessScale, double varianceScale,
                                     double calibration, int maxOccupiedBins, double gate) {
        if (histogram == null || brightnessBins < 2 || varianceBins < 2
                || histogram.length != brightnessBins * varianceBins
                || !positiveFinite(brightnessScale) || !positiveFinite(varianceScale)
                || !positiveFinite(calibration) || maxOccupiedBins < 1) return null;

        Model first = fitPass(histogram, brightnessBins, varianceBins, brightnessScale,
                varianceScale, calibration, maxOccupiedBins, null, 0.0);
        if (first == null || !positiveFinite(gate)) return first;
        Model second = fitPass(histogram, brightnessBins, varianceBins, brightnessScale,
                varianceScale, calibration, maxOccupiedBins, first, gate);
        return second != null ? second : first;
    }

    private static Model fitPass(int[] histogram, int brightnessBins, int varianceBins,
                                 double brightnessScale, double varianceScale,
                                 double calibration, int maxOccupiedBins, Model prior,
                                 double gate) {
        double sw = 0, swb = 0, swv = 0, swb2 = 0, swbv = 0;
        double minBrightness = Double.POSITIVE_INFINITY, maxBrightness = 0;
        int rows = 0, points = 0;
        for (int row = 0; row < brightnessBins - 1; row++) {
            double b = (row + 0.5) / brightnessScale;
            b *= b;
            int occupied = 0;
            boolean acceptedRow = false;
            for (int column = 0; column < varianceBins - 1; column++) {
                int count = histogram[row * varianceBins + column];
                if (count <= 0) continue;
                if (occupied++ >= maxOccupiedBins) break;
                double v = (column + 0.5) / (varianceScale * calibration);
                v *= v;
                if (prior != null && v > gate * (prior.s * b + prior.o)) continue;
                sw += count;
                swb += count * b;
                swv += count * v;
                swb2 += count * b * b;
                swbv += count * b * v;
                minBrightness = Math.min(minBrightness, b);
                maxBrightness = Math.max(maxBrightness, b);
                points++;
                acceptedRow = true;
            }
            if (acceptedRow) rows++;
        }
        if (sw < MIN_SAMPLES || rows < MIN_BRIGHTNESS_ROWS
                || maxBrightness - minBrightness < MIN_BRIGHTNESS_SPAN) return null;
        double meanB = swb / sw;
        double brightnessVariance = swb2 / sw - meanB * meanB;
        if (brightnessVariance < MIN_BRIGHTNESS_VARIANCE) return null;
        double s = (swbv / sw - meanB * (swv / sw)) / brightnessVariance;
        double o = (swv - s * swb) / sw;
        // Nonnegative least squares: refit the remaining coefficient on the
        // boundary. Merely clamping S leaves the negative-slope intercept far
        // above the observed variance, which previously blurred low-ISO shots.
        if (s < 0) {
            s = 0;
            o = swv / sw;
        } else if (o < 0) {
            o = 0;
            s = swbv / swb2;
        }
        if (!Double.isFinite(s) || !Double.isFinite(o) || s < 0 || o < 0) return null;
        return new Model(s, o, points);
    }

    public static boolean isLowIso(int iso, int analogIso) {
        return iso > 0 && iso <= Math.max(200, analogIso / 8);
    }

    /**
     * Until higher-ISO calibration is available, bound positive texture leakage
     * against the sensor model. The allowance is in variance units, so 2 means
     * at most sqrt(2) times the baseline sigma at every brightness.
     */
    public static Model limitToBaseline(Model fitted, double baselineS, double baselineO,
                                        double varianceAllowance) {
        if (fitted == null || !Double.isFinite(varianceAllowance) || varianceAllowance < 1)
            return fitted;
        if (!Double.isFinite(baselineS) || !Double.isFinite(baselineO)
                || baselineS < 0 || baselineO < 0 || baselineS + baselineO <= 0) return fitted;
        double s = Math.min(fitted.s, baselineS * varianceAllowance);
        double o = Math.min(fitted.o, baselineO * varianceAllowance);
        if (fitted.o > o && fitted.s <= 1e-10) {
            // Only the zero-slope NNLS boundary has lost the shot-noise term
            // (1e-10 is the caller's storage floor). A positive measured slope
            // can be much lower than an inaccurate sensor prior. Limiting O
            // must not replace that measurement with a larger shot-noise curve.
            s = baselineS * varianceAllowance;
            o = baselineO * varianceAllowance;
        }
        return new Model(s, o, fitted.points, fitted.rows, fitted.textureRows);
    }

    private static boolean positiveFinite(double value) {
        return Double.isFinite(value) && value > 0;
    }
}
