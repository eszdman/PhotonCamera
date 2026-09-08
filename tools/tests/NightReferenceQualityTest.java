import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;
import com.particlesdevs.photoncamera.processing.parameters.NightRawMetrics;
import com.particlesdevs.photoncamera.processing.parameters.NightReferenceSelector;

public final class NightReferenceQualityTest {
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static NightReferenceSelector.Quality q(double sharp, double clipping) {
        NightReferenceSelector.Quality q = new NightReferenceSelector.Quality();
        q.sharpness = sharp; q.clipping = clipping; return q;
    }
    private static ByteBuffer raw(int[] values) {
        ByteBuffer buffer = ByteBuffer.allocate(values.length * 2).order(ByteOrder.nativeOrder());
        for (int v : values) buffer.putShort((short) v);
        buffer.flip(); return buffer;
    }
    private static NightRawMetrics metrics(ByteBuffer buffer) {
        return NightRawMetrics.measure(buffer, 384, 256, 4095, new float[]{64,64,64,64}, .00001, .000001);
    }
    public static void main(String[] args) {
        double[] exposure = {1,1,4}; float[] gyro = {0,10,0};
        NightReferenceSelector.Quality[] quality = {q(.02,0), q(.1,0), q(1,0)};
        require(NightReferenceSelector.select(exposure, gyro, quality) == 1, "Sharp short wins; long excluded");
        quality[1].clipping = .02;
        require(NightReferenceSelector.select(exposure, gyro, quality) == 0, "Protect highlights");
        require(NightReferenceSelector.select(exposure, gyro, null) == 0, "Missing quality falls back to gyro");
        quality[0] = q(.1,0); quality[1] = q(.1,0);
        quality[0].subjectChange = .5; quality[0].alignment = .2;
        quality[1].subjectChange = 0; quality[1].alignment = 1;
        require(NightReferenceSelector.select(exposure, gyro, quality) == 1, "Prefer alignable unchanged scene");
        quality[0] = q(.1,0); quality[1] = q(.1,0);
        quality[0].timeDistanceSeconds = 4; quality[1].timeDistanceSeconds = .1;
        require(NightReferenceSelector.select(exposure, new float[]{0,0,0}, quality) == 1, "Prefer shutter moment on equal quality");
        int[] values = new int[384 * 256], translated = values.clone();
        int[] cells = new int[48 * 32]; Random random = new Random(9);
        for (int i = 0; i < cells.length; i++) cells[i] = 400 + random.nextInt(2500);
        for (int y = 0; y < 256; y++) for (int x = 0; x < 384; x++) values[y * 384 + x] = cells[(y / 8) * 48 + x / 8];
        for (int y = 0; y < 256; y++) for (int x = 0; x < 384; x++) translated[y * 384 + x] = values[y * 384 + Math.min(383,x + 8)];
        ByteBuffer buffer = raw(values);
        NightRawMetrics textured = metrics(buffer);
        require(buffer.position() == 0, "Do not consume RAW buffer");
        require(textured.compare(textured)[0] == 0, "Identical frame has no subject change");
        require(textured.compare(metrics(raw(translated)))[0] == 0, "Camera translation is compensated");
        for (int y = 80; y < 150; y++) for (int x = 100; x < 180; x++) translated[y * 384 + x] = 3500;
        require(textured.compare(metrics(raw(translated)))[0] > .02, "Independent subject change remains");
        java.util.Arrays.fill(values, 1000);
        require(metrics(raw(values)).sharpness < textured.sharpness, "Flat scene is less detailed");
        java.util.Arrays.fill(values, 4095);
        require(metrics(raw(values)).clippedFraction == 1, "RAW clipping measured");
        require(metrics(ByteBuffer.allocate(8)) == null, "Malformed RAW falls back");
        System.out.println("Night reference quality checks passed");
    }
}
