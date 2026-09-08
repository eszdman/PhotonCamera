package com.particlesdevs.photoncamera.processing.parameters;

/** Pure bounded capture search. ISO is sensor ISO; time is seconds. */
public final class NightCapturePlanner {
    public static final class Input {
        public double baseSeconds, baseIso, minSeconds, maxSeconds, minIso, maxIso;
        public int maxFrames;
        public double budgetSeconds = 4, overheadSeconds = .05, minFrameSeconds;
        public boolean tripod, ois, motionReliable;
        public double cameraPixelsPerSecond, subjectFraction, clippedFraction, shadowP10 = .1;
        public double contrast, shake = -1;
        // Profile at noiseIso, before temporal merge; absent profile uses a labeled heuristic.
        public double noiseS = Double.NaN, noiseO = Double.NaN, noiseIso;
        public double maxBracketRatio = 1;
    }

    public static final class Plan {
        public final double shortSeconds, longSeconds, durationSeconds, score;
        public final int iso, shortCount, longCount;
        public final boolean sensorNoiseProfile;
        private Plan(double shortTime, double longTime, int sensitivity, int shorts, int longs,
                     double duration, double cost, boolean profile) {
            shortSeconds = shortTime; longSeconds = longTime; iso = sensitivity;
            shortCount = shorts; longCount = longs; durationSeconds = duration;
            score = cost; sensorNoiseProfile = profile;
        }
        public int size() { return shortCount + longCount; }
        /** Keep two short frames first; distribute long frames through the remainder. */
        public boolean isLong(int slot) {
            if (slot < 0 || slot >= size()) throw new IllegalArgumentException("Invalid slot");
            if (slot < 2 || longCount == 0) return false;
            int remaining = size() - 2;
            return (slot - 1) * longCount / remaining > (slot - 2) * longCount / remaining;
        }
    }

    public static Plan select(Input p) {
        if (!positive(p.baseSeconds) || !positive(p.baseIso) || !positive(p.minSeconds)
                || !positive(p.maxSeconds) || p.maxSeconds < p.minSeconds
                || !positive(p.minIso) || !positive(p.maxIso) || p.maxIso < p.minIso
                || p.maxFrames < 1 || !positive(p.budgetSeconds)
                || !Double.isFinite(p.overheadSeconds) || p.overheadSeconds < 0
                || !Double.isFinite(p.minFrameSeconds) || p.minFrameSeconds < 0) return null;
        boolean profile = positive(p.noiseS) && Double.isFinite(p.noiseO) && p.noiseO >= 0 && positive(p.noiseIso);
        double subject = p.motionReliable ? clamp(p.subjectFraction, 0, 1) : .1;
        double camera = p.motionReliable ? clamp(p.cameraPixelsPerSecond, 0, 1000) : 1;
        // Preview motion already includes OIS; only the gyro proxy gets OIS relief.
        double gyro = p.tripod ? 0 : clamp(p.shake < 0 ? 100 : p.shake, 0, 1000) / 100.0 * (p.ois ? .7 : 1);
        double speed = Math.max(camera, gyro);
        double clipping = clamp(p.clippedFraction, 0, 1);
        // Preview tone mapping makes absolute radiance unknowable. This is a bounded shadow proxy.
        double shadow = clamp(Math.pow(clamp(p.shadowP10, 0, 1), 2.2), .005, .18);
        double originalEnergy = p.baseSeconds * p.baseIso;
        double highlightSafety = clipping >= .01 ? .5 : clipping >= .002 ? .75 : 1;
        double energy = originalEnergy * highlightSafety;
        // Absolute shadow SNR target: dividing by the baseline variance would cancel
        // noise magnitude and make clean and noisy sensors choose identical counts.
        double targetVariance = Math.max(1e-8, shadow * shadow * .25);
        Plan best = null;
        // Half-stop shutter lattice, plus exact endpoints and the original shutter.
        for (int candidate = -4; candidate <= 40; candidate++) {
            double seconds = candidate == -4 ? energy / p.minIso : candidate == -3 ? energy / p.maxIso
                    : candidate == -2 ? p.minSeconds : candidate == -1 ? p.baseSeconds
                    : p.maxSeconds / Math.pow(2, candidate * .5);
            if (seconds < p.minSeconds || seconds > p.maxSeconds) continue;
            int iso = (int) Math.round(energy / seconds);
            if (iso < Math.ceil(p.minIso) || iso > Math.floor(p.maxIso)) continue;
            double shortVariance = variance(p, seconds, iso, originalEnergy, shadow, profile);
            double blur = speed * seconds;
            double subjectBlur = subject * Math.pow(seconds / .04, 2);
            double maxRatio = clamp(p.maxBracketRatio, 1, 8);
            // Unknown/high motion gets equal exposures; bracket only for visible contrast/clipping.
            boolean bracket = p.motionReliable && subject < .08 && p.contrast > .55 && clipping > .001;
            double desiredRatio = bracket ? 1 + (maxRatio - 1) * clamp((.08 - subject) / .08, 0, 1) : 1;
            for (int ratioSlot = 0; ratioSlot < 3; ratioSlot++) {
                double ratio = ratioSlot == 0 ? 1 : ratioSlot == 1 ? Math.sqrt(desiredRatio) : desiredRatio;
                double longSeconds = Math.min(p.maxSeconds, seconds * ratio);
                if (ratioSlot > 0 && longSeconds < seconds * 1.5) continue;
                double actualRatio = longSeconds / seconds;
                double longVariance = variance(p, longSeconds, iso, originalEnergy, shadow, profile);
                // Stage 7 uses confidence times inverse radiance variance.
                double acceptance = Math.exp(-Math.pow(speed * longSeconds, 2) - subject * Math.pow(longSeconds / .04, 2));
                for (int count = 1; count <= Math.min(p.maxFrames, 64); count++) {
                    for (int longs = 0; longs <= (ratioSlot == 0 ? 0 : Math.min(3, count - 2)); longs++) {
                        if (ratioSlot > 0 && longs == 0) continue;
                        int shorts = count - longs;
                        double duration = shorts * Math.max(seconds, p.minFrameSeconds)
                                + longs * Math.max(longSeconds, p.minFrameSeconds) + count * p.overheadSeconds;
                        if (duration > p.budgetSeconds) continue;
                        double information = shorts / shortVariance + longs * acceptance / longVariance;
                        double outputVariance = 1.0 / information;
                        double noiseCost = outputVariance / targetVariance;
                        // Short frames cover clipped highlights; long-frame clipping is locally rejected.
                        double clippingCost = clipping * 4 * highlightSafety
                                + longs * clipping * Math.max(0, actualRatio * highlightSafety - 1) / count;
                        double cost = noiseCost + .25 * blur * blur + .15 * subjectBlur
                                + clippingCost + .18 * duration / p.budgetSeconds
                                + .03 * longs * (1 - acceptance) / count;
                        if (best == null || cost < best.score) best = new Plan(seconds, longSeconds, iso,
                                shorts, longs, duration, cost, profile);
                    }
                }
            }
        }
        return best;
    }

    private static double variance(Input p, double time, double iso, double referenceEnergy,
                                   double shadow, boolean profile) {
        double gain = iso / (profile ? p.noiseIso : p.minIso);
        double s = (profile ? p.noiseS : .0005) * gain;
        double o = (profile ? p.noiseO : .000002) * gain * gain;
        double exposureRatio = time * iso / referenceEnergy;
        return Math.max(1e-12, (s * shadow * exposureRatio + o) / (exposureRatio * exposureRatio));
    }
    private static boolean positive(double value) { return Double.isFinite(value) && value > 0; }
    private static double clamp(double value, double low, double high) {
        return Double.isFinite(value) ? Math.max(low, Math.min(high, value)) : low;
    }
}
