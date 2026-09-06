package com.particlesdevs.photoncamera.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.InputType;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

import com.google.gson.Gson;
import com.particlesdevs.photoncamera.api.VendorTagUtils;
import com.particlesdevs.photoncamera.ui.camera.data.CameraLensData;
import com.particlesdevs.photoncamera.ui.settings.custompreferences.TunableKeyDialog;
import com.particlesdevs.photoncamera.ui.settings.custompreferences.TunableKeyPreference;
import com.particlesdevs.photoncamera.util.Log;
import com.particlesdevs.photoncamera.settings.annotations.SensorConfig;
import com.particlesdevs.photoncamera.ui.settings.custompreferences.TunableSeekBarPreference;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Automatically generates per-physical-sensor preference UI from {@code @SensorConfig} annotations.
 * A selector on top lets the user pick which physical camera to configure; only that sensor's
 * sliders are shown.
 */
public class SensorConfigPreferenceGenerator {
    private static final String TAG = "SensorConfigPrefGen";

    private static final String SUBMENU_KEY = "pref_sensor_config_submenu";
    private static final String SELECTOR_KEY = "pref_sensor_config_selector";

    private SensorConfigPreferenceGenerator() {}

    /**
     * Generate and add per-sensor preferences to the preference screen.
     */
    public static void generatePreferences(Context context, PreferenceScreen preferenceScreen) {
        PreferenceScreen submenu = preferenceScreen.findPreference(SUBMENU_KEY);
        if (submenu == null) {
            Log.w(TAG, "Sensor config submenu not found! Preferences will not be generated.");
            return;
        }

        try {
            List<String> physicalIds = getSortedPhysicalIds();
            if (physicalIds.isEmpty()) {
                Log.w(TAG, "No camera ids found, cannot generate sensor config preferences.");
                addNoSensorsPreference(context, submenu);
                return;
            }

            List<TunableFieldInfo> fields = scanFields();
            if (fields.isEmpty()) {
                Log.w(TAG, "No @SensorConfig fields found - UI will not be generated!");
                return;
            }

            Map<String, CameraLensData> lensMap = getCameraLensMap();

            ListPreference selector = createSelector(context, submenu, physicalIds, lensMap);
            Map<String, PreferenceCategory> categories = createCategories(context, submenu, physicalIds, lensMap, fields);

            String selected = resolveSelected(context, selector, physicalIds);
            updateVisibility(selector, categories, selected);

            selector.setOnPreferenceChangeListener((preference, newValue) -> {
                String sel = newValue != null ? newValue.toString() : null;
                updateVisibility(selector, categories, sel);
                return true;
            });

            Log.d(TAG, "Generated sensor config preferences for " + physicalIds.size() + " sensors, " + fields.size() + " fields");
        } catch (Exception e) {
            Log.e(TAG, "ERROR in generatePreferences: " + Log.getStackTraceString(e));
        }
    }

    private static ListPreference createSelector(Context context, PreferenceScreen submenu, List<String> physicalIds, Map<String, CameraLensData> lensMap) {
        ListPreference selector = new ListPreference(context);
        selector.setKey(SELECTOR_KEY);
        selector.setTitle("Sensor");
        selector.setDialogTitle("Select Sensor");

        CharSequence[] entries = new CharSequence[physicalIds.size()];
        CharSequence[] entryValues = new CharSequence[physicalIds.size()];
        for (int i = 0; i < physicalIds.size(); i++) {
            String pid = physicalIds.get(i);
            entries[i] = buildCategoryTitle(pid, lensMap.get(pid));
            entryValues[i] = pid;
        }
        selector.setEntries(entries);
        selector.setEntryValues(entryValues);
        selector.setDefaultValue(physicalIds.get(0));
        submenu.addPreference(selector);
        return selector;
    }

    private static Map<String, PreferenceCategory> createCategories(Context context, PreferenceScreen submenu,
                                                                    List<String> physicalIds, Map<String, CameraLensData> lensMap,
                                                                    List<TunableFieldInfo> fields) {
        Map<String, PreferenceCategory> categories = new LinkedHashMap<>();
        for (String physicalId : physicalIds) {
            PreferenceCategory category = findOrCreateCategory(context, submenu, physicalId, lensMap.get(physicalId));
            for (TunableFieldInfo info : fields) {
                addPreference(context, category, physicalId, info);
            }
            addTunableKeySection(context, category, physicalId);
            categories.put(physicalId, category);
        }
        return categories;
    }

    private static void addTunableKeySection(Context context, PreferenceCategory category, String physicalId) {
        Runnable refresh = () -> refreshTunableKeys(context, category, physicalId);

        List<VendorTagUtils.TunableKey> keys = TunableKeyManager.loadKeys(context, physicalId);
        for (int i = 0; i < keys.size(); i++) {
            TunableKeyPreference keyPref = new TunableKeyPreference(context, physicalId, i, refresh);
            keyPref.setOrder(100 + i);
            category.addPreference(keyPref);
        }

        androidx.preference.Preference addButton = new androidx.preference.Preference(context);
        addButton.setKey("pref_sensorconfig_" + physicalId + "_add_tunablekey");
        addButton.setLayoutResource(com.particlesdevs.photoncamera.R.layout.preference_with_margin);
        addButton.setTitle("+ Add Tunable Key");
        addButton.setSummary("Add a custom vendor tag for this sensor (type, name, value)");
        addButton.setIcon(com.particlesdevs.photoncamera.R.drawable.ic_add);
        addButton.setOrder(10000);
        addButton.setOnPreferenceClickListener(preference -> {
            TunableKeyDialog.show(context, physicalId, -1, refresh);
            return true;
        });
        category.addPreference(addButton);
    }

    private static void refreshTunableKeys(Context context, PreferenceCategory category, String physicalId) {
        List<androidx.preference.Preference> toRemove = new ArrayList<>();
        for (int i = 0; i < category.getPreferenceCount(); i++) {
            androidx.preference.Preference p = category.getPreference(i);
            if (p instanceof TunableKeyPreference || ("pref_sensorconfig_" + physicalId + "_add_tunablekey").equals(p.getKey())) {
                toRemove.add(p);
            }
        }
        for (androidx.preference.Preference p : toRemove) {
            category.removePreference(p);
        }
        addTunableKeySection(context, category, physicalId);
    }

    private static String resolveSelected(Context context, ListPreference selector, List<String> physicalIds) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String selected = prefs.getString(selector.getKey(), physicalIds.get(0));
        if (!physicalIds.contains(selected)) {
            selected = physicalIds.get(0);
        }
        return selected;
    }

    private static void updateVisibility(ListPreference selector, Map<String, PreferenceCategory> categories, String selected) {
        if (selected == null) selected = categories.keySet().iterator().next();
        for (Map.Entry<String, PreferenceCategory> entry : categories.entrySet()) {
            entry.getValue().setVisible(selected.equals(entry.getKey()));
        }
        if (selector != null) {
            PreferenceCategory category = categories.get(selected);
            selector.setSummary(category != null && category.getTitle() != null
                    ? category.getTitle().toString() : selected);
        }
    }

    private static void addNoSensorsPreference(Context context, PreferenceScreen submenu) {
        try {
            androidx.preference.Preference info = new androidx.preference.Preference(context);
            info.setKey("pref_sensor_config_no_sensors");
            info.setTitle("No sensors found");
            info.setSummary("Camera ids have not been scanned yet. Open the camera once and return to this menu.");
            info.setSelectable(false);
            submenu.addPreference(info);
        } catch (Exception e) {
            Log.e(TAG, "Failed to add no-sensors message: " + Log.getStackTraceString(e));
        }
    }

    /**
     * Resolve the sorted list of available physical camera ids from the scanned cameras preference.
     */
    private static List<String> getSortedPhysicalIds() {
        Set<String> sensorIds = getAvailableSensorIds();
        if (sensorIds == null || sensorIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> physicalIds = new ArrayList<>();
        for (String id : sensorIds) {
            String physicalId = toPhysicalId(id);
            if (!physicalIds.contains(physicalId)) {
                physicalIds.add(physicalId);
            }
        }
        physicalIds.sort((a, b) -> {
            try {
                return Integer.compare(Integer.parseInt(a), Integer.parseInt(b));
            } catch (NumberFormatException e) {
                return a.compareTo(b);
            }
        });
        return physicalIds;
    }

    /**
     * Resolve the set of available physical camera ids from the scanned cameras preference.
     */
    private static Set<String> getAvailableSensorIds() {
        try {
            SettingsManager settingsManager = com.particlesdevs.photoncamera.app.PhotonCamera.getSettingsManagerStatic();
            if (settingsManager == null) {
                Log.w(TAG, "SettingsManager is null, cannot read camera ids");
                return null;
            }
            Set<String> ids = settingsManager.getStringSet(
                    PreferenceKeys.Key.CAMERAS_PREFERENCE_FILE_NAME.mValue,
                    PreferenceKeys.Key.ALL_CAMERA_IDS_KEY, null);
            if (ids == null) return null;
            java.util.LinkedHashSet<String> physical = new java.util.LinkedHashSet<>();
            for (String id : ids) {
                physical.add(toPhysicalId(id));
            }
            return physical;
        } catch (Exception e) {
            Log.e(TAG, "Failed to read camera ids: " + Log.getStackTraceString(e));
            return null;
        }
    }

    private static List<TunableFieldInfo> scanFields() {
        List<TunableFieldInfo> fields = new ArrayList<>();
        for (Class<?> clazz : SensorConfigRegistry.SENSOR_CONFIG_CLASSES) {
            scanClass(clazz, fields);
        }
        return fields;
    }

    /**
     * Strip the logical part of a "logical-physical" id (e.g. "0-1" -> "1").
     */
    public static String toPhysicalId(String id) {
        if (id != null && id.contains("-")) {
            return id.split("-")[1];
        }
        return id;
    }

    private static void scanClass(Class<?> clazz, List<TunableFieldInfo> fields) {
        String className = clazz.getSimpleName();
        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(SensorConfig.class)) {
                SensorConfig annotation = field.getAnnotation(SensorConfig.class);
                if (annotation == null) continue;

                TunableFieldInfo info = new TunableFieldInfo();
                info.className = className;
                info.fieldName = field.getName();
                info.fieldType = field.getType();
                info.annotation = annotation;
                fields.add(info);
            }
        }
    }

    private static PreferenceCategory findOrCreateCategory(Context context, PreferenceScreen screen, String sensorId, CameraLensData lens) {
        String categoryKey = "pref_category_sensor_" + sensorId;

        for (int i = 0; i < screen.getPreferenceCount(); i++) {
            if (screen.getPreference(i) instanceof PreferenceCategory) {
                PreferenceCategory cat = (PreferenceCategory) screen.getPreference(i);
                if (categoryKey.equals(cat.getKey())) {
                    return cat;
                }
            }
        }

        PreferenceCategory category = new PreferenceCategory(context);
        category.setKey(categoryKey);
        category.setTitle(buildCategoryTitle(sensorId, lens));
        screen.addPreference(category);

        return category;
    }

    private static String buildCategoryTitle(String physicalId, CameraLensData lens) {
        StringBuilder title = new StringBuilder("Sensor ").append(physicalId);
        if (lens != null) {
            if (lens.getCameraId() != null) {
                title.append(" \u00b7 ID ").append(lens.getCameraId());
            }
            title.append(" \u00b7 ").append(String.format(Locale.ROOT, "%.1fx", lens.getZoomFactor()));
        }
        return title.toString();
    }

    /**
     * Load a map of physical id -> lens data from the scanned cameras preference.
     */
    private static Map<String, CameraLensData> getCameraLensMap() {
        Map<String, CameraLensData> lensMap = new HashMap<>();
        try {
            SettingsManager settingsManager = com.particlesdevs.photoncamera.app.PhotonCamera.getSettingsManagerStatic();
            if (settingsManager == null) return lensMap;
            Set<String> jsonSet = settingsManager.getStringSet(
                    PreferenceKeys.Key.CAMERAS_PREFERENCE_FILE_NAME.mValue,
                    PreferenceKeys.Key.ALL_CAMERA_LENS_KEY, null);
            if (jsonSet == null) return lensMap;
            Gson gson = new Gson();
            for (String json : jsonSet) {
                try {
                    CameraLensData data = gson.fromJson(json, CameraLensData.class);
                    if (data != null && data.getCameraId() != null) {
                        lensMap.put(toPhysicalId(data.getCameraId()), data);
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to read camera lens data: " + Log.getStackTraceString(e));
        }
        return lensMap;
    }

    private static void addPreference(Context context, PreferenceCategory category, String sensorId, TunableFieldInfo info) {
        // Skip OIS configuration for sensors that do not physically support hardware OIS
        if ("oisMode".equalsIgnoreCase(info.fieldName) && !isOisSupported(context, sensorId)) {
            return;
        }

        SensorConfig annotation = info.annotation;
        String prefKey = "pref_sensorconfig_" + sensorId + "_" + info.fieldName.toLowerCase();

        if (annotation.entries().length > 0 && annotation.entryValues().length > 0) {
            // Heal preference type in background if corrupted by config import
            ensureStringPreference(context, prefKey);
            addListPreference(context, category, prefKey, info);
            return;
        }

        if (annotation.step() == 0f) {
            // Heal preference type in background if corrupted by config import
            ensureStringPreference(context, prefKey);
            addFreeTextPreference(context, category, prefKey, info);
            return;
        }

        TunableSeekBarPreference seekBar = new TunableSeekBarPreference(context);
        seekBar.setKey(prefKey);
        seekBar.setTitle(annotation.title());

        if (!annotation.description().isEmpty()) {
            seekBar.setSummary(annotation.description());
        }

        seekBar.setMinValue(annotation.min());
        seekBar.setMaxValue(annotation.max());

        float step = annotation.step();
        boolean isFloat = (step != Math.floor(step));
        seekBar.setIsFloat(isFloat);

        int stepsPerUnit = (int) (1.0f / step);
        seekBar.setStepPerUnit(Math.max(1, stepsPerUnit));

        float defaultValue = getFieldDefaultValue(info);
        seekBar.setDefaultValue(defaultValue);

        category.addPreference(seekBar);
    }

    private static float getFieldDefaultValue(TunableFieldInfo info) {
        float annotationDefault = info.annotation.defaultValue();
        if (annotationDefault != -999999f) {
            return annotationDefault;
        }
        return info.annotation.min();
    }

    /**
     * Creates a plain text input preference (no slider) when the annotation step is 0.
     * The value is stored as a String and parsed back to the field type on injection.
     */
    private static void addFreeTextPreference(Context context, PreferenceCategory category, String prefKey, TunableFieldInfo info) {
        SensorConfig annotation = info.annotation;
        float defaultValue = getFieldDefaultValue(info);
        Class<?> fieldType = info.fieldType;

        EditTextPreference editText = new EditTextPreference(context);
        editText.setKey(prefKey);
        editText.setTitle(annotation.title());
        editText.setDialogTitle(annotation.title());
        editText.setIconSpaceReserved(false);

        int inputType;
        if (fieldType == float.class || fieldType == Float.class
                || fieldType == double.class || fieldType == Double.class) {
            inputType = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED | InputType.TYPE_NUMBER_FLAG_DECIMAL;
        } else if (fieldType == int.class || fieldType == Integer.class
                || fieldType == long.class || fieldType == Long.class
                || fieldType == short.class || fieldType == Short.class
                || fieldType == byte.class || fieldType == Byte.class) {
            inputType = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED;
        } else {
            inputType = InputType.TYPE_CLASS_TEXT;
        }

        editText.setOnBindEditTextListener(edit -> {
            edit.setInputType(inputType);
            if (editText.getText() == null) {
                edit.setText(formatDefault(defaultValue, fieldType));
            }
        });

        String description = annotation.description();
        editText.setSummaryProvider(preference -> {
            String value = editText.getText();
            String display = value != null ? value : formatDefault(defaultValue, fieldType);
            return description.isEmpty() ? display : description + "\n" + display;
        });

        category.addPreference(editText);
    }

    /**
     * Creates a ListPreference (dropdown/dialog list) when the annotation provides entries and entryValues.
     */
    private static void addListPreference(Context context, PreferenceCategory category, String prefKey, TunableFieldInfo info) {
        SensorConfig annotation = info.annotation;
        ListPreference listPref = new ListPreference(context);
        listPref.setKey(prefKey);
        listPref.setTitle(annotation.title());
        listPref.setDialogTitle(annotation.title());

        listPref.setEntries(annotation.entries());
        listPref.setEntryValues(annotation.entryValues());

        // Resolve default value from annotation defaultValue
        String defaultValue = annotation.entryValues()[0];
        if (annotation.defaultValue() != -999999f) {
            float def = annotation.defaultValue();
            for (String val : annotation.entryValues()) {
                try {
                    if (Float.parseFloat(val) == def) {
                        defaultValue = val;
                        break;
                    }
                } catch (NumberFormatException ignored) {
                    if (val.equals(String.valueOf(def))) {
                        defaultValue = val;
                        break;
                    }
                }
            }
        }
        listPref.setDefaultValue(defaultValue);
        listPref.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());

        category.addPreference(listPref);
    }

    /**
     * Checks if the physical camera sensor supports hardware Optical Image Stabilization (OIS).
     * Delegates to {@link VendorTagUtils#isOisSupported(Context, android.hardware.camera2.CameraCharacteristics, String)}.
     */
    private static boolean isOisSupported(Context context, String sensorId) {
        if (VendorTagUtils.isOisSupported(context, null, sensorId)) {
            Log.d(TAG, "Sensor " + sensorId + " OIS is supported");
            return true;
        }
        Log.d(TAG, "Sensor " + sensorId + " OIS is NOT supported");
        return false;
    }

    private static String formatDefault(float defaultValue, Class<?> fieldType) {
        if (fieldType == float.class || fieldType == Float.class
                || fieldType == double.class || fieldType == Double.class) {
            return String.valueOf(defaultValue);
        }
        return String.valueOf((int) defaultValue);
    }

    private static class TunableFieldInfo {
        String className;
        String fieldName;
        Class<?> fieldType;
        SensorConfig annotation;
    }
    /**
     * Inspects the SharedPreferences entry for the given key and ensures it is stored 
     * as a String. If it contains a mismatched type (like Integer due to config import), 
     * it automatically heals it on the fly to prevent ClassCastException in ListPreference/EditTextPreference.
     */
    private static void ensureStringPreference(Context context, String key) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (prefs.contains(key)) {
            try {
                // Try reading as a String to verify correct type
                prefs.getString(key, null);
            } catch (ClassCastException e) {
                Log.w(TAG, "Mismatched type detected for key: " + key + ". Healing to String.");
                try {
                    // Attempt to heal if stored as Integer
                    int intVal = prefs.getInt(key, -999999);
                    if (intVal != -999999) {
                        prefs.edit().putString(key, String.valueOf(intVal)).apply();
                        return;
                    }
                } catch (Exception ignored) {}
                try {
                    // Attempt to heal if stored as Float
                    float floatVal = prefs.getFloat(key, -999999f);
                    if (floatVal != -999999f) {
                        prefs.edit().putString(key, String.valueOf(floatVal)).apply();
                        return;
                    }
                } catch (Exception ignored) {}
                try {
                    // Attempt to heal if stored as Boolean
                    boolean boolVal = prefs.getBoolean(key, false);
                    prefs.edit().putString(key, boolVal ? "1" : "0").apply();
                } catch (Exception ignored) {}
            }
        }
    }
}