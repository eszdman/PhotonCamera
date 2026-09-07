package com.particlesdevs.photoncamera.processing.parameters;

/** Small, bounded Night burst policy. Time values are seconds. */
public final class NightFrameCount {
    private NightFrameCount() {}

    public static int select(double isoRelativeToBase, int shake, boolean tripod, double[] seconds) {
        if (seconds.length == 0) throw new IllegalArgumentException("Empty capture plan");
        int desired = desiredCount(seconds.length, isoRelativeToBase, shake, tripod);
        double elapsed = 0;
        int count = 0;
        while (count < desired && count < seconds.length) {
            double next = validSeconds(seconds[count]) + 0.05;
            if (count > 0 && elapsed + next > (tripod ? 12.0 : 4.0)) break;
            elapsed += next;
            count++;
        }
        return Math.max(1, count);
    }

    public static int select(int maximum, double isoRelativeToBase, int shake,
                             boolean tripod, double normalSeconds, double bracketSeconds) {
        int desired = desiredCount(maximum, isoRelativeToBase, shake, tripod);
        double[] seconds = new double[desired];
        for (int i = 0; i < desired; i++) seconds[i] = i % 3 == 0 ? bracketSeconds : normalSeconds;
        return select(isoRelativeToBase, shake, tripod, seconds);
    }

    private static int desiredCount(int maximum, double isoRelativeToBase, int shake, boolean tripod) {
        maximum = Math.max(1, maximum);
        if (!Double.isFinite(isoRelativeToBase) || isoRelativeToBase < 1) isoRelativeToBase = 1;
        // Four frames at base ISO; increase gradually with capture gain.
        // This is a tuning policy, not a calibrated prediction of output SNR.
        double motion = tripod || shake < 0 ? 1.0
                : 1.0 + 0.5 * Math.min(1.0, Math.max(0.0, (shake - 25.0) / 375.0));
        return (int) Math.min(maximum, Math.ceil(4.0 * Math.sqrt(isoRelativeToBase) * motion));
    }

    private static double validSeconds(double seconds) {
        return Double.isFinite(seconds) && seconds > 0 ? seconds : 1.0;
    }
}
