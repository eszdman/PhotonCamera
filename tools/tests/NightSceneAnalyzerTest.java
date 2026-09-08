import com.particlesdevs.photoncamera.processing.parameters.NightSceneAnalyzer;
import java.nio.ByteBuffer;
import java.util.Random;

public final class NightSceneAnalyzerTest {
    private static final int W = NightSceneAnalyzer.WIDTH, H = NightSceneAnalyzer.HEIGHT;
    private static final long T = 1_000_000_000L, DT = NightSceneAnalyzer.INTERVAL_NS;
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static ByteBuffer frame(int[] pixels) {
        ByteBuffer buffer = ByteBuffer.allocate(W * H * 4);
        for (int value : pixels) buffer.put((byte) value).put((byte) value).put((byte) value).put((byte) 255);
        buffer.flip();
        return buffer;
    }
    public static void main(String[] args) {
        NightSceneAnalyzer analyzer = new NightSceneAnalyzer();
        int[] base = new int[W * H];
        Random random = new Random(42);
        for (int i = 0; i < base.length; i++) base[i] = 40 + random.nextInt(150);
        analyzer.accept(frame(base), T);
        require(!analyzer.snapshot(T).motionReliable, "One frame cannot measure motion");
        analyzer.accept(frame(base), T + DT);
        require(analyzer.snapshot(T + DT).motionReliable, "Textured static scene should align");
        require(analyzer.snapshot(T + DT).subjectMotion == 0, "Static scene has no residual movement");
        int[] shifted = new int[base.length];
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++)
            shifted[y * W + x] = base[y * W + Math.min(W - 1, x + 2)];
        analyzer.reset();
        analyzer.accept(frame(base), T);
        analyzer.accept(frame(shifted), T + DT);
        NightSceneAnalyzer.Snapshot translation = analyzer.snapshot(T + DT);
        require(translation.motionReliable && Math.abs(translation.cameraMotion - 16) < .001,
                "Recover global translation in pixels/second");
        require(translation.subjectMotion == 0, "Global translation must not become subject motion");
        analyzer.accept(frame(shifted), T + 2 * DT);
        analyzer.accept(frame(shifted), T + 3 * DT);
        require(analyzer.snapshot(T + 3 * DT).frameCount == 4, "Aggregate four recent frames");
        require(analyzer.snapshot(T + 3 * DT).cameraMotion == 0, "Median suppresses isolated movement");
        for (int y = 15; y < 30; y++) for (int x = 20; x < 35; x++) shifted[y * W + x] = 245;
        analyzer.reset();
        analyzer.accept(frame(base), T);
        analyzer.accept(frame(shifted), T + DT);
        require(analyzer.snapshot(T + DT).motionReliable, "Background still aligns with moving foreground");
        require(analyzer.snapshot(T + DT).subjectMotion > .05, "Detect independent local change");
        analyzer.reset();
        analyzer.accept(frame(base), T);
        int[] brighter = base.clone();
        for (int i = 0; i < brighter.length; i++) brighter[i] += 10;
        analyzer.accept(frame(brighter), T + DT);
        require(analyzer.snapshot(T + DT).motionReliable && analyzer.snapshot(T + DT).subjectMotion == 0,
                "Modest AE shift should not become motion");
        analyzer.reset();
        analyzer.accept(frame(base), T);
        for (int i = 0; i < brighter.length; i++) brighter[i] = base[i] + 40;
        analyzer.accept(frame(brighter), T + DT);
        require(!analyzer.snapshot(T + DT).motionReliable, "Large AE change invalidates motion");
        require(analyzer.snapshot(T + 2_000_000_000L) == null, "Expire stopped preview");
        analyzer.accept(frame(base), T + 2_000_000_000L);
        require(!analyzer.snapshot(T + 2_000_000_000L).motionReliable, "Gap resets temporal history");
        analyzer.reset();
        require(analyzer.snapshot(T) == null, "Camera reset clears data");
        int[] flat = new int[base.length];
        analyzer.accept(frame(flat), T);
        analyzer.accept(frame(flat), T + DT);
        require(!analyzer.snapshot(T + DT).motionReliable, "Flat scene must not imply known zero motion");
        require(analyzer.snapshot(T + DT).shadowFraction == 1, "Black preview shadow fraction");
        for (int i = 0; i < flat.length; i++) flat[i] = i < flat.length / 2 ? 0 : 255;
        analyzer.accept(frame(flat), T + 2 * DT);
        NightSceneAnalyzer.Snapshot histogram = analyzer.snapshot(T + 2 * DT);
        require(histogram.clippedFraction == .5 && histogram.shadowP10 == 0 && histogram.highlightP99 == 1,
                "Known histogram percentiles and clipping");
        analyzer.accept(frame(base), T + DT);
        require(analyzer.snapshot(T + 2 * DT) == histogram, "Ignore out-of-order samples");
        System.out.println("Night scene analysis checks passed");
    }
}
