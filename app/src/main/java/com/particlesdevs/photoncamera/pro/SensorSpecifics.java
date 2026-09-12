package com.particlesdevs.photoncamera.pro;

import android.content.Context;
import android.os.Build;
import com.particlesdevs.photoncamera.api.VendorTagUtils;
import com.particlesdevs.photoncamera.util.Log;

import com.particlesdevs.photoncamera.processing.render.SpecificSettingSensor;
import com.particlesdevs.photoncamera.settings.PreferenceKeys;
import com.particlesdevs.photoncamera.settings.SettingsManager;
import com.particlesdevs.photoncamera.util.HttpLoader;
import com.particlesdevs.photoncamera.util.SimpleStorageHelper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;

public class SensorSpecifics {
    private static final String TAG = "SensorSpecifics";
    public SpecificSettingSensor[] specificSettingSensor;
    public SpecificSettingSensor selectedSensorSpecifics = new SpecificSettingSensor();

    private static final String SENSOR_SPECIFICS_PATH = "DCIM/PhotonCamera/Tuning/SensorSpecifics.txt";

    ArrayList<String> loadCache(Context context, SettingsManager mSettingsManager) {
        String cached = mSettingsManager.getString(PreferenceKeys.Key.DEVICES_PREFERENCE_FILE_NAME.mValue, "sensor_specific_val", "");
        ArrayList<String> inputStr = new ArrayList<>();
        if (!cached.isEmpty()) {
            for (String line : cached.split("\n", -1)) {
                inputStr.add(line + "\n");
            }
        }
        Log.d(TAG, "Loaded cache:" + inputStr.size());
        return inputStr;
    }

    ArrayList<String> loadLocal(Context context) throws Exception {
        ArrayList<String> inputStr = new ArrayList<>();
        InputStream is = SimpleStorageHelper.openInputStreamByPath(context, SENSOR_SPECIFICS_PATH);
        BufferedReader indevice = new BufferedReader(new InputStreamReader(is));
        String str;
        while ((str = indevice.readLine()) != null) {
            Log.d(TAG, "read local:" + str);
            inputStr.add(str + "\n");
        }
        indevice.close();
        return inputStr;
    }

    ArrayList<String> loadAssets(Context context, String device) throws IOException {
        ArrayList<String> inputStr = new ArrayList<>();
        String assetPath = "specific/sensors/" + device + ".txt";
        InputStream is = context.getAssets().open(assetPath);
        BufferedReader indevice = new BufferedReader(new InputStreamReader(is));
        String str;
        while ((str = indevice.readLine()) != null) {
            Log.d(TAG, "read asset:" + str);
            inputStr.add(str + "\n");
        }
        indevice.close();
        return inputStr;
    }

    ArrayList<String> loadNetwork(String device) throws IOException {
        ArrayList<String> inputStr = new ArrayList<>();
        BufferedReader indevice = HttpLoader.readURL(
                "https://raw.githubusercontent.com/eszdman/PhotonCamera/dev/app/src/main/assets/specific/sensors/" + device + ".txt", 150);
        String str;
        while ((str = indevice.readLine()) != null) {
            Log.d(TAG, "read network:" + str);
            inputStr.add(str + "\n");
        }
        return inputStr;
    }

    private void parseAndApply(ArrayList<String> inputStr, SettingsManager mSettingsManager, boolean[] loaded) {
        int count = 0;
        for (String str : inputStr) {
            if (str.contains("sensor")) count++;
        }
        Log.d(TAG, "SensorCount:" + count);
        specificSettingSensor = new SpecificSettingSensor[count];
        count = 0;
        for (String str2 : inputStr) {
            if (str2.contains("sensor")) {
                String[] vals = str2.split("_");
                vals[1] = vals[1].replace("\n", "");
                specificSettingSensor[count] = new SpecificSettingSensor();
                specificSettingSensor[count].id = Integer.parseInt(vals[1]);
                count++;
            } else {
                String[] valsIn = str2.replace(" ", "").replace("\n", "").split("=");
                if (valsIn.length <= 1) continue;
                String[] istr = valsIn[1].replace("{", "").replace("}", "").split(",");
                SpecificSettingSensor current = specificSettingSensor[count - 1];
                switch (valsIn[0]) {
                    case "NoiseModelA": {
                        for (int i = 0; i < 4; i++)
                            current.NoiseModelerArr[0][i] = Double.parseDouble(istr[i]);
                        break;
                    }
                    case "NoiseModelB": {
                        for (int i = 0; i < 4; i++)
                            current.NoiseModelerArr[1][i] = Double.parseDouble(istr[i]);
                        break;
                    }
                    case "NoiseModelC": {
                        for (int i = 0; i < 4; i++)
                            current.NoiseModelerArr[2][i] = Double.parseDouble(istr[i]);
                        break;
                    }
                    case "NoiseModelD": {
                        for (int i = 0; i < 4; i++)
                            current.NoiseModelerArr[3][i] = Double.parseDouble(istr[i]);
                        current.ModelerExists = true;
                        break;
                    }
                    case "captureSharpeningS":
                        current.captureSharpeningS = (float) Double.parseDouble(valsIn[1]);
                        break;
                    case "captureSharpeningIntense":
                        current.captureSharpeningIntense = (float) Double.parseDouble(valsIn[1]);
                        break;
                    case "aberrationCorrection": {
                        for (int i = 0; i < 8; i++)
                            current.aberrationCorrection[i] = (float) Double.parseDouble(istr[i]);
                        break;
                    }
                    case "calibrationTransform1": {
                        current.CalibrationTransform1 = new float[3][3];
                        for (int i = 0; i < 3; i++)
                            for (int j = 0; j < 3; j++)
                                current.CalibrationTransform1[i][j] = (float) Double.parseDouble(istr[i * 3 + j]);
                        break;
                    }
                    case "calibrationTransform2": {
                        current.CalibrationTransform2 = new float[3][3];
                        for (int i = 0; i < 3; i++)
                            for (int j = 0; j < 3; j++)
                                current.CalibrationTransform2[i][j] = (float) Double.parseDouble(istr[i * 3 + j]);
                        break;
                    }
                    case "colorTransform1": {
                        current.ColorTransform1 = new float[3][3];
                        for (int i = 0; i < 3; i++)
                            for (int j = 0; j < 3; j++)
                                current.ColorTransform1[i][j] = (float) Double.parseDouble(istr[i * 3 + j]);
                        break;
                    }
                    case "colorTransform2": {
                        current.ColorTransform2 = new float[3][3];
                        for (int i = 0; i < 3; i++)
                            for (int j = 0; j < 3; j++)
                                current.ColorTransform2[i][j] = (float) Double.parseDouble(istr[i * 3 + j]);
                        break;
                    }
                    case "forwardMatrix1": {
                        current.ForwardMatrix1 = new float[3][3];
                        for (int i = 0; i < 3; i++)
                            for (int j = 0; j < 3; j++)
                                current.ForwardMatrix1[i][j] = (float) Double.parseDouble(istr[i * 3 + j]);
                        break;
                    }
                    case "forwardMatrix2": {
                        current.ForwardMatrix2 = new float[3][3];
                        for (int i = 0; i < 3; i++)
                            for (int j = 0; j < 3; j++)
                                current.ForwardMatrix2[i][j] = (float) Double.parseDouble(istr[i * 3 + j]);
                        break;
                    }
                    case "referenceIlluminant1":
                        current.referenceIlluminant1 = Integer.parseInt(valsIn[1]);
                        break;
                    case "referenceIlluminant2":
                        current.referenceIlluminant2 = Integer.parseInt(valsIn[1]);
                        break;
                    case "profileHueSatMapDims": {
                        if (istr.length < 3) break;
                        current.profileHueSatMapDims = new int[3];
                        for (int i = 0; i < 3; i++)
                            current.profileHueSatMapDims[i] = Integer.parseInt(istr[i]);
                        break;
                    }
                    case "profileHueSatMapData1": {
                        if (current.profileHueSatMapDims == null) break;
                        int sz = current.profileHueSatMapDims[0] * current.profileHueSatMapDims[1] * current.profileHueSatMapDims[2];
                        if (istr.length < sz) break;
                        current.profileHueSatMapData1 = new float[sz * 3];
                        for (int i = 0; i < current.profileHueSatMapData1.length; i++)
                            current.profileHueSatMapData1[i] = Float.parseFloat(istr[i]);
                        current.profileHueSatMapData2 = current.profileHueSatMapData1.clone();
                    }
                    case "profileHueSatMapData2": {
                        if (current.profileHueSatMapDims == null) break;
                        int sz = current.profileHueSatMapDims[0] * current.profileHueSatMapDims[1] * current.profileHueSatMapDims[2];
                        if (istr.length < sz) break;
                        current.profileHueSatMapData2 = new float[sz * 3];
                        for (int i = 0; i < current.profileHueSatMapData2.length; i++)
                            current.profileHueSatMapData2[i] = Float.parseFloat(istr[i]);
                        break;
                    }
                    case "profileLookTableDims": {
                        if (istr.length < 3) break;
                        current.profileLookTableDims = new int[3];
                        for (int i = 0; i < 3; i++)
                            current.profileLookTableDims[i] = Integer.parseInt(istr[i]);
                        break;
                    }
                    case "profileLookTableData": {
                        if (current.profileLookTableDims == null) break;
                        int sz = current.profileLookTableDims[0] * current.profileLookTableDims[1] * current.profileLookTableDims[2];
                        if (istr.length < sz) break;
                        current.profileLookTableData = new float[sz * 3];
                        for (int i = 0; i < current.profileLookTableData.length; i++)
                            current.profileLookTableData[i] = Float.parseFloat(istr[i]);
                        break;
                    }
                    case "preferredResolution": {
                        if (istr.length < 2) break;
                        current.preferredResolution = new int[2];
                        for (int i = 0; i < 2; i++)
                            current.preferredResolution[i] = Integer.parseInt(istr[i]);
                        break;
                    }
                    case "overrideRawColors":
                        current.overrideRawColors = Boolean.parseBoolean(valsIn[1]);
                        break;
                    case "ISZ": {
                        // In-Sensor Zoom virtual lens: { valueType ; keyName ; keyValue ; zoomRatio }
                        // Fields are ';'-delimited so array keyValues (comma separated) stay intact.
                        String iszBody = valsIn[1].replace("{", "").replace("}", "");
                        String[] parts = iszBody.split(";");
                        if (parts.length < 4) break;
                        VendorTagUtils.TunableKey key = new VendorTagUtils.TunableKey();
                        key.type = "CaptureRequest";
                        key.valueType = parts[0].trim();
                        key.name = parts[1].trim();
                        key.value = parts[2].trim();
                        current.iszKey = key;
                        current.iszZoomRatio = Float.parseFloat(parts[3].trim());
                        current.hasIsz = true;
                        Log.d(TAG, "ISZ for sensor " + current.id + ": " + key.name
                                + " (" + key.valueType + ") = " + key.value + " ratio " + current.iszZoomRatio);
                        break;
                    }
                }
                current.updateTransforms();
                loaded[0] = true;
            }
        }
    }

    public void loadSpecifics(SettingsManager mSettingsManager, Context context) {
        final boolean[] loaded = {mSettingsManager.getBoolean(PreferenceKeys.Key.DEVICES_PREFERENCE_FILE_NAME.mValue, "sensor_specific_loaded", false)};
        Log.d(TAG, "loaded:" + loaded[0]);
        String device = Build.BRAND.toLowerCase() + "/" + Build.DEVICE.toLowerCase();
        ArrayList<String> inputStr = new ArrayList<>();
        try {
            if (SimpleStorageHelper.fileExistsByPath(context, SENSOR_SPECIFICS_PATH)) {
                Log.d(TAG, "Loading from local file");
                inputStr = loadLocal(context);
            } else {
                try {
                    Log.d(TAG, "Loading from cache");
                    inputStr = loadCache(context, mSettingsManager);
                    if(inputStr.isEmpty()) {
                        Log.d(TAG, "Loading from assets");
                        inputStr = loadAssets(context, device);
                    }
                } catch (IOException e) {
                    Log.d(TAG, "No asset found for device, skipping network: " + device);
                    if (loaded[0]) {
                        inputStr = mSettingsManager.getArrayList(
                                PreferenceKeys.Key.DEVICES_PREFERENCE_FILE_NAME.mValue,
                                "sensor_specific_loaded", new HashSet<>());
                    }
                }
            }
            if (!inputStr.isEmpty()) {
                parseAndApply(inputStr, mSettingsManager, loaded);
            }
        } catch (Exception e) {
            Log.d(TAG, "Failed to load sensor specific:" + Log.getStackTraceString(e));
        }
        mSettingsManager.set(PreferenceKeys.Key.DEVICES_PREFERENCE_FILE_NAME.mValue, "sensor_specific_loaded", loaded[0]);
    }

    public void fetchFromNetwork(SettingsManager mSettingsManager, Context context) {
        String device = Build.BRAND.toLowerCase() + "/" + Build.DEVICE.toLowerCase();
        Log.d(TAG, "Fetching from network for device: " + device);
        try {
            final boolean[] loaded = {false};
            ArrayList<String> inputStr = loadNetwork(device);
            if (!inputStr.isEmpty()) {
                parseAndApply(inputStr, mSettingsManager, loaded);
                mSettingsManager.set(PreferenceKeys.Key.DEVICES_PREFERENCE_FILE_NAME.mValue, "sensor_specific_loaded", loaded[0]);
                StringBuilder cache = new StringBuilder();
                for (String line : inputStr) cache.append(line);
                mSettingsManager.set(PreferenceKeys.Key.DEVICES_PREFERENCE_FILE_NAME.mValue, "sensor_specific_val", cache.toString());
                Log.d(TAG, "Network fetch successful");
            }
        } catch (Exception e) {
            Log.e(TAG, "Network fetch failed: " + e.toString());
        }
    }

    public void selectSpecifics(int id) {
        if (specificSettingSensor != null) {
            for (SpecificSettingSensor specifics : specificSettingSensor) {
                if (specifics != null && specifics.id == id) {
                    Log.d(TAG, "Selected:" + id);
                    selectedSensorSpecifics = specifics;
                }
            }
        }
    }

    /**
     * Returns the In-Sensor Zoom (ISZ) virtual-lens config for a physical sensor id,
     * or {@code null} if that sensor does not expose an ISZ lens.
     *
     * @param sensorId the physical camera id (e.g. {@code 3})
     * @return the specific setting sensor carrying an ISZ key, or {@code null}
     */
    public SpecificSettingSensor getIszForSensor(int sensorId) {
        if (specificSettingSensor == null) return null;
        for (SpecificSettingSensor specifics : specificSettingSensor) {
            if (specifics != null && specifics.id == sensorId && specifics.hasIsz) {
                return specifics;
            }
        }
        return null;
    }
}
