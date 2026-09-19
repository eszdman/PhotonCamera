package com.particlesdevs.photoncamera.api;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.particlesdevs.photoncamera.settings.TunableKeyManager;

import org.junit.Test;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for the tunable-key init phase ("Capture" default / "Session")
 * and duplicate coexistence. All framework-free: no camera device needed.
 */
public class TunableKeyInitTest {

    private static final Type LIST_TYPE =
            new TypeToken<List<VendorTagUtils.TunableKey>>() {}.getType();

    private static VendorTagUtils.TunableKey key(String name, String value, String init) {
        VendorTagUtils.TunableKey key = new VendorTagUtils.TunableKey();
        key.type = "CaptureRequest";
        key.name = name;
        key.valueType = "Integer";
        key.value = value;
        key.init = init;
        return key;
    }

    @Test
    public void newKeyDefaultsToCaptureInit() {
        VendorTagUtils.TunableKey key = new VendorTagUtils.TunableKey();
        assertEquals("Capture", key.init);
        assertFalse(VendorTagUtils.TunableKey.isSessionInit(key));
        assertFalse(VendorTagUtils.TunableKey.isSessionInit(null));
    }

    @Test
    public void legacyJsonWithoutInitReadsAsCapture() {
        // Stored before the init field existed: must behave as capture-init.
        String json = "[{\"type\":\"CaptureRequest\",\"name\":\"com.vendor.mode\","
                + "\"valueType\":\"Integer\",\"value\":\"2\","
                + "\"supported\":false,\"tested\":false}]";
        List<VendorTagUtils.TunableKey> keys = new Gson().fromJson(json, LIST_TYPE);
        assertEquals(1, keys.size());
        assertFalse(VendorTagUtils.TunableKey.isSessionInit(keys.get(0)));
        assertTrue(TunableKeyManager.sessionSubset(keys).isEmpty());
    }

    @Test
    public void sessionInitRoundTripsThroughJson() {
        List<VendorTagUtils.TunableKey> keys = new ArrayList<>();
        keys.add(key("com.vendor.mode", "2", "Session"));
        String json = new Gson().toJson(keys);
        List<VendorTagUtils.TunableKey> back = new Gson().fromJson(json, LIST_TYPE);
        assertEquals(1, back.size());
        assertEquals("Session", back.get(0).init);
        assertTrue(VendorTagUtils.TunableKey.isSessionInit(back.get(0)));
        assertEquals(1, TunableKeyManager.sessionSubset(back).size());
    }

    @Test
    public void duplicateNameAndValueCoexist() {
        List<VendorTagUtils.TunableKey> keys = new ArrayList<>();
        keys.add(key("com.vendor.mode", "2", "Capture"));
        keys.add(key("com.vendor.mode", "2", "Session"));
        assertEquals(2, keys.size());
        // Survives a storage round-trip without collapsing.
        List<VendorTagUtils.TunableKey> back =
                new Gson().fromJson(new Gson().toJson(keys), LIST_TYPE);
        assertEquals(2, back.size());
        assertEquals("Capture", back.get(0).init);
        assertEquals("Session", back.get(1).init);
    }

    @Test
    public void sessionSubsetSplitsMixedList() {
        List<VendorTagUtils.TunableKey> keys = new ArrayList<>();
        keys.add(key("com.vendor.a", "1", "Capture"));
        keys.add(key("com.vendor.b", "2", "Session"));
        keys.add(key("com.vendor.c", "3", "Session"));
        List<VendorTagUtils.TunableKey> subset = TunableKeyManager.sessionSubset(keys);
        assertEquals(2, subset.size());
        assertEquals("com.vendor.b", subset.get(0).name);
        assertEquals("com.vendor.c", subset.get(1).name);
    }

    @Test
    public void sessionSubsetHoldsLiveReferences() {
        List<VendorTagUtils.TunableKey> keys = new ArrayList<>();
        keys.add(key("com.vendor.b", "2", "Session"));
        List<VendorTagUtils.TunableKey> subset = TunableKeyManager.sessionSubset(keys);
        // Flags set on the subset must be visible when the full list is saved.
        subset.get(0).tested = true;
        subset.get(0).supported = true;
        assertSame(subset.get(0), keys.get(0));
        assertTrue(keys.get(0).tested);
        assertTrue(keys.get(0).supported);
    }

    @Test
    public void sessionSubsetHandlesNullAndEmpty() {
        assertTrue(TunableKeyManager.sessionSubset(null).isEmpty());
        assertTrue(TunableKeyManager.sessionSubset(new ArrayList<>()).isEmpty());
    }
}
