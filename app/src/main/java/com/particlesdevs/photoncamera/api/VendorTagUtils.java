package com.particlesdevs.photoncamera.api;

import android.annotation.SuppressLint;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.CameraCharacteristics;

import com.particlesdevs.photoncamera.util.Log;
import com.particlesdevs.photoncamera.settings.TunableKeyManager;

import java.lang.reflect.Array;
import java.util.List;

public class VendorTagUtils {
    private static final String TAG = "VendorTagUtils";

    private static boolean isSupported(CaptureRequest.Builder builder,
                                       CaptureRequest.Key<?> key) {
        boolean supported = true;
        try {
            builder.get(key);
        }catch(IllegalArgumentException exception){
            supported = false;
            Log.w(TAG,"vendor tag " + key.getName() + " is not supported");
        }
        if ( supported ) {
            Log.d(TAG,"vendor tag " + key.getName() + " is supported");
        }
        return supported;
    }

    @SuppressLint("NewApi")
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setPhysical(CaptureRequest.Builder builder, CaptureRequest.Key key, Object value, String physicalId) {
        try {
            builder.setPhysicalCameraKey(key, value, physicalId);
        } catch (Exception e) {
            Log.w(TAG, "Error setting physical camera key: " + key.getName() + " for camera: " + physicalId, e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setKeyValue(CaptureRequest.Builder builder, CaptureRequest.Key key, Object value) {
        builder.set(key, value);
    }

    /**
     * A user-defined vendor tag that can be created per physical sensor in the
     * Sensor Configurations settings. Supports both {@link CaptureRequest} and
     * {@link CaptureResult} keys, though only request keys are applied to the
     * {@link CaptureRequest.Builder} during capture.
     */
    public static class TunableKey {
        /** "CaptureRequest" or "CaptureResult" */
        public String type = "CaptureRequest";
        /** Vendor tag string, e.g. "com.qti.sensorbps.mode_index" */
        public String name = "";
        /** "Integer", "Long", "Float", "Byte", "int[]", ... */
        public String valueType = "Integer";
        /** Value as a string (comma separated for arrays) */
        public String value = "0";
        /** Whether the key was tested against the camera and found supported */
        public boolean supported = false;
        /** Whether the key has been tested yet (only possible via VendorTagUtils at capture) */
        public boolean tested = false;
        /**
         * Where the key is applied: "Capture" (default, per request builder)
         * or "Session" (session parameters at session creation). Missing in
         * legacy stored JSON, which reads as Capture.
         */
        public String init = "Capture";
        /**
         * Whether the user enabled the key. Disabled keys are kept in the
         * stored list but never applied to a request. Missing in legacy
         * stored JSON, which reads as enabled.
         */
        public boolean enabled = true;

        public TunableKey() {}

        public TunableKey(String type, String name, Class<?> valueClass, Object value) {
            this.type = type;
            this.name = name;
            this.valueType = valueTypeForClass(valueClass);
            this.value = valueToString(valueClass, value);
        }

        public Object parseValue() {
            return parseValue(valueType, value);
        }

        /**
         * True when this key is applied at session init (session parameters)
         * rather than per capture request. Null-safe: legacy stored JSON
         * without the field reads as capture-init.
         */
        public static boolean isSessionInit(TunableKey key) {
            return key != null && "Session".equals(key.init);
        }

        /**
         * True when the user enabled this key. Null-safe: legacy stored JSON
         * without the field reads as enabled.
         */
        public static boolean isEnabled(TunableKey key) {
            return key == null || key.enabled;
        }

        public CaptureRequest.Key<?> toCaptureRequestKey() {
            return new CaptureRequest.Key<>(name, classForValueType(valueType));
        }

        public CaptureResult.Key<?> toCaptureResultKey() {
            return new CaptureResult.Key<>(name, classForValueType(valueType));
        }

        public static String valueTypeForClass(Class<?> valueClass) {
            if (valueClass == null) return "Integer";
            if (valueClass == Integer.class) return "Integer";
            if (valueClass == Long.class) return "Long";
            if (valueClass == Float.class) return "Float";
            if (valueClass == Double.class) return "Double";
            if (valueClass == Byte.class) return "Byte";
            if (valueClass == Short.class) return "Short";
            if (valueClass == int[].class) return "int[]";
            if (valueClass == byte[].class) return "byte[]";
            if (valueClass == long[].class) return "long[]";
            if (valueClass == float[].class) return "float[]";
            if (valueClass == String.class) return "String";
            return "Integer";
        }

        public static Class<?> classForValueType(String valueType) {
            if (valueType == null) return Integer.class;
            switch (valueType) {
                case "Long": return Long.class;
                case "Float": return Float.class;
                case "Double": return Double.class;
                case "Byte": return Byte.class;
                case "Short": return Short.class;
                case "int[]": return int[].class;
                case "byte[]": return byte[].class;
                case "long[]": return long[].class;
                case "float[]": return float[].class;
                case "String": return String.class;
                case "Integer":
                default: return Integer.class;
            }
        }

        public static String valueToString(Class<?> valueClass, Object value) {
            if (value == null) return "0";
            if (valueClass != null && valueClass.isArray()) {
                StringBuilder sb = new StringBuilder();
                int len = Array.getLength(value);
                for (int i = 0; i < len; i++) {
                    if (i > 0) sb.append(",");
                    sb.append(Array.get(value, i));
                }
                return sb.toString();
            }
            return String.valueOf(value);
        }

        public static Object parseValue(String valueType, String value) {
            if (value == null) value = "";
            String vt = valueType == null ? "Integer" : valueType;
            switch (vt) {
                case "Long": return Long.parseLong(value.trim());
                case "Float": return Float.parseFloat(value.trim());
                case "Double": return Double.parseDouble(value.trim());
                case "Byte": return Byte.parseByte(value.trim());
                case "Short": return Short.parseShort(value.trim());
                case "int[]": {
                    String[] parts = value.split(",");
                    int[] arr = new int[parts.length];
                    for (int i = 0; i < parts.length; i++) arr[i] = Integer.parseInt(parts[i].trim());
                    return arr;
                }
                case "byte[]": {
                    String[] parts = value.split(",");
                    byte[] arr = new byte[parts.length];
                    for (int i = 0; i < parts.length; i++) arr[i] = Byte.parseByte(parts[i].trim());
                    return arr;
                }
                case "long[]": {
                    String[] parts = value.split(",");
                    long[] arr = new long[parts.length];
                    for (int i = 0; i < parts.length; i++) arr[i] = Long.parseLong(parts[i].trim());
                    return arr;
                }
                case "float[]": {
                    String[] parts = value.split(",");
                    float[] arr = new float[parts.length];
                    for (int i = 0; i < parts.length; i++) arr[i] = Float.parseFloat(parts[i].trim());
                    return arr;
                }
                case "String": return value;
                case "Integer":
                default: return Integer.parseInt(value.trim());
            }
        }
    }

    /**
     * Apply a list of user-defined tunable keys to a request builder.
     * Tests each key for support (via {@code builder.get}) and only sets it when
     * supported. Updates the {@code tested}/{@code supported} flags in place so the
     * settings UI can show green/red feedback afterwards. Only capture-init
     * keys are applied here; session-init keys belong in the session
     * parameters (see CaptureController session setup).
     */
    public static void applyTunableKeys(CaptureRequest.Builder builder, List<TunableKey> keys, String physicalId) {
        if (builder == null || keys == null) return;
        for (TunableKey tunableKey : keys) {
            if (tunableKey == null) continue;
            if (TunableKey.isSessionInit(tunableKey)) continue;
            if (!TunableKey.isEnabled(tunableKey)) continue;
            tunableKey.tested = true;
            if (!"CaptureRequest".equals(tunableKey.type)) {
                Log.w(TAG, "Tunable key " + tunableKey.name + " is a " + tunableKey.type + " key, cannot be applied to the request builder");
                tunableKey.supported = false;
                continue;
            }
            try {
                CaptureRequest.Key<?> key = tunableKey.toCaptureRequestKey();
                boolean supported = isSupported(builder, key);
                tunableKey.supported = supported;
                if (supported) {
                    Object parsedValue = tunableKey.parseValue();
                    setKeyValue(builder, key, parsedValue);
                    if (physicalId != null && !physicalId.isEmpty()) {
                        setPhysical(builder, key, parsedValue, physicalId);
                    }
                    Log.d(TAG, "Applied tunable key " + tunableKey.name + " = " + tunableKey.value + " (" + tunableKey.valueType + ")");
                }
            } catch (Exception e) {
                tunableKey.supported = false;
                Log.w(TAG, "Error applying tunable key " + tunableKey.name, e);
            }
        }
    }

    @SuppressLint({"NewApi", "LocalSuppress"})
    public static void builderSessionApply(CaptureRequest.Builder builder, boolean burst, boolean useMaximumResolutionKey, String physicalId) {
        try {
            byte enable = 1;
             var clientName = new CaptureRequest.Key<>("com.xiaomi.sessionparams.clientName", String.class);
            if(isSupported(builder,clientName)) {
                Log.d(TAG, "com.xiaomi.sessionparams.clientName supported");
                builder.set(clientName, "com.android.camera");
                setPhysical(builder, clientName, "com.android.camera", physicalId);
            }
            if(burst) {
                var remosaicEnabled = new CaptureRequest.Key<>("xiaomi.remosaic.enabled", Byte.class);
                if (isSupported(builder, remosaicEnabled)) {
                    builder.set(remosaicEnabled, enable);
                    setPhysical(builder, remosaicEnabled, enable, physicalId);
                }
                var remosaicQuadEnabled = new CaptureRequest.Key<>("xiaomi.quadcfa.enabled", Byte.class);
                if (isSupported(builder, remosaicQuadEnabled)) {
                    builder.set(remosaicQuadEnabled, enable);
                    setPhysical(builder, remosaicQuadEnabled, enable, physicalId);
                }
                var remosaicEnabled2 = new CaptureRequest.Key<>("com.mediatek.control.capture.remosaicenable", int[].class);
                if (isSupported(builder, remosaicEnabled2)) {
                    builder.set(remosaicEnabled2, new int[]{1});
                    setPhysical(builder, remosaicEnabled2, new int[]{1}, physicalId);
                }
            }
        } catch (Exception e){
            Log.w(TAG, "Error applying vendor tags to CaptureRequest.Builder", e);
        }
        if(useMaximumResolutionKey) {
            builder.set(CaptureRequest.SENSOR_PIXEL_MODE, CaptureRequest.SENSOR_PIXEL_MODE_MAXIMUM_RESOLUTION);
        }
        TunableKeyManager.applyTunableKeys(builder, physicalId);
    }

    /**
     * Whether hardware Optical Image Stabilization (OIS) is supported:
     * the sensor must advertise at least the two basic modes (OFF and ON).
     */
    public static boolean isOisSupported(CameraCharacteristics chars) {
        int[] modes = chars != null
                ? chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION) : null;
        return modes != null && modes.length >= 2;
    }
}
