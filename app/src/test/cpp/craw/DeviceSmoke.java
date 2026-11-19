import com.particlesdevs.photoncamera.util.CrawWriter;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Standalone app_process JNI test; no camera/microphone capture or app installation. */
public final class DeviceSmoke {
    public static void main(String[] args) throws Exception {
        ByteBuffer raw = ByteBuffer.allocateDirect(16 * 12 * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < 16 * 12; i++) raw.putShort((short) (32768 + i));
        ByteBuffer encoded = ByteBuffer.allocateDirect(8192);
        for (int codec : new int[]{1, 3}) {
            int size = CrawWriter.encode(raw, 16, 12, 32, false, 0, 12, false, 2, codec, encoded);
            if (encoded.get(0) != codec || encoded.get(1) != 16 || encoded.get(2) != 2) throw new AssertionError();
            try (CrawWriter writer = CrawWriter.open(args[0] + "/jni-" + codec + ".craw", "{}", 2, 48000)) {
                writer.writeShading(0, 1, 2, 2, new float[]{1, 2, 3, 4, 1, 2, 3, 4, 1, 2, 3, 4, 1, 2, 3, 4});
                writer.writeFrame(encoded, size, 0, "{\"lshd_ref\":1}");
                writer.writeAudio(new short[]{-32768, 32767, 123, -123}, 0);
                writer.writeImu(1, new long[]{10, 20}, new float[]{1, 2, 3, 4, 5, 6});
                writer.writeImu(2, new long[]{15}, new float[]{7, 8, 9});
                if (writer.frameCount() != 1) throw new AssertionError();
                try { writer.writeImu(1, new long[]{1}, new float[2]); throw new AssertionError("array accepted"); }
                catch (IOException expected) { }
                writer.close(); writer.close();
                try { writer.writeAudio(new short[2], 1); throw new AssertionError("closed writer accepted"); }
                catch (IllegalStateException expected) { }
            }
        }
        try { CrawWriter.encode(ByteBuffer.allocate(32), 4, 4, 8, false, 0, 4, false, 0, 1, encoded);
            throw new AssertionError("heap buffer accepted"); } catch (IOException expected) { }
        raw.limit(raw.capacity() - 1);
        try { CrawWriter.encode(raw, 16, 12, 32, false, 0, 12, false, 0, 3, encoded);
            throw new AssertionError("truncated plane accepted"); } catch (IOException expected) { }
        // Repeated ownership transfer/close should not leak file descriptors.
        int before = new File("/proc/self/fd").list().length;
        for (int i = 0; i < 64; i++) {
            try (CrawWriter writer = CrawWriter.open(args[0] + "/fd-check.craw", "{}", 2, 48000)) { }
        }
        int after = new File("/proc/self/fd").list().length;
        if (after > before + 2) throw new AssertionError("fd leak " + before + " -> " + after);
        // Exercise Android's real JSON/rational classes and the shading crop interpolation.
        Class<?> metadata = Class.forName("com.particlesdevs.photoncamera.util.CrawMetadata");
        java.lang.reflect.Method json = metadata.getDeclaredMethod("json", Object.class);
        json.setAccessible(true);
        Object rationals = json.invoke(null, (Object) new android.util.Rational[]{new android.util.Rational(3, 2)});
        if (!"[[3,2]]".equals(rationals.toString())) throw new AssertionError(rationals);
        Object maps = metadata.getConstructor().newInstance();
        for (String field : new String[]{"mapTop", "mapBottom"}) {
            java.lang.reflect.Field f = metadata.getDeclaredField(field); f.setAccessible(true);
            f.setFloat(maps, field.equals("mapTop") ? .25f : .75f);
        }
        java.lang.reflect.Method resample = metadata.getDeclaredMethod("outputShading", float[].class, int.class, int.class);
        resample.setAccessible(true);
        float[] source = new float[3 * 3 * 4];
        for (int y = 0; y < 3; y++) for (int x = 0; x < 3; x++) for (int c = 0; c < 4; c++)
            source[(y * 3 + x) * 4 + c] = 1 + y + c;
        float[] mapped = (float[]) resample.invoke(maps, source, 3, 3);
        for (int y = 0; y < 3; y++) for (int x = 0; x < 3; x++) for (int c = 0; c < 4; c++)
            if (mapped[(y * 3 + x) * 4 + c] != 1.5f + y * .5f + c) throw new AssertionError("shading crop");
        System.out.println("CRAW Android JNI: Fast/Live, PCM, IMU, shading, bounds, close and fd ownership passed");
    }
}
