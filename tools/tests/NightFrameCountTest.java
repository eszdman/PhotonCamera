import com.particlesdevs.photoncamera.processing.parameters.NightFrameCount;

public final class NightFrameCountTest {
    private static int count(int maximum, double gain, int shake, boolean tripod, double normal, double bracket) {
        return NightFrameCount.select(maximum, gain, shake, tripod, normal, bracket);
    }
    private static void require(boolean condition) {
        if (!condition) throw new AssertionError("Night frame-count regression");
    }
    public static void main(String[] args) {
        require(count(20, 1, 25, false, .1, .1) == 4);
        require(count(20, 4, 25, false, .1, .1) == 8);
        require(count(20, 4, 400, false, .1, .1) == 12);
        require(count(20, 4, -1, false, .1, .1) == 8);
        require(count(3, 100, 400, false, .1, .1) == 3);
        require(count(20, 100, 400, false, 1, 1) == 3);
        require(count(20, 100, 400, true, 2, 2) == 5);
        require(count(20, 100, 25, false, .1, 1) < count(20, 100, 25, false, .1, .1));
        require(count(20, 100, 25, false, 30, 30) == 1);
        require(count(0, 1, 0, false, .1, .1) == 1);
        require(count(20, Double.NaN, 0, false, Double.NaN, Double.NaN) >= 1);
        for (int maximum = 1; maximum <= 30; maximum++) {
            for (int shake = -1; shake <= 800; shake += 40) {
                int n = count(maximum, 16, shake, false, .2, .33);
                require(n >= 1 && n <= maximum);
                double duration = ((n + 2) / 3) * .33 + (n - (n + 2) / 3) * .2 + n * .05;
                require(duration <= 4.0);
            }
        }
        System.out.println("Night frame-count regression checks passed");
    }
}
