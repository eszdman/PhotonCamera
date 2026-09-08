import com.particlesdevs.photoncamera.processing.parameters.NightMergeNoise;

public final class NightMergeNoiseTest {
    private static void close(double actual, double expected) {
        if (Math.abs(actual - expected) > Math.max(1e-12, Math.abs(expected) * 1e-5))
            throw new AssertionError(actual + " != " + expected);
    }
    public static void main(String[] args) {
        NightMergeNoise reference = new NightMergeNoise(.004, .0001, 1, 1);
        NightMergeNoise longer = new NightMergeNoise(.004, .0001, 1, .25);
        close(longer.radianceS, reference.radianceS / 4);
        close(longer.radianceO, reference.radianceO / 16);
        // Transform source-domain variance directly: y=a*x, Var(y)=a^2 Var(x).
        double x = .2, a = .25;
        close(longer.radianceS * (a * x) + longer.radianceO,
                a * a * (longer.sensorS * x + longer.sensorO));
        NightMergeNoise gain = new NightMergeNoise(.004, .0001, 2, .5);
        close(gain.radianceS, reference.radianceS);
        close(gain.radianceO, reference.radianceO);
        // Equal-ISO long frames have 4..16x confidence at equal reference radiance.
        double ratio = (reference.radianceS * .02 + reference.radianceO)
                / (longer.radianceS * .02 + longer.radianceO);
        if (ratio < 4 || ratio > 16) throw new AssertionError("Wrong bracket variance ratio");
        NightMergeNoise fallback = new NightMergeNoise(Double.NaN, -1, 1, 1);
        if (!Float.isFinite(fallback.radianceS) || fallback.radianceO <= 0) throw new AssertionError("Invalid fallback");
        boolean rejected = false;
        try { new NightMergeNoise(.004, .0001, 1, Double.NaN); }
        catch (IllegalArgumentException expected) { rejected = true; }
        if (!rejected) throw new AssertionError("Invalid exposure accepted");
        System.out.println("Night merge noise checks passed");
    }
}
