package com.particlesdevs.photoncamera.api;

import androidx.annotation.StringRes;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.app.PhotonCamera;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public enum CameraMode {
    UNLIMITED(R.string.mode_unlimited),
    RAWVIDEO(R.string.mode_rawvideo),
    MOTION(R.string.mode_motion),
    PHOTO(R.string.mode_photo),
    NIGHT(R.string.mode_night),
    VIDEO(R.string.mode_video);

    int stringId;

    CameraMode(@StringRes int stringId) {
        this.stringId = stringId;
    }

    public static CameraMode valueOf(int modeOrdinal) {
        for (CameraMode mode : values()) {
            if (modeOrdinal == mode.ordinal()) {
                return mode;
            }
        }
        return PHOTO;
    }

    public static Integer[] nameIds() {
        return Stream.of(values()).map(mode -> mode.stringId).toArray(Integer[]::new);
    }

    /**
     * Parses a stored display order (comma-separated ordinals, e.g. "3,2,4,5,0,1")
     * into a full permutation of all modes: invalid/duplicate entries are dropped
     * and any missing modes are appended in enum order. Never returns null and
     * always contains every mode exactly once.
     */
    public static List<CameraMode> parseOrder(String raw) {
        List<CameraMode> order = new ArrayList<>();
        CameraMode[] all = values();
        if (raw != null) {
            for (String part : raw.split(",")) {
                try {
                    int ordinal = Integer.parseInt(part.trim());
                    if (ordinal >= 0 && ordinal < all.length && !order.contains(all[ordinal])) {
                        order.add(all[ordinal]);
                    }
                } catch (NumberFormatException ignored) {
                    // Drop corrupt entries.
                }
            }
        }
        for (CameraMode mode : all) {
            if (!order.contains(mode)) {
                order.add(mode);
            }
        }
        return order;
    }

    /**
     * Visible subset of {@code ordered} for the given hidden ordinals, keeping
     * the stored order. Defensive: if everything is hidden the full order is
     * returned so the selector never ends up empty.
     */
    public static List<CameraMode> filterVisible(List<CameraMode> ordered, Set<String> hiddenOrdinals) {
        List<CameraMode> visible = new ArrayList<>();
        if (ordered != null) {
            for (CameraMode mode : ordered) {
                if (hiddenOrdinals == null || !hiddenOrdinals.contains(String.valueOf(mode.ordinal()))) {
                    visible.add(mode);
                }
            }
        }
        if (visible.isEmpty()) {
            visible.addAll(ordered != null && !ordered.isEmpty() ? ordered : parseOrder(null));
        }
        return visible;
    }

    /**
     * Mode to switch to when {@code current} is hidden: the next visible mode
     * after its position in the stored order, wrapping around. Returns
     * {@code current} unchanged when it is already visible.
     */
    public static CameraMode findFallback(List<CameraMode> ordered, Set<String> hiddenOrdinals, CameraMode current) {
        List<CameraMode> visible = filterVisible(ordered, hiddenOrdinals);
        if (current != null && visible.contains(current)) {
            return current;
        }
        if (visible.isEmpty()) {
            return PHOTO;
        }
        int position = ordered != null ? ordered.indexOf(current) : -1;
        if (position < 0) {
            return visible.get(0);
        }
        for (int i = 1; i <= ordered.size(); i++) {
            CameraMode candidate = ordered.get((position + i) % ordered.size());
            if (visible.contains(candidate)) {
                return candidate;
            }
        }
        return visible.get(0);
    }

}
