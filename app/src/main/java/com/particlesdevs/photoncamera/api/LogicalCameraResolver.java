package com.particlesdevs.photoncamera.api;

import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.util.Range;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.settings.PreferenceKeys;
import com.particlesdevs.photoncamera.ui.camera.data.CameraLensData;
import com.particlesdevs.photoncamera.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Discovers the physical member lenses behind a logical camera id.
 *
 * <p>Used by the video-mode logical-id feature: given e.g. logical id "5",
 * returns its members (0.6x ultra-wide, 1x wide, 3.1x tele, ...) with the
 * zoom factor of each derived exactly like the normal lens scan (35mm
 * equivalent relative to the 1x member).
 *
 * <p>Results are cached per logical id for the process lifetime; cameras do
 * not change at runtime. Call {@link #clearCache()} if that ever stops
 * holding.
 */
public final class LogicalCameraResolver {
    private static final String TAG = "LogicalCameraResolver";

    /** Separator for member pill ids ({@code logicalId#physicalId}, e.g. "5#2"). */
    public static final String MEMBER_ID_SEPARATOR = "#";

    /** One physical member of a logical camera. */
    public static final class Member {
        /** Physical camera id as known to {@link CameraManager}. */
        public final String physicalId;
        /** Pill id used by the lens selector while logical mode is active. */
        public final String memberId;
        public final float focalLength;
        public final float aperture;
        public final float focal35mm;
        /** Zoom factor relative to the widest member (wide == 1.0). */
        public final float zoomFactor;
        public final float maxDigitalZoom;
        public final int facing;
        /** True for user-entered fallback entries (no physical stream). */
        public final boolean synthetic;

        Member(String logicalId, String physicalId, float focalLength, float aperture,
                float focal35mm, float zoomFactor, float maxDigitalZoom, int facing) {
            this(logicalId, physicalId, focalLength, aperture, focal35mm, zoomFactor,
                    maxDigitalZoom, facing, false);
        }

        Member(String logicalId, String physicalId, float focalLength, float aperture,
                float focal35mm, float zoomFactor, float maxDigitalZoom, int facing,
                boolean synthetic) {
            this.physicalId = physicalId;
            this.memberId = logicalId + MEMBER_ID_SEPARATOR + physicalId;
            this.focalLength = focalLength;
            this.aperture = aperture;
            this.focal35mm = focal35mm;
            this.zoomFactor = zoomFactor;
            this.maxDigitalZoom = maxDigitalZoom;
            this.facing = facing;
            this.synthetic = synthetic;
        }

        @NonNull
        @Override
        public String toString() {
            return memberId + " zf=" + zoomFactor;
        }
    }

    private static final Map<String, List<Member>> sCache = new HashMap<>();
    private static final Map<String, Range<Float>> sZoomRangeCache = new HashMap<>();

    private LogicalCameraResolver() {}

    public static void clearCache() {
        sCache.clear();
        sZoomRangeCache.clear();
    }

    /** True when {@code id} names a logical camera (has physical members). */
    public static boolean isLogicalCamera(@Nullable Context context, @Nullable String id) {
        if (context == null || id == null || id.isEmpty()) return false;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false;
        try {
            CameraManager manager =
                    (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            if (manager == null) return false;
            CameraCharacteristics chars = manager.getCameraCharacteristics(id.trim());
            return chars != null && !chars.getPhysicalCameraIds().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Resolves the member lenses of a logical camera, sorted by zoom factor
     * ascending (ultra-wide first). Empty when the id is not logical,
     * unreachable, or has no usable same-facing members.
     */
    @NonNull
    public static List<Member> resolveMembers(@Nullable Context context, @Nullable String logicalId) {
        if (context == null || logicalId == null) return Collections.emptyList();
        String id = logicalId.trim();
        if (id.isEmpty()) return Collections.emptyList();
        synchronized (sCache) {
            List<Member> cached = sCache.get(id);
            if (cached != null) return cached;
        }
        List<Member> members = queryMembers(context, id);
        synchronized (sCache) {
            sCache.put(id, members);
        }
        return members;
    }

    /**
     * Seamless {@code CONTROL_ZOOM_RATIO} range of the logical camera
     * (API 30+), or null when unknown. Inside this range the HAL switches
     * members without reopening the device.
     */
    @Nullable
    public static Range<Float> getLogicalZoomRatioRange(@Nullable Context context,
            @Nullable String logicalId) {
        if (context == null || logicalId == null) return null;
        String id = logicalId.trim();
        if (id.isEmpty()) return null;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null;
        synchronized (sZoomRangeCache) {
            if (sZoomRangeCache.containsKey(id)) return sZoomRangeCache.get(id);
        }
        Range<Float> range = null;
        try {
            CameraManager manager =
                    (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            if (manager != null) {
                CameraCharacteristics chars = manager.getCameraCharacteristics(id);
                if (chars != null) {
                    range = chars.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "getLogicalZoomRatioRange failed for " + id, e);
        }
        synchronized (sZoomRangeCache) {
            sZoomRangeCache.put(id, range);
        }
        return range;
    }

    /** Splits a member pill id ({@code logicalId#physicalId}) or returns null. */
    @Nullable
    public static String[] splitMemberId(@Nullable String memberId) {
        if (memberId == null || !memberId.contains(MEMBER_ID_SEPARATOR)) return null;
        String[] parts = memberId.split(MEMBER_ID_SEPARATOR);
        if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()) return null;
        return parts;
    }

    /** True for member pill ids created by this resolver. */
    public static boolean isMemberId(@Nullable String id) {
        return splitMemberId(id) != null;
    }

    /**
     * True when video logical-id mode is effectively active: VIDEO mode, the
     * toggle on, and a usable member list (manual entry when set, else
     * discovered members).
     */
    public static boolean isVideoLogicalActive(@Nullable Context context) {
        try {
            if (PhotonCamera.getSettings() == null
                    || PhotonCamera.getSettings().selectedMode != CameraMode.VIDEO) {
                return false;
            }
            if (!PreferenceKeys.isVideoUseLogicalId()) return false;
            if (context == null) return false;
            // The selfie camera is not a member of the logical back camera:
            // flipping to it must open the front device normally instead of
            // being overridden back to the logical id.
            if (isSelectedCameraFrontFacing(context)) return false;
            return !resolveEffectiveMembers(context, PreferenceKeys.getVideoLogicalId()).isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * True when the currently selected camera id is a front-facing camera.
     * Member pill ids ({@code logical#physical}) are reduced to their physical
     * part. Unknown or unreachable ids return false, so composite/synthetic
     * ids keep logical mode enabled.
     */
    public static boolean isSelectedCameraFrontFacing(@Nullable Context context) {
        if (context == null) return false;
        try {
            String selected = PreferenceKeys.getCameraID();
            if (selected == null || selected.isEmpty()) return false;
            String[] member = splitMemberId(selected);
            String id = member != null ? member[1] : selected.trim();
            if (id.isEmpty()) return false;
            CameraManager manager =
                    (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            if (manager == null) return false;
            CameraCharacteristics chars = manager.getCameraCharacteristics(id);
            Integer facing = chars != null ? chars.get(CameraCharacteristics.LENS_FACING) : null;
            return facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Member list actually used by the video logical mode: user-entered zoom
     * factors when set (they win over discovery), else auto-discovered
     * members (ratio-only synthetic members), else empty.
     */
    @NonNull
    public static List<Member> resolveEffectiveMembers(@Nullable Context context,
            @Nullable String logicalId) {
        if (context == null || logicalId == null) return Collections.emptyList();
        String id = logicalId.trim();
        if (id.isEmpty()) return Collections.emptyList();
        List<Float> manual;
        try {
            manual = parseManualZooms(PreferenceKeys.getVideoLogicalLenses());
        } catch (Exception e) {
            manual = Collections.emptyList();
        }
        if (!manual.isEmpty()) {
            int facing = CameraCharacteristics.LENS_FACING_BACK;
            try {
                CameraManager manager =
                        (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
                if (manager != null) {
                    CameraCharacteristics chars = manager.getCameraCharacteristics(id);
                    if (chars != null) {
                        Integer f = chars.get(CameraCharacteristics.LENS_FACING);
                        if (f != null) facing = f;
                    }
                }
            } catch (Exception ignored) {
            }
            List<Member> out = new ArrayList<>();
            int i = 0;
            for (Float zf : manual) {
                out.add(new Member(id, "m" + (i++), zf, 0f, zf * 24f, zf, 4f, facing, true));
            }
            Log.d(TAG, "logical " + id + " manual members=" + out);
            return out;
        }
        return resolveMembers(context, id);
    }

    /**
     * Parses a comma-separated zoom-factor list (e.g. "0.6,1,3.1") into
     * sorted, deduplicated positive factors (max 8).
     */
    @NonNull
    public static List<Float> parseManualZooms(@Nullable String text) {
        List<Float> out = new ArrayList<>();
        if (text == null) return out;
        for (String part : text.split(",")) {
            try {
                float zf = Float.parseFloat(part.trim());
                if (zf > 0f && zf < 100f) out.add(zf);
            } catch (Exception ignored) {
            }
            if (out.size() >= 8) break;
        }
        Collections.sort(out);
        List<Float> deduped = new ArrayList<>();
        for (Float zf : out) {
            if (deduped.isEmpty() || Math.abs(zf - deduped.get(deduped.size() - 1)) > 1e-4f) {
                deduped.add(zf);
            }
        }
        return deduped;
    }

    /** Finds a member by pill id in a resolved list, or null. */
    @Nullable
    public static Member findMember(@Nullable List<Member> members, @Nullable String memberId) {
        if (members == null || memberId == null) return null;
        for (Member m : members) {
            if (m != null && memberId.equals(m.memberId)) return m;
        }
        return null;
    }

    /** Converts members to pill-ready lens data (labels render via zoom factor). */
    @NonNull
    public static List<CameraLensData> toCameraLensData(@Nullable List<Member> members) {
        List<CameraLensData> out = new ArrayList<>();
        if (members == null) return out;
        for (Member m : members) {
            if (m == null) continue;
            CameraLensData data = new CameraLensData(m.memberId);
            data.setFacing(m.facing);
            data.setCameraFocalLength(m.focalLength);
            data.setCameraAperture(m.aperture);
            data.setCamera35mmFocalLength(m.focal35mm);
            data.setZoomFactor(m.zoomFactor);
            data.setFlashSupported(false);
            out.add(data);
        }
        return out;
    }

    @NonNull
    private static List<Member> queryMembers(@NonNull Context context, @NonNull String logicalId) {
        List<Member> out = new ArrayList<>();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return out;
        try {
            CameraManager manager =
                    (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            if (manager == null) return out;
            CameraCharacteristics logicalChars;
            try {
                logicalChars = manager.getCameraCharacteristics(logicalId);
            } catch (Exception e) {
                Log.w(TAG, "logical camera " + logicalId + " unreachable", e);
                return out;
            }
            if (logicalChars == null) return out;
            Set<String> physicalIds = logicalChars.getPhysicalCameraIds();
            if (physicalIds == null || physicalIds.isEmpty()) {
                Log.w(TAG, "camera " + logicalId + " has no physical members");
                return out;
            }
            Integer logicalFacing = logicalChars.get(CameraCharacteristics.LENS_FACING);

            List<Member> found = new ArrayList<>();
            for (String pid : physicalIds) {
                try {
                    CameraCharacteristics chars = manager.getCameraCharacteristics(pid);
                    if (chars == null) continue;
                    Integer facing = chars.get(CameraCharacteristics.LENS_FACING);
                    if (logicalFacing != null && facing != null && !logicalFacing.equals(facing)) {
                        continue;
                    }
                    float[] focals = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
                    float[] apertures = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES);
                    android.util.SizeF sensorSize =
                            chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
                    if (focals == null || focals.length == 0 || sensorSize == null
                            || sensorSize.getWidth() <= 0) {
                        continue;
                    }
                    float focal = focals[0];
                    float aperture = (apertures != null && apertures.length > 0) ? apertures[0] : 0f;
                    // Same 35mm formula as CameraManager2.createNewCameraLensData.
                    float focal35 = 36.0f / sensorSize.getWidth() * focal;
                    Float maxZoom = chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
                    float maxDigitalZoom = maxZoom != null && maxZoom > 1f ? maxZoom : 4f;
                    found.add(new Member(logicalId, pid, focal, aperture, focal35,
                            0f, maxDigitalZoom,
                            facing != null ? facing : CameraCharacteristics.LENS_FACING_BACK));
                } catch (Exception e) {
                    Log.w(TAG, "skipping physical member " + pid, e);
                }
            }
            if (found.isEmpty()) return out;
            // Zoom reference is the 1x member (like the normal lens scan
            // references its 1x main lens): the member whose focal matches
            // the logical camera's own focal length. Falls back to the
            // widest member when the logical focal is unknown.
            float logicalFocal = 0f;
            try {
                float[] logicalFocals =
                        logicalChars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
                if (logicalFocals != null && logicalFocals.length > 0) {
                    logicalFocal = logicalFocals[0];
                }
            } catch (Exception ignored) {
            }
            Member reference = null;
            if (logicalFocal > 0f) {
                float best = Float.MAX_VALUE;
                for (Member m : found) {
                    float diff = Math.abs(m.focalLength - logicalFocal)
                            / Math.max(m.focalLength, logicalFocal);
                    if (diff < best) {
                        best = diff;
                        reference = m;
                    }
                }
                if (best > 0.05f) reference = null;
            }
            if (reference == null) {
                reference = Collections.min(found, Comparator.comparingDouble(m -> m.focal35mm));
            }
            final float referenceFocal35 = reference.focal35mm;
            Log.d(TAG, "logical " + logicalId + " zoom reference=" + reference.physicalId);
            for (Member m : found) {
                out.add(new Member(logicalId, m.physicalId, m.focalLength, m.aperture,
                        m.focal35mm, m.focal35mm / referenceFocal35,
                        m.maxDigitalZoom, m.facing));
            }
            out.sort(Comparator.comparingDouble(m -> m.zoomFactor));
            Log.d(TAG, "logical " + logicalId + " members=" + out);
        } catch (Exception e) {
            Log.w(TAG, "resolveMembers failed for " + logicalId, e);
            return new ArrayList<>();
        }
        return out;
    }
}
