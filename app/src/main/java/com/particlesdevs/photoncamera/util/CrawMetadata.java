package com.particlesdevs.photoncamera.util;

import android.graphics.Rect;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.params.BlackLevelPattern;
import android.hardware.camera2.params.ColorSpaceTransform;
import android.hardware.camera2.params.LensShadingMap;
import android.hardware.camera2.params.OisSample;
import android.hardware.camera2.params.RggbChannelVector;
import android.os.Build;
import android.util.Pair;
import android.util.Range;
import android.util.Rational;
import android.util.Size;
import android.util.SizeF;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.Arrays;

/** CRAW spec metadata plus original Camera2 keys for future readers. */
public final class CrawMetadata {
    private static final String[] CFA = {"RGGB", "GRBG", "GBRG", "BGGR"};
    private float[] lastShading;
    private int shadingId, sourceShadingId, shadingColumns, shadingRows;
    private float mapLeft = 0, mapTop = 0, mapRight = 1, mapBottom = 1;

    /** LensShadingMap spans the sensor active array. Remap it to the stored RAW crop. */
    public void setGeometry(CameraCharacteristics c, int width, int height, int top, int rows) {
        Size pixels = c.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE);
        Rect active = c.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        if (Build.VERSION.SDK_INT >= 31) {
            Size maximum = c.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE_MAXIMUM_RESOLUTION);
            Rect maximumActive = c.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE_MAXIMUM_RESOLUTION);
            if (maximum != null && maximumActive != null && width == maximum.getWidth()
                    && height == maximum.getHeight()) { pixels = maximum; active = maximumActive; }
        }
        if (pixels != null && active != null && active.width() > 1 && active.height() > 1) {
            float sx = pixels.getWidth() / (float) width, sy = pixels.getHeight() / (float) height;
            mapLeft = -active.left / (float) (active.width() - 1);
            mapRight = ((width - 1) * sx - active.left) / (active.width() - 1);
            mapTop = (top * sy - active.top) / (active.height() - 1);
            mapBottom = ((top + rows - 1) * sy - active.top) / (active.height() - 1);
        } else {
            mapTop = top / (float) Math.max(1, height - 1);
            mapBottom = (top + rows - 1) / (float) Math.max(1, height - 1);
        }
    }

    private float[] outputShading(float[] source, int columns, int rows) {
        float[] output = new float[source.length];
        for (int y = 0; y < rows; y++) {
            float sy = Math.max(0, Math.min(1, mapTop + (mapBottom - mapTop)
                    * y / Math.max(1, rows - 1))) * (rows - 1);
            int y0 = (int) sy, y1 = Math.min(y0 + 1, rows - 1);
            for (int x = 0; x < columns; x++) {
                float sx = Math.max(0, Math.min(1, mapLeft + (mapRight - mapLeft)
                        * x / Math.max(1, columns - 1))) * (columns - 1);
                int x0 = (int) sx, x1 = Math.min(x0 + 1, columns - 1);
                for (int channel = 0; channel < 4; channel++) {
                    float a = source[(y0 * columns + x0) * 4 + channel];
                    float b = source[(y0 * columns + x1) * 4 + channel];
                    float d = source[(y1 * columns + x0) * 4 + channel];
                    float e = source[(y1 * columns + x1) * 4 + channel];
                    float upper = a + (b - a) * (sx - x0), lower = d + (e - d) * (sx - x0);
                    output[(y * columns + x) * 4 + channel] = upper + (lower - upper) * (sy - y0);
                }
            }
        }
        return output;
    }

    public static int cfa(CameraCharacteristics c) {
        Integer value = c.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT);
        if (value == null || value < 0 || value > 3)
            throw new IllegalArgumentException("CRAW requires a Bayer camera");
        return value;
    }

    public static String container(CameraCharacteristics c, CaptureResult initial, String cameraId,
            int width, int height, int sourceWidth, int sourceHeight, int cropTop, int cropHeight,
            boolean raw10, boolean bin, int codec, int rotation, int fps, int channels, int rate,
            long origin, long sensorOffset) throws JSONException {
        JSONObject meta = new JSONObject();
        meta.put("generator", "PhotonCamera / CinematicRAWStudio-Encoder");
        meta.put("camera", new JSONObject().put("make", Build.MANUFACTURER)
                .put("model", Build.MODEL).put("camera_id", cameraId));
        JSONObject sensor = new JSONObject().put("width", width).put("height", height)
                .put("bit_depth", raw10 ? 10 : 16).put("cfa", CFA[cfa(c)]);
        put(sensor, "black_level", c.get(CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN));
        put(sensor, "white_level", c.get(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL));
        // Calibration remains in its original sensor coordinate system; output is described separately.
        put(sensor, "source_active_array", c.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE));
        sensor.put("active_array", new JSONArray(new int[]{0, 0, width, height}));
        put(sensor, "color_matrix1", c.get(CameraCharacteristics.SENSOR_COLOR_TRANSFORM1));
        put(sensor, "color_matrix2", c.get(CameraCharacteristics.SENSOR_COLOR_TRANSFORM2));
        put(sensor, "forward_matrix1", c.get(CameraCharacteristics.SENSOR_FORWARD_MATRIX1));
        put(sensor, "forward_matrix2", c.get(CameraCharacteristics.SENSOR_FORWARD_MATRIX2));
        put(sensor, "calibration1", c.get(CameraCharacteristics.SENSOR_CALIBRATION_TRANSFORM1));
        put(sensor, "calibration2", c.get(CameraCharacteristics.SENSOR_CALIBRATION_TRANSFORM2));
        put(sensor, "illuminant1", c.get(CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT1));
        put(sensor, "illuminant2", c.get(CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT2));
        if (initial != null) put(sensor, "noise_profile", initial.get(CaptureResult.SENSOR_NOISE_PROFILE));
        Integer timeSource = c.get(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE);
        sensor.put("timestamp_source", timeSource != null && timeSource == 1 ? "REALTIME" : "UNKNOWN");
        meta.put("sensor", sensor);
        JSONObject lens = new JSONObject();
        put(lens, "intrinsics", c.get(CameraCharacteristics.LENS_INTRINSIC_CALIBRATION));
        if (Build.VERSION.SDK_INT >= 28) {
            put(lens, "distortion", c.get(CameraCharacteristics.LENS_DISTORTION));
            put(lens, "pose_reference", c.get(CameraCharacteristics.LENS_POSE_REFERENCE));
        }
        put(lens, "pose_rotation", c.get(CameraCharacteristics.LENS_POSE_ROTATION));
        put(lens, "pose_translation", c.get(CameraCharacteristics.LENS_POSE_TRANSLATION));
        put(lens, "focal_lengths", c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS));
        put(lens, "apertures", c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES));
        Integer facing = c.get(CameraCharacteristics.LENS_FACING);
        lens.put("facing", facing == null ? "UNKNOWN" : facing == 1 ? "BACK" : facing == 0 ? "FRONT" : "EXTERNAL");
        meta.put("lens", lens);
        meta.put("video", new JSONObject().put("fps_num", fps).put("fps_den", 1)
                .put("rotation", rotation).put("codec", codec));
        meta.put("audio", new JSONObject().put("sample_rate", rate).put("channels", channels).put("format", "S16"));
        meta.put("capture_geometry", new JSONObject().put("source_width", sourceWidth)
                .put("source_height", sourceHeight).put("crop", new JSONArray(new int[]{0, cropTop, sourceWidth, cropHeight}))
                .put("binning", bin ? 2 : 1));
        meta.put("timing", new JSONObject().put("camera_origin_ns", origin)
                .put("sensor_to_camera_offset_ns", sensorOffset)
                .put("sync", timeSource != null && timeSource == 1 ? "shared_realtime" : "estimated_at_first_frame"));
        JSONObject all = new JSONObject();
        for (CameraCharacteristics.Key<?> key : c.getKeys()) {
            try { put(all, key.getName(), c.get(key)); } catch (RuntimeException ignored) { }
        }
        meta.put("camera2_characteristics", all);
        return meta.toString();
    }

    public String frame(CaptureResult result, long cameraTs, long origin, int rotation,
            CrawWriter writer) throws JSONException, IOException {
        JSONObject meta = new JSONObject().put("sensor_timestamp_ns", cameraTs)
                .put("rotation", rotation).put("metadata_matched", result != null);
        if (result == null) return meta.toString();
        put(meta, "exposure_ns", result.get(CaptureResult.SENSOR_EXPOSURE_TIME));
        put(meta, "iso", result.get(CaptureResult.SENSOR_SENSITIVITY));
        put(meta, "frame_duration_ns", result.get(CaptureResult.SENSOR_FRAME_DURATION));
        put(meta, "rolling_shutter_skew_ns", result.get(CaptureResult.SENSOR_ROLLING_SHUTTER_SKEW));
        put(meta, "focus_distance", result.get(CaptureResult.LENS_FOCUS_DISTANCE));
        put(meta, "focal_length", result.get(CaptureResult.LENS_FOCAL_LENGTH));
        put(meta, "aperture", result.get(CaptureResult.LENS_APERTURE));
        put(meta, "dynamic_black_level", result.get(CaptureResult.SENSOR_DYNAMIC_BLACK_LEVEL));
        put(meta, "dynamic_white_level", result.get(CaptureResult.SENSOR_DYNAMIC_WHITE_LEVEL));
        put(meta, "awb_gains", result.get(CaptureResult.COLOR_CORRECTION_GAINS));
        put(meta, "as_shot_neutral", result.get(CaptureResult.SENSOR_NEUTRAL_COLOR_POINT));
        put(meta, "ccm", result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM));
        put(meta, "noise_profile", result.get(CaptureResult.SENSOR_NOISE_PROFILE));
        put(meta, "intrinsics", result.get(CaptureResult.LENS_INTRINSIC_CALIBRATION));
        put(meta, "crop_region", result.get(CaptureResult.SCALER_CROP_REGION));
        if (Build.VERSION.SDK_INT >= 28) {
            put(meta, "distortion", result.get(CaptureResult.LENS_DISTORTION));
            OisSample[] ois = result.get(CaptureResult.STATISTICS_OIS_SAMPLES);
            if (ois != null) {
                JSONArray values = new JSONArray();
                for (OisSample sample : ois) values.put(new JSONObject()
                        .put("timestamp_ns", sample.getTimestamp() - origin)
                        .put("x_shift", sample.getXshift()).put("y_shift", sample.getYshift()));
                meta.put("ois_samples", values);
            }
        }
        LensShadingMap shading = result.get(CaptureResult.STATISTICS_LENS_SHADING_CORRECTION_MAP);
        if (shading != null) {
            float[] gains = new float[shading.getGainFactorCount()];
            shading.copyGainFactors(gains, 0);
            if (shadingColumns != shading.getColumnCount() || shadingRows != shading.getRowCount()
                    || !Arrays.equals(lastShading, gains)) {
                int next = shadingId + 1;
                writer.writeShading(cameraTs - origin, next, shading.getColumnCount(), shading.getRowCount(), gains);
                sourceShadingId = next;
                float[] output = outputShading(gains, shading.getColumnCount(), shading.getRowCount());
                if (!Arrays.equals(output, gains)) {
                    next++;
                    writer.writeShading(cameraTs - origin, next, shading.getColumnCount(), shading.getRowCount(), output);
                }
                shadingId = next;
                shadingColumns = shading.getColumnCount();
                shadingRows = shading.getRowCount();
                lastShading = gains;
            }
            meta.put("lshd_ref", shadingId);
            meta.put("lshd_coordinate_system", "stored_frame");
            meta.put("source_lshd_ref", sourceShadingId);
        }
        JSONObject all = new JSONObject();
        for (CaptureResult.Key<?> key : result.getKeys()) {
            if (key.equals(CaptureResult.STATISTICS_LENS_SHADING_CORRECTION_MAP)) continue;
            try { put(all, key.getName(), result.get(key)); } catch (RuntimeException ignored) { }
        }
        meta.put("camera2_result", all);
        return meta.toString();
    }

    private static void put(JSONObject object, String key, Object value) throws JSONException {
        if (value != null) object.put(key, json(value));
    }

    private static Object json(Object value) throws JSONException {
        if (value == null) return JSONObject.NULL;
        if (value instanceof Number || value instanceof Boolean || value instanceof String) {
            if (value instanceof Rational) {
                Rational r = (Rational) value;
                return new JSONArray(new int[]{r.getNumerator(), r.getDenominator()});
            }
            if (value instanceof Float || value instanceof Double)
                return Double.isFinite(((Number) value).doubleValue()) ? value : JSONObject.NULL;
            return value;
        }
        if (value.getClass().isArray()) {
            JSONArray result = new JSONArray();
            for (int i = 0; i < Array.getLength(value); i++) result.put(json(Array.get(value, i)));
            return result;
        }
        if (value instanceof ColorSpaceTransform) {
            Rational[] matrix = new Rational[9];
            ((ColorSpaceTransform) value).copyElements(matrix, 0);
            return json(matrix);
        }
        if (value instanceof BlackLevelPattern) {
            int[] black = new int[4];
            ((BlackLevelPattern) value).copyTo(black, 0);
            return json(black);
        }
        if (value instanceof RggbChannelVector) {
            float[] gains = new float[4];
            ((RggbChannelVector) value).copyTo(gains, 0);
            return json(gains);
        }
        if (value instanceof Rect) {
            Rect r = (Rect) value;
            return new JSONArray(new int[]{r.left, r.top, r.width(), r.height()});
        }
        if (value instanceof Size) {
            Size s = (Size) value;
            return new JSONArray(new int[]{s.getWidth(), s.getHeight()});
        }
        if (value instanceof SizeF) {
            SizeF s = (SizeF) value;
            return new JSONArray(new float[]{s.getWidth(), s.getHeight()});
        }
        if (value instanceof Pair) {
            Pair<?, ?> p = (Pair<?, ?>) value;
            return new JSONArray().put(json(p.first)).put(json(p.second));
        }
        if (value instanceof Range) {
            Range<?> r = (Range<?>) value;
            return new JSONArray().put(json(r.getLower())).put(json(r.getUpper()));
        }
        return value.toString();
    }
}
