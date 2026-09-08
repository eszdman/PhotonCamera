import java.nio.FloatBuffer;
import com.particlesdevs.photoncamera.processing.parameters.NightUncertaintyMap;

public final class NightUncertaintyMapTest {
    private static void require(boolean result) { if (!result) throw new AssertionError("Uncertainty map validation"); }
    public static void main(String[] args) {
        FloatBuffer map = FloatBuffer.wrap(new float[]{.001f, 1, 0, 1, .0001f, .1f, 0, 1});
        require(NightUncertaintyMap.valid(map, 2, 1, 17, 16));
        require(map.position() == 0);
        require(!NightUncertaintyMap.valid(map, 1, 2, 17, 16));
        require(!NightUncertaintyMap.valid(map, 2, 1, 16, 16));
        require(!NightUncertaintyMap.valid(null, 2, 1, 17, 16));
        map.put(1, Float.NaN);
        require(!NightUncertaintyMap.valid(map, 2, 1, 17, 16));
        map.put(1, 1.01f);
        require(!NightUncertaintyMap.valid(map, 2, 1, 17, 16));
        map.put(1, 1); map.put(3, 0);
        require(!NightUncertaintyMap.valid(map, 2, 1, 17, 16));
        map.put(3, 1); map.position(4);
        require(NightUncertaintyMap.valid(map, 1, 1, 16, 16));
        require(map.position() == 4);
        require(!NightUncertaintyMap.valid(FloatBuffer.allocate(8), 2, 1, 17, 16));
        System.out.println("Night uncertainty map checks passed");
    }
}
