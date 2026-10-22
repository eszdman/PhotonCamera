package com.particlesdevs.photoncamera.manual;

import com.particlesdevs.photoncamera.circularbarlib.camera.ExposureIndex;
import com.particlesdevs.photoncamera.processing.parameters.ColorTemperatureConverter;

import java.util.Locale;

public final class ManualAutoValues {

    private ManualAutoValues() {
    }

    public static String formatFocus(Float diopters) {
        if (diopters == null) {
            return null;
        }
        if (diopters <= 0f) {
            return "Inf";
        }
        return String.format(Locale.ROOT, "%.1fm", 1.0f / diopters);
    }

    /** Live exposure result, suffixed with "s" to mark seconds (e.g. "1/125s"). */
    public static String formatExposure(Long exposureNs) {
        if (exposureNs == null || exposureNs <= 0L) {
            return null;
        }
        return ExposureIndex.sec2string(ExposureIndex.time2sec(exposureNs)) + "s";
    }

    public static String formatIso(Integer iso) {
        if (iso == null || iso <= 0) {
            return null;
        }
        return String.valueOf(iso);
    }

    public static String formatWb(Integer kelvin) {
        if (kelvin == null || kelvin < ColorTemperatureConverter.MIN_KELVIN
                || kelvin > ColorTemperatureConverter.MAX_KELVIN) {
            return null;
        }
        return kelvin + "K";
    }
}
