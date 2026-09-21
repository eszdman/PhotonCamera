package com.particlesdevs.photoncamera.api;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class CameraModeOrderTest {

    private static List<CameraMode> enumOrder() {
        return Arrays.asList(CameraMode.values());
    }

    private static Set<String> ordinals(CameraMode... modes) {
        Set<String> set = new HashSet<>();
        for (CameraMode mode : modes) {
            set.add(String.valueOf(mode.ordinal()));
        }
        return set;
    }

    @Test
    public void parseOrder_nullOrEmpty_returnsEnumOrder() {
        assertEquals(enumOrder(), CameraMode.parseOrder(null));
        assertEquals(enumOrder(), CameraMode.parseOrder(""));
    }

    @Test
    public void parseOrder_partialOrder_appendsMissingModes() {
        List<CameraMode> parsed = CameraMode.parseOrder("4,3");
        assertEquals(Arrays.asList(
                CameraMode.NIGHT,
                CameraMode.PHOTO,
                CameraMode.UNLIMITED,
                CameraMode.RAWVIDEO,
                CameraMode.MOTION,
                CameraMode.VIDEO), parsed);
    }

    @Test
    public void parseOrder_duplicatesAndInvalidEntries_areDropped() {
        List<CameraMode> parsed = CameraMode.parseOrder("2,2,abc,1,99,-1,");
        assertEquals(Arrays.asList(
                CameraMode.MOTION,
                CameraMode.RAWVIDEO,
                CameraMode.UNLIMITED,
                CameraMode.PHOTO,
                CameraMode.NIGHT,
                CameraMode.VIDEO), parsed);
        assertEquals(CameraMode.values().length, parsed.size());
    }

    @Test
    public void parseOrder_alreadyComplete_keepsStoredOrder() {
        List<CameraMode> parsed = CameraMode.parseOrder("5,4,3,2,1,0");
        assertEquals(Arrays.asList(
                CameraMode.VIDEO,
                CameraMode.NIGHT,
                CameraMode.PHOTO,
                CameraMode.MOTION,
                CameraMode.RAWVIDEO,
                CameraMode.UNLIMITED), parsed);
    }

    @Test
    public void filterVisible_removesHiddenModes_keepingOrder() {
        List<CameraMode> ordered = Arrays.asList(
                CameraMode.VIDEO,
                CameraMode.PHOTO,
                CameraMode.NIGHT,
                CameraMode.MOTION,
                CameraMode.RAWVIDEO,
                CameraMode.UNLIMITED);
        List<CameraMode> visible = CameraMode.filterVisible(ordered, ordinals(CameraMode.PHOTO));
        assertEquals(Arrays.asList(
                CameraMode.VIDEO,
                CameraMode.NIGHT,
                CameraMode.MOTION,
                CameraMode.RAWVIDEO,
                CameraMode.UNLIMITED), visible);
    }

    @Test
    public void filterVisible_allHidden_fallsBackToFullOrder() {
        List<CameraMode> ordered = CameraMode.parseOrder("3,2,1,0,5,4");
        assertEquals(ordered, CameraMode.filterVisible(ordered, ordinals(CameraMode.values())));
    }

    @Test
    public void findFallback_currentVisible_returnsCurrent() {
        List<CameraMode> ordered = CameraMode.parseOrder("3,2,1,0,5,4");
        assertEquals(CameraMode.VIDEO,
                CameraMode.findFallback(ordered, ordinals(CameraMode.PHOTO), CameraMode.VIDEO));
    }

    @Test
    public void findFallback_currentHidden_returnsNextInStoredOrder() {
        List<CameraMode> ordered = Arrays.asList(
                CameraMode.VIDEO,
                CameraMode.PHOTO,
                CameraMode.NIGHT,
                CameraMode.MOTION,
                CameraMode.RAWVIDEO,
                CameraMode.UNLIMITED);
        assertEquals(CameraMode.NIGHT,
                CameraMode.findFallback(ordered, ordinals(CameraMode.PHOTO), CameraMode.PHOTO));
        assertEquals(CameraMode.MOTION,
                CameraMode.findFallback(ordered, ordinals(CameraMode.PHOTO, CameraMode.NIGHT),
                        CameraMode.PHOTO));
    }

    @Test
    public void findFallback_wrapsAroundToFirstVisible() {
        List<CameraMode> ordered = Arrays.asList(
                CameraMode.PHOTO,
                CameraMode.VIDEO,
                CameraMode.NIGHT,
                CameraMode.MOTION,
                CameraMode.RAWVIDEO,
                CameraMode.UNLIMITED);
        Set<String> hidden = ordinals(CameraMode.VIDEO, CameraMode.NIGHT, CameraMode.MOTION,
                CameraMode.RAWVIDEO, CameraMode.UNLIMITED);
        assertEquals(CameraMode.PHOTO,
                CameraMode.findFallback(ordered, hidden, CameraMode.UNLIMITED));
    }

    @Test
    public void findFallback_nullCurrent_returnsFirstVisible() {
        List<CameraMode> ordered = CameraMode.parseOrder("3,2,1,0,5,4");
        assertEquals(CameraMode.MOTION,
                CameraMode.findFallback(ordered, ordinals(CameraMode.PHOTO), null));
    }

    @Test
    public void findFallback_emptyOrder_fallsBackToEnumOrder() {
        assertEquals(CameraMode.UNLIMITED,
                CameraMode.findFallback(Collections.emptyList(), Collections.emptySet(), null));
    }
}
