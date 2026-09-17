package com.particlesdevs.photoncamera.api;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for the In-Sensor Zoom (ISZ) virtual-lens mechanics that do not
 * depend on the Android camera framework: virtual id composition/routing and the
 * {@link VendorTagUtils.TunableKey} round-trip used to carry the ISZ capture key.
 */
public class IszVirtualLensTest {

    // --- virtual id composition / routing (framework-free helpers) ---

    @Test
    public void iszVirtualForPlainBase() {
        // Plain base id "3" -> virtual id "3-3-v".
        String virtual = IszLensUtil.composeIszVirtualId("3");
        assertEquals("3-3-v", virtual);
        assertTrue(IszLensUtil.isIszVirtual(virtual));
        // Routing resolves correctly for both logical and physical segments.
        assertEquals(3, IszLensUtil.physicalIdFrom(virtual));
        assertFalse(IszLensUtil.isIszVirtual("3"));
    }

    @Test
    public void iszVirtualForCompositeBase() {
        // Composite base id "0-3" -> virtual id "0-3-v"; physical stays 3.
        String virtual = IszLensUtil.composeIszVirtualId("0-3");
        assertEquals("0-3-v", virtual);
        assertTrue(IszLensUtil.isIszVirtual(virtual));
        assertEquals(3, IszLensUtil.physicalIdFrom(virtual));
    }

    @Test
    public void physicalIdFromHandlesComposite() {
        assertEquals(3, IszLensUtil.physicalIdFrom("0-3"));
    }

    @Test
    public void physicalIdFromHandlesPlain() {
        assertEquals(3, IszLensUtil.physicalIdFrom("3"));
    }

    @Test
    public void isIszVirtualRejectsNullAndReal() {
        assertFalse(IszLensUtil.isIszVirtual(null));
        assertFalse(IszLensUtil.isIszVirtual(""));
        assertFalse(IszLensUtil.isIszVirtual("0-3"));
        assertFalse(IszLensUtil.isIszVirtual("0-3-vv"));
    }

    // --- TunableKey round-trip for ISZ key types ---

    @Test
    public void tunableKeyRoundTripsScalarInteger() {
        VendorTagUtils.TunableKey key = new VendorTagUtils.TunableKey();
        key.type = "CaptureRequest";
        key.valueType = "Integer";
        key.name = "com.vendor.isz.mode";
        key.value = "2";
        assertEquals(Integer.valueOf(2), key.parseValue());
        assertEquals("Integer", VendorTagUtils.TunableKey.valueTypeForClass(int.class));
    }

    @Test
    public void tunableKeyRoundTripsScalarFloat() {
        VendorTagUtils.TunableKey key = new VendorTagUtils.TunableKey();
        key.type = "CaptureRequest";
        key.valueType = "Float";
        key.name = "com.vendor.isz.ratio";
        key.value = "2.0";
        assertEquals(Float.valueOf(2.0f), key.parseValue());
    }

    @Test
    public void tunableKeyRoundTripsIntArray() {
        VendorTagUtils.TunableKey key = new VendorTagUtils.TunableKey();
        key.type = "CaptureRequest";
        key.valueType = "int[]";
        key.name = "com.vendor.isz.modes";
        key.value = "1,2,3";
        int[] parsed = (int[]) key.parseValue();
        assertEquals(3, parsed.length);
        assertEquals(1, parsed[0]);
        assertEquals(2, parsed[1]);
        assertEquals(3, parsed[2]);
    }

    @Test
    public void tunableKeyRoundTripsString() {
        VendorTagUtils.TunableKey key = new VendorTagUtils.TunableKey();
        key.type = "CaptureRequest";
        key.valueType = "String";
        key.name = "com.vendor.isz.name";
        key.value = "in-sensor-zoom";
        assertEquals("in-sensor-zoom", key.parseValue());
    }

    @Test
    public void tunableKeyRoundTripsByte() {
        VendorTagUtils.TunableKey key = new VendorTagUtils.TunableKey();
        key.type = "CaptureRequest";
        key.valueType = "Byte";
        key.name = "com.vendor.isz.enable";
        key.value = "1";
        assertEquals(Byte.valueOf((byte) 1), key.parseValue());
    }

    @Test
    public void iszKeyDefaultsToCaptureRequest() {
        VendorTagUtils.TunableKey key = new VendorTagUtils.TunableKey("CaptureRequest",
                "com.vendor.isz.mode", Integer.class, 2);
        assertEquals("CaptureRequest", key.type);
        assertEquals("Integer", key.valueType);
        assertEquals("2", key.value);
    }
}
