import com.particlesdevs.photoncamera.processing.parameters.NightBracketing;
import com.particlesdevs.photoncamera.processing.parameters.NightFrameCount;

public final class NightBracketingTest {
    private static void require(boolean value) {
        if (!value) throw new AssertionError("Night bracketing regression");
    }
    public static void main(String[] args) {
        NightBracketing steady = new NightBracketing(true, 2, 8, 25, false);
        require(steady.ratio == 8 && !steady.isLong(0) && !steady.isLong(1));
        int longs = 0;
        for (int i = 0; i < 30; i++) if (steady.isLong(i)) longs++;
        require(longs == 3);
        require(new NightBracketing(false, 2, 8, 25, false).ratio == 1);
        require(new NightBracketing(true, 0, 8, 25, false).ratio == 1);
        require(new NightBracketing(true, 2, 1, 25, false).ratio == 1);
        require(new NightBracketing(true, 2, 8, 400, false).ratio == 1);
        require(new NightBracketing(true, 2, 8, -1, false).ratio == 1);
        require(new NightBracketing(true, 1, 8, 25, true).ratio == 4);
        NightBracketing moving = new NightBracketing(true, 2, 8, 200, false);
        require(moving.ratio < steady.ratio && moving.isLong(2) && !moving.isLong(5) && moving.isLong(8));
        require(NightFrameCount.select(16, 25, false, new double[]{.1, .1, 4, .1}) == 2);
        require(NightFrameCount.select(16, 25, false, new double[]{.1, .1, .3, .1}) == 4);
        System.out.println("Night bracketing regression checks passed");
    }
}
