import com.particlesdevs.photoncamera.processing.parameters.NightReferenceSelector;

/** Standalone regression checks, runnable without Android or Gradle. */
public final class NightReferenceSelectorTest {
    private static void check(int expected, double[] exposures, float[] shake) {
        int actual = NightReferenceSelector.select(exposures, shake);
        if (actual != expected) throw new AssertionError(expected + " != " + actual);
    }

    public static void main(String[] args) {
        check(2, new double[]{4, 1, 1, 8}, new float[]{0, 10, 2, 0});
        check(0, new double[]{1}, new float[]{Float.NaN});
        check(1, new double[]{4, 1, 1}, new float[]{0, Float.NaN, Float.NaN});
        check(2, new double[]{1, 1, 1}, new float[]{Float.NaN, -1, 5});
        check(0, new double[]{1, 1}, new float[]{2, 2});
        check(1, new double[]{1, 1}, new float[]{Float.POSITIVE_INFINITY, 0});
        check(1, new double[]{1.001, 1}, new float[]{0, 5});
        try {
            NightReferenceSelector.select(new double[]{0}, new float[]{0});
            throw new AssertionError("Invalid exposure accepted");
        } catch (IllegalArgumentException expected) {
            // Invalid metrics must not silently pick an incompatible reference.
        }
        System.out.println("Night reference regression checks passed");
    }
}
