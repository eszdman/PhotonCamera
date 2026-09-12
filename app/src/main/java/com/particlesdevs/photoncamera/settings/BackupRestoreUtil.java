package com.particlesdevs.photoncamera.settings;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.util.FileManager;
import com.particlesdevs.photoncamera.util.Log;
import com.particlesdevs.photoncamera.util.SimpleStorageHelper;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class BackupRestoreUtil {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    /**
     * Export all settings to JSON format, including per-lens settings
     */
    public static String backupSettings(Context context, String fileName) {
        try {
            if (fileName.equals("")) {
                throw new IOException(context.getString(R.string.empty_file_name_error));
            }
            
            // Ensure tunable classes are registered before export
            // This is necessary even if the tunable settings screen was never opened
            TunableSettingsManager.ensureTunableClassesRegistered();
            
            // Create the export data structure
            Map<String, Object> exportData = new HashMap<>();
            
            // Get all SharedPreferences files
            String packageName = context.getPackageName();
            
            // Export main preferences (excluding per-lens keys and tunable settings)
            SharedPreferences mainPrefs = PreferenceManager.getDefaultSharedPreferences(context);
            Map<String, ?> mainPrefsMap = mainPrefs.getAll();
            // Filter out tunable settings - they are exported separately in tunable_settings section
            Map<String, Object> filteredMainPrefs = new HashMap<>();
            for (Map.Entry<String, ?> entry : mainPrefsMap.entrySet()) {
                String key = entry.getKey();
                if (key != null && !key.startsWith("pref_tunable_")) {
                    filteredMainPrefs.put(key, entry.getValue());
                }
            }
            exportData.put("main_preferences", filteredMainPrefs);
            
            // Export per-lens settings as an array (more human-readable)
            String perLensFileName = context.getString(R.string._per_lens);
            SharedPreferences perLensPrefs = context.getSharedPreferences(packageName + perLensFileName, Context.MODE_PRIVATE);
            Map<String, ?> perLensPrefsMap = perLensPrefs.getAll();
            
            if (!perLensPrefsMap.isEmpty()) {
                java.util.List<Map<String, Object>> perLensArray = new java.util.ArrayList<>();
                
                for (Map.Entry<String, ?> entry : perLensPrefsMap.entrySet()) {
                    String key = entry.getKey();
                    // Keys are like "settings_for_camera_0", extract the camera ID
                    if (key.startsWith("settings_for_camera_")) {
                        String cameraId = key.substring("settings_for_camera_".length());
                        
                        Map<String, Object> cameraSettings = new HashMap<>();
                        cameraSettings.put("id", cameraId);
                        
                        // Parse the JSON string to a map for better readability
                        String jsonString = entry.getValue().toString();
                        try {
                            Map<String, ?> settingsMap = GSON.fromJson(jsonString, HashMap.class);
                            cameraSettings.put("settings", settingsMap);
                        } catch (Exception e) {
                            // If parsing fails, store as string
                            cameraSettings.put("settings", jsonString);
                        }
                        
                        perLensArray.add(cameraSettings);
                    }
                }
                
                if (!perLensArray.isEmpty()) {
                    exportData.put("per_lens_settings", perLensArray);
                }
            }
            
            // Export tunable settings (only non-default values)
            Map<String, Object> tunableSettings = TunableSettingsManager.exportTunableSettings(context);
            if (!tunableSettings.isEmpty()) {
                exportData.put("tunable_settings", tunableSettings);
            }
            
            // Add metadata
            Map<String, String> metadata = new HashMap<>();
            metadata.put("version", "2.1"); // Increment version to include tunable settings
            metadata.put("format", "json");
            metadata.put("timestamp", String.valueOf(System.currentTimeMillis()));
            exportData.put("metadata", metadata);
            
            String nameWithExt = fileName.endsWith(".json") ? fileName : fileName.concat(".json");
            if (SimpleStorageHelper.hasStorageAccess(context)) {
                try (OutputStream os = SimpleStorageHelper.openOutputStream(context, nameWithExt);
                     OutputStreamWriter writer = new OutputStreamWriter(os, StandardCharsets.UTF_8)) {
                    GSON.toJson(exportData, writer);
                }
                return "Saved: DCIM/PhotonCamera/" + nameWithExt;
            } else {
                File toSave = new File(FileManager.sPHOTON_DIR, nameWithExt);
                try (FileWriter writer = new FileWriter(toSave)) {
                    GSON.toJson(exportData, writer);
                }
                return "Saved: " + toSave.getAbsolutePath();
            }
        } catch (IOException e) {
            e.printStackTrace();
            return "Error: " + e.getLocalizedMessage();
        } catch (Exception e) {
            e.printStackTrace();
            return "Error: " + e.getLocalizedMessage();
        }
    }

    /**
     * Import settings from JSON or XML format
     * Supports both new JSON format and legacy XML format.
     * Uses SimpleStorage (DCIM/PhotonCamera) when available, else legacy File path.
     */
    public static String restorePreferences(Context context, String fileName) {
        try {
            if (SimpleStorageHelper.hasStorageAccess(context)) {
                try (InputStream is = SimpleStorageHelper.openInputStream(context, fileName)) {
                    if (fileName.endsWith(".json")) {
                        return restoreFromJsonStream(context, is, fileName);
                    } else if (fileName.endsWith(".xml")) {
                        return restoreFromXmlStream(context, is, fileName);
                    } else {
                        return "Error: Unknown file format. Use .json or .xml";
                    }
                }
            }
        } catch (Exception e) {
            Log.e("BackupRestoreUtil", "Restore (SAF) error: " + Log.getStackTraceString(e));
            return "Error: " + e.getLocalizedMessage();
        }
        
        File toRestore = new File(FileManager.sPHOTON_DIR, fileName);
        if (!toRestore.exists()) {
            return "Error: File not found";
        }
        try {
            if (fileName.endsWith(".json")) {
                return restoreFromJson(context, toRestore);
            } else if (fileName.endsWith(".xml")) {
                return restoreFromXml(context, toRestore);
            } else {
                return "Error: Unknown file format. Use .json or .xml";
            }
        } catch (Exception e) {
            Log.e("BackupRestoreUtil", "Restore error: " + Log.getStackTraceString(e));
            return "Error: " + e.getLocalizedMessage();
        }
    }
    
    /**
     * Restore from JSON input stream (e.g. from SimpleStorage DocumentFile).
     */
    private static String restoreFromJsonStream(Context context, InputStream is, String fileName) throws IOException {
        TunableSettingsManager.ensureTunableClassesRegistered();
        try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            applyRestoredJson(context, root);
            PhotonCamera.restartWithDelay(context, 1000);
            return "Restored from JSON: " + fileName;
        }
    }
    
    /**
     * Restore from new JSON format
     */
    private static String restoreFromJson(Context context, File jsonFile) throws IOException {
        TunableSettingsManager.ensureTunableClassesRegistered();
        try (FileReader reader = new FileReader(jsonFile)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            applyRestoredJson(context, root);
            PhotonCamera.restartWithDelay(context, 1000);
            return "Restored from JSON: " + jsonFile.getName();
        }
    }
    
    private static void applyRestoredJson(Context context, JsonObject root) {
        String packageName = context.getPackageName();
        if (root.has("main_preferences")) {
            SharedPreferences mainPrefs = PreferenceManager.getDefaultSharedPreferences(context);
            SharedPreferences.Editor editor = mainPrefs.edit();
            editor.clear();
            JsonObject mainPrefsObj = root.getAsJsonObject("main_preferences");
            for (String key : mainPrefsObj.keySet()) {
                putJsonValueToEditor(editor, key, mainPrefsObj.get(key));
            }
            editor.apply();
        }
        if (root.has("per_lens_settings")) {
            String perLensFileName = context.getString(R.string._per_lens);
            SharedPreferences perLensPrefs = context.getSharedPreferences(packageName + perLensFileName, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = perLensPrefs.edit();
            editor.clear();
            com.google.gson.JsonElement perLensElement = root.get("per_lens_settings");
            if (perLensElement.isJsonArray()) {
                com.google.gson.JsonArray perLensArray = perLensElement.getAsJsonArray();
                for (com.google.gson.JsonElement element : perLensArray) {
                    JsonObject cameraObj = element.getAsJsonObject();
                    String cameraId = cameraObj.get("id").getAsString();
                    JsonObject settings = cameraObj.getAsJsonObject("settings");
                    editor.putString("settings_for_camera_" + cameraId, GSON.toJson(settings));
                }
            } else if (perLensElement.isJsonObject()) {
                JsonObject perLensPrefsObj = perLensElement.getAsJsonObject();
                for (String key : perLensPrefsObj.keySet()) {
                    putJsonValueToEditor(editor, key, perLensPrefsObj.get(key));
                }
            }
            editor.apply();
        }
        if (root.has("tunable_settings")) {
            JsonObject tunableSettingsObj = root.getAsJsonObject("tunable_settings");
            Map<String, Object> tunableSettingsMap = GSON.fromJson(tunableSettingsObj,
                new com.google.gson.reflect.TypeToken<Map<String, Object>>(){}.getType());
            TunableSettingsManager.importTunableSettings(context, tunableSettingsMap);
        }
        if (root.has("cameras_preferences")) {
            String camerasFileName = context.getString(R.string._cameras);
            SharedPreferences camerasPrefs = context.getSharedPreferences(packageName + camerasFileName, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = camerasPrefs.edit();
            JsonObject camerasPrefsObj = root.getAsJsonObject("cameras_preferences");
            for (String key : camerasPrefsObj.keySet()) {
                putJsonValueToEditor(editor, key, camerasPrefsObj.get(key));
            }
            editor.apply();
        }
        if (root.has("devices_preferences")) {
            String devicesFileName = context.getString(R.string._devices);
            SharedPreferences devicesPrefs = context.getSharedPreferences(packageName + devicesFileName, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = devicesPrefs.edit();
            JsonObject devicesPrefsObj = root.getAsJsonObject("devices_preferences");
            for (String key : devicesPrefsObj.keySet()) {
                putJsonValueToEditor(editor, key, devicesPrefsObj.get(key));
            }
            editor.apply();
        }
    }
    
    /**
     * Restore from XML input stream (e.g. from SimpleStorage). Copies to temp file then restores.
     */
    private static String restoreFromXmlStream(Context context, InputStream is, String fileName) throws IOException {
        File temp = new File(context.getCacheDir(), "restore_prefs.xml");
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(temp)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
        }
        try {
            return restoreFromXml(context, temp);
        } finally {
            temp.delete();
        }
    }
    
    /**
     * Restore from legacy XML format (backward compatibility)
     */
    private static String restoreFromXml(Context context, File xmlFile) throws IOException {
        File data_dir = context.getDataDir();
        File shared_prefs_file = Paths.get(
                data_dir.toPath()
                        + File.separator
                        + "shared_prefs"
                        + File.separator
                        + context.getPackageName() + "_preferences.xml"
        ).toFile();
        
        FileUtils.copyFile(xmlFile, shared_prefs_file);
        PhotonCamera.restartWithDelay(context, 1000);
        return "Restored from XML: " + xmlFile.getName();
    }
    
    /**
     * Helper method to put JSON values into SharedPreferences Editor with type safety
     */
    private static void putJsonValueToEditor(SharedPreferences.Editor editor, String key, com.google.gson.JsonElement value) {
        if (value.isJsonPrimitive()) {
            com.google.gson.JsonPrimitive primitive = value.getAsJsonPrimitive();
            if (primitive.isBoolean()) {
                editor.putBoolean(key, primitive.getAsBoolean());
            } else if (primitive.isNumber()) {
                // Try to determine if it's int, long, or float
                String strValue = primitive.getAsString();
                if (strValue.contains(".")) {
                    editor.putFloat(key, primitive.getAsFloat());
                } else {
                    try {
                        editor.putInt(key, primitive.getAsInt());
                    } catch (Exception e) {
                        editor.putLong(key, primitive.getAsLong());
                    }
                }
            } else {
                String strValue = primitive.getAsString();
                // Sanitize system flags which are expected to be booleans
                if (key.startsWith("pref_sysflag_")) {
                    boolean boolVal = "1".equals(strValue.trim()) || "true".equalsIgnoreCase(strValue.trim());
                    editor.putBoolean(key, boolVal);
                    return;
                }
                // Sanitize legacy/corrupted string numbers for tunable and sensor seekbars
                if (key.startsWith("pref_tunable_") || key.startsWith("pref_sensorconfig_")) {
                    try {
                        if (strValue.contains(".")) {
                            editor.putFloat(key, Float.parseFloat(strValue));
                            return;
                        } else {
                            editor.putInt(key, Integer.parseInt(strValue));
                            return;
                        }
                    } catch (NumberFormatException ignored) {
                        // Fallback to string if it is a non-numeric string or free text
                    }
                }
                editor.putString(key, strValue);
            }
        } else if (value.isJsonArray()) {
            // Handle string sets
            java.util.Set<String> stringSet = new java.util.HashSet<>();
            for (com.google.gson.JsonElement element : value.getAsJsonArray()) {
                stringSet.add(element.getAsString());
            }
            editor.putStringSet(key, stringSet);
        } else {
            // For complex objects, store as string
            editor.putString(key, value.toString());
        }
    }

    public static boolean resetPreferences(Context context) {
        File data_dir = context.getDataDir();
        File shared_prefs_dir = Paths.get(data_dir.toPath() + File.separator + "shared_prefs").toFile();
        try {
            FileUtils.deleteDirectory(shared_prefs_dir);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
}
