package com.particlesdevs.photoncamera.settings;

import android.content.Context;
import android.text.InputType;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;

import com.particlesdevs.photoncamera.util.Log;
import com.particlesdevs.photoncamera.settings.annotations.Tunable;
import com.particlesdevs.photoncamera.ui.settings.custompreferences.TunableCheckBoxPreference;
import com.particlesdevs.photoncamera.ui.settings.custompreferences.TunableListPreference;
import com.particlesdevs.photoncamera.ui.settings.custompreferences.TunablePngPreference;
import com.particlesdevs.photoncamera.ui.settings.custompreferences.TunableSeekBarPreference;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Automatically generates preference UI from @Tunable annotations.
 * Scans all classes with @Tunable fields and creates preference entries.
 */
public class TunablePreferenceGenerator {
    private static final String TAG = "TunablePrefGen";
    
    // List of classes to scan for @Tunable annotations
    private static final List<Class<?>> TUNABLE_CLASSES = new ArrayList<>();
    
    /**
     * Register a class that contains @Tunable fields
     */
    public static void registerTunableClass(Class<?> clazz) {
        if (!TUNABLE_CLASSES.contains(clazz)) {
            TUNABLE_CLASSES.add(clazz);
            Log.d(TAG, "Registered tunable class: " + clazz.getSimpleName() + ", total classes: " + TUNABLE_CLASSES.size());
        } else {
            Log.w(TAG, "Class already registered: " + clazz.getSimpleName());
        }
    }
    
    /**
     * Generate and add preferences to the preference screen.
     * ONLY adds to tunable submenu - won't pollute main settings.
     */
    public static void generatePreferences(Context context, PreferenceScreen preferenceScreen) {
        // Try to find the tunable submenu
        PreferenceScreen tunableSubmenu = preferenceScreen.findPreference("pref_tunable_submenu");
        
        if (tunableSubmenu == null) {
            Log.w(TAG, "Tunable submenu not found! Tunable preferences will not be generated.");
            return;
        }
        
        Log.d(TAG, "Target screen: Tunable Submenu (found!)");
        try {
            Log.d(TAG, "=== generatePreferences START ===");
            Log.d(TAG, "TUNABLE_CLASSES size: " + TUNABLE_CLASSES.size());
            Log.d(TAG, "Context: " + (context != null ? "OK" : "NULL"));
            Log.d(TAG, "PreferenceScreen: " + (tunableSubmenu != null ? "OK" : "NULL"));
            
            if (TUNABLE_CLASSES.isEmpty()) {
                Log.w(TAG, "No tunable classes registered - UI will not be generated!");
                return;
            }
            
            // Group preferences by category
            Map<String, List<TunableFieldInfo>> categorizedFields = new HashMap<>();
            
            // Scan all registered classes
            for (Class<?> clazz : TUNABLE_CLASSES) {
                Log.d(TAG, "About to scan class: " + clazz.getName());
                scanClass(clazz, categorizedFields);
            }
            
            Log.d(TAG, "Found " + categorizedFields.size() + " categories");
            
            // Create preference categories and add preferences
            for (Map.Entry<String, List<TunableFieldInfo>> entry : categorizedFields.entrySet()) {
                String categoryName = entry.getKey();
                List<TunableFieldInfo> fields = entry.getValue();
                
                Log.d(TAG, "Processing category: " + categoryName + " with " + fields.size() + " fields");
                
                // Sort by order
                fields.sort((a, b) -> Integer.compare(a.order, b.order));
                
                // Find or create category in the tunable submenu
                PreferenceCategory category = findOrCreateCategory(context, tunableSubmenu, categoryName);
                Log.d(TAG, "Category created/found: " + categoryName);
                
                // Add preferences for each field
                for (TunableFieldInfo fieldInfo : fields) {
                    //Log.d(TAG, "Adding preference for: " + fieldInfo.fieldName);
                    addPreference(context, category, fieldInfo);
                }
            }
            
            Log.d(TAG, "=== generatePreferences COMPLETE - Generated preferences for " + TUNABLE_CLASSES.size() + " classes ===");
        } catch (Exception e) {
            Log.e(TAG, "ERROR in generatePreferences: " + e.getMessage(), e);
            e.printStackTrace();
        }
    }
    
    private static void scanClass(Class<?> clazz, Map<String, List<TunableFieldInfo>> categorizedFields) {
        String className = clazz.getSimpleName();
        Log.d(TAG, "Scanning class: " + className);
        
        Field[] fields = clazz.getDeclaredFields();
        Log.d(TAG, "Found " + fields.length + " fields in " + className);
        
        int fieldOrder = 0; // Track field declaration order
        
        for (Field field : fields) {
            //Log.d(TAG, "Checking field: " + field.getName() + ", has annotation: " + field.isAnnotationPresent(Tunable.class));
            if (field.isAnnotationPresent(Tunable.class)) {
                Tunable annotation = field.getAnnotation(Tunable.class);
                if (annotation == null) {
                    Log.w(TAG, "Field " + field.getName() + " has @Tunable but annotation is null!");
                    continue;
                }
                
                TunableFieldInfo info = new TunableFieldInfo();
                info.className = className;
                info.classType = clazz;
                info.fieldName = field.getName();
                info.fieldType = field.getType();
                info.annotation = annotation;
                info.order = fieldOrder++; // Use declaration order
                
                String category = annotation.category();
                if (!categorizedFields.containsKey(category)) {
                    categorizedFields.put(category, new ArrayList<>());
                }
                categorizedFields.get(category).add(info);
                
                //Log.d(TAG, "Found tunable field: " + className + "." + field.getName() + " (order: " + info.order + ")");
            }
        }
    }
    
    private static PreferenceCategory findOrCreateCategory(Context context, PreferenceScreen screen, String categoryName) {
        // Try to find existing category
        String categoryKey = "pref_category_tunable_" + categoryName.toLowerCase().replace(" ", "_");
        
        for (int i = 0; i < screen.getPreferenceCount(); i++) {
            if (screen.getPreference(i) instanceof PreferenceCategory) {
                PreferenceCategory cat = (PreferenceCategory) screen.getPreference(i);
                if (categoryKey.equals(cat.getKey())) {
                    return cat;
                }
            }
        }
        
        // Create new category
        PreferenceCategory category = new PreferenceCategory(context);
        category.setKey(categoryKey);
        category.setTitle("Tunable - " + categoryName);
        screen.addPreference(category);
        
        return category;
    }
    
    private static void addPreference(Context context, PreferenceCategory category, TunableFieldInfo info) {
        Tunable annotation = info.annotation;
        String prefKey = "pref_tunable_" + info.className.toLowerCase() + "_" + info.fieldName.toLowerCase();

        // String fields with entries/entryValues become a list selector
        if (info.fieldType == String.class
                && annotation.entries().length > 0
                && annotation.entryValues().length > 0) {
            addListPreference(context, category, prefKey, info);
            return;
        }

        // step == 0 means plain text input without a slider
        if (annotation.step() == 0f) {
            addFreeTextPreference(context, category, prefKey, info);
            return;
        }

        // Check if this should be a checkbox (min=0, max=1, step=1)
        float min = annotation.min();
        float max = annotation.max();
        float step = annotation.step();
        boolean isCheckbox = (min == 0.0f && max == 1.0f && step == 1.0f);
        
        // Create appropriate preference based on type
        if (info.fieldType == float.class || info.fieldType == Float.class ||
            info.fieldType == double.class || info.fieldType == Double.class ||
            info.fieldType == int.class || info.fieldType == Integer.class ||
            info.fieldType == boolean.class || info.fieldType == Boolean.class) {

            // Use checkbox for boolean-like tunables (min=0, max=1, step=1)
            if (isCheckbox) {
                TunableCheckBoxPreference checkBox = new TunableCheckBoxPreference(context);
                checkBox.setKey(prefKey);
                checkBox.setTitle(annotation.title());

                if (!annotation.description().isEmpty()) {
                    checkBox.setSummary(annotation.description());
                }

                // Get default value and convert to int (0 or 1)
                float defaultValue = getFieldDefaultValue(info);
                int intDefault = (defaultValue != 0.0f) ? 1 : 0;
                checkBox.setDefaultValue(intDefault);

                category.addPreference(checkBox);

                Log.d(TAG, "Added checkbox preference: " + prefKey + " with default: " + intDefault);
            } else {
                // Create seekbar preference for other numeric types
                TunableSeekBarPreference seekBar = new TunableSeekBarPreference(context);
                seekBar.setKey(prefKey);
                seekBar.setTitle(annotation.title());

                if (!annotation.description().isEmpty()) {
                    seekBar.setSummary(annotation.description());
                }

                // Set min/max/step
                seekBar.setMinValue(annotation.min());
                seekBar.setMaxValue(annotation.max());

                // Auto-detect if float based on step value
                // If step has decimals (not a whole number), treat as float
                boolean isFloat = (step != Math.floor(step));

                // IMPORTANT: Set isFloat BEFORE setDefaultValue so precision is calculated correctly
                seekBar.setIsFloat(isFloat);

                // Calculate step per unit (for seekbar)
                float range = annotation.max() - annotation.min();
                int stepsPerUnit = (int) (1.0f / step);
                seekBar.setStepPerUnit(Math.max(1, stepsPerUnit));

                //Log.d(TAG, "Field " + info.fieldName + " - step: " + step + ", isFloat: " + isFloat + ", stepsPerUnit: " + stepsPerUnit);

                // Get default value from the actual field
                float defaultValue = getFieldDefaultValue(info);

                // Set default value AFTER isFloat and stepPerUnit are set
                seekBar.setDefaultValue(defaultValue);
                //Log.d(TAG, "Set default value for " + prefKey + ": " + defaultValue);

                // No icons for tunable preferences - keep UI clean and simple

                category.addPreference(seekBar);

                //Log.d(TAG, "Added seekbar preference: " + prefKey + " with default: " + defaultValue);
            }
        } else if (info.fieldType == java.io.File.class) {
            // Create PNG preference for File type fields
            TunablePngPreference pngPref = new TunablePngPreference(context);
            pngPref.setKey(prefKey);
            pngPref.setTitle(annotation.title());
            pngPref.setAllowedPngSizes(annotation.allowedPngSizes());

            if (!annotation.description().isEmpty()) {
                pngPref.setSummary(annotation.description());
            } else {
                pngPref.setSummary("None selected");
            }

            category.addPreference(pngPref);

            Log.d(TAG, "Added PNG preference: " + prefKey);
        }
    }
    
    /**
     * Creates a plain text input preference (no slider) when the annotation step is 0.
     * The value is stored as a String and parsed back to the field type on injection.
     */
    private static void addFreeTextPreference(Context context, PreferenceCategory category, String prefKey, TunableFieldInfo info) {
        Tunable annotation = info.annotation;
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
        Log.d(TAG, "Added free text preference: " + prefKey);
    }

    /**
     * Creates a ListPreference (dropdown/dialog list) when the annotation provides entries and entryValues.
     * Supports long press to reset to default.
     */
    private static void addListPreference(Context context, PreferenceCategory category, String prefKey, TunableFieldInfo info) {
        Tunable annotation = info.annotation;
        TunableListPreference listPref = new TunableListPreference(context);
        listPref.setKey(prefKey);
        listPref.setTitle(annotation.title());
        listPref.setDialogTitle(annotation.title());

        listPref.setEntries(annotation.entries());
        listPref.setEntryValues(annotation.entryValues());

        // Resolve default from annotation defaultValue: numeric match first, then index
        String defaultValue = null;
        float def = annotation.defaultValue();
        if (def != -999999f) {
            for (String val : annotation.entryValues()) {
                try {
                    if (Float.parseFloat(val) == def) {
                        defaultValue = val;
                        break;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            if (defaultValue == null && def >= 0 && def < annotation.entryValues().length) {
                defaultValue = annotation.entryValues()[(int) def];
            }
        }
        if (defaultValue == null) defaultValue = annotation.entryValues()[0];
        listPref.setDefaultValue(defaultValue);
        listPref.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());

        category.addPreference(listPref);
        Log.d(TAG, "Added list preference: " + prefKey + " with default: " + defaultValue);
    }

    private static String formatDefault(float defaultValue, Class<?> fieldType) {
        if (fieldType == float.class || fieldType == Float.class
                || fieldType == double.class || fieldType == Double.class) {
            return String.valueOf(defaultValue);
        }
        return String.valueOf((int) defaultValue);
    }

    private static float getFieldDefaultValue(TunableFieldInfo info) {
        // Check if default value is specified in annotation
        float annotationDefault = info.annotation.defaultValue();
        if (annotationDefault != -999999f) {
            //Log.d(TAG, "Using annotation default for " + info.fieldName + ": " + annotationDefault);
            return annotationDefault;
        }
        
        // Try to read from static field analysis (fallback)
        Log.w(TAG, "No default value in annotation for " + info.fieldName + ", using min value: " + info.annotation.min());
        return info.annotation.min(); // Fallback to min value
    }
    
    private static class TunableFieldInfo {
        String className;
        Class<?> classType;
        String fieldName;
        Class<?> fieldType;
        Tunable annotation;
        int order;
    }
}

