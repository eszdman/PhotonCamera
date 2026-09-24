package com.particlesdevs.photoncamera.api;

import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;

import com.particlesdevs.photoncamera.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.pro.SensorSpecifics;
import com.particlesdevs.photoncamera.pro.SpecificSetting;
import com.particlesdevs.photoncamera.processing.render.SpecificSettingSensor;
import com.particlesdevs.photoncamera.settings.SettingsManager;
import com.particlesdevs.photoncamera.ui.camera.data.CameraLensData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.particlesdevs.photoncamera.settings.PreferenceKeys.Key.ALL_CAMERA_IDS_KEY;
import static com.particlesdevs.photoncamera.settings.PreferenceKeys.Key.ALL_CAMERA_LENS_KEY;
import static com.particlesdevs.photoncamera.settings.PreferenceKeys.Key.CAMERAS_PREFERENCE_FILE_NAME;
import static com.particlesdevs.photoncamera.settings.PreferenceKeys.Key.CAMERA_COUNT_KEY;

/**
 * Responsible for Scanning all Camera IDs on a Device and Storing them in SharedPreferences as a {@link Set<String>}
 */
public final class CameraManager2 {
    private static final String _CAMERAS = CAMERAS_PREFERENCE_FILE_NAME.mValue;
    private static final String TAG = "CameraManager2";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Map<String, CameraLensData> mCameraLensDataMap = new LinkedHashMap<>();
    private final SettingsManager mSettingsManager;
    /**
     * This Set stores the set of all valid camera IDs as String,
     * to be saved in SharedPreferences
     */
    private Set<String> mAllCameraIDsSet = new LinkedHashSet<>();
    /**
     * This Set stores the set of {@link CameraLensData} objects as JSON strings,
     * to be saved in SharedPreferences
     */
    private Set<String> mCameraLensDataJSONSet = new LinkedHashSet<>();

    /**
     * Initialise this class.
     *
     * @param cameraManager   {@link CameraManager} instance from {@link android.content.Context#CAMERA_SERVICE}
     * @param settingsManager {@link SettingsManager}
     */
    public CameraManager2(CameraManager cameraManager, SettingsManager settingsManager) {
        this.mSettingsManager = settingsManager;

        //Spinlock waiting for specific manager
        for(int i =0; i<100; i++){
            if(PhotonCamera.getSpecific().isLoaded) break;
            try {
                Thread.sleep(1);
            } catch (InterruptedException ignored) {}
        }
        SpecificSetting sp = PhotonCamera.getSpecific().specificSetting;
        String[] ids = sp.cameraIDS;
        Log.d("CameraManager2", "Loaded ids:"+ Arrays.toString(ids));
            if (!isLoaded()) {
                if(ids == null)
                    scanAllCameras(cameraManager);
                else {
                    for (String id : ids) {
                        try {
                            String physicalID = id;
                            if(id.contains("-")){
                                physicalID = id.split("-")[1];
                            }
                            CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(physicalID);
                            CameraLensData cameraLensData = createNewCameraLensData(id, cameraCharacteristics);
                            mAllCameraIDsSet.add(id);
                            mCameraLensDataMap.put(id, cameraLensData);
                        } catch (Exception ignored) {
                        }
                    }
                    findLensZoomFactor(mCameraLensDataMap);
                }
                //Override ID detection
                save();
            } else {
                loadFromSave(cameraManager,ids);
            }
            injectIszVirtualLenses();
    }
    private void initExt(CameraManager cameraManager, String[] ids) {
        for (String id : ids) {
            try {
                int num = Integer.parseInt(id);
                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(String.valueOf(num));
                log("BitAnalyser:" + num + ":" + intToReverseBinary(num));
                CameraLensData cameraLensData = createNewCameraLensData(String.valueOf(num), cameraCharacteristics);
                mAllCameraIDsSet.add(String.valueOf(num));
                mCameraLensDataMap.put(String.valueOf(num), cameraLensData);
                findLensZoomFactor(mCameraLensDataMap);
            } catch (Exception ignored) {
            }
        }
    }

    private boolean isLoaded(){
        return mSettingsManager.isSet(_CAMERAS, ALL_CAMERA_IDS_KEY);
    }

    private void loadFromSave(CameraManager cameraManager,String ids[]){
        mAllCameraIDsSet = mSettingsManager.getStringSet(_CAMERAS, ALL_CAMERA_IDS_KEY, null);
        //Retrieve the saved Set of CameraLensData JSON strings from SharedPreferences
        mCameraLensDataJSONSet = mSettingsManager.getStringSet(_CAMERAS, ALL_CAMERA_LENS_KEY, null);
        //Deserialize JSON and store CameraLensData objects into mCameraLensDataMap
        mCameraLensDataJSONSet.forEach(jsonString -> {
            CameraLensData cameraLensData = GSON.fromJson(jsonString, CameraLensData.class);
            mCameraLensDataMap.put(cameraLensData.getCameraId(), cameraLensData);
        });
        if(ids != null && mCameraLensDataJSONSet.size() < ids.length){
            initExt(cameraManager,ids);
            save();
        }
    }

    private void scanAllCameras(CameraManager cameraManager) {
        boolean isSamsung = Build.BRAND.equalsIgnoreCase("samsung") || Build.BRAND.equalsIgnoreCase("google");
            CameraLensData mainLensData = null;
            try {
                mainLensData = createNewCameraLensData("0", cameraManager.getCameraCharacteristics("0"));
                for (int num = 0; num < 121; num++) {
                    try {
                        String formatID = String.valueOf(num);
                        if(isSamsung){
                            formatID = "0-" + formatID;
                        }
                        CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(String.valueOf(num));
                        log("BitAnalyser:" + num + ":" + intToReverseBinary(num));
                        CameraLensData cameraLensData = createNewCameraLensData(formatID, cameraCharacteristics);
                        if (mainLensData.getCameraFocalLength() == cameraLensData.getCameraFocalLength()) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                boolean isLogical = !cameraCharacteristics.getPhysicalCameraIds().isEmpty();
                                if (!getBit(6, num) && !mCameraLensDataMap.containsValue(cameraLensData) && !isLogical) {
                                    mAllCameraIDsSet.add(formatID);
                                    mCameraLensDataMap.put(formatID, cameraLensData);
                                    break;
                                }
                            } else {
                                if (!getBit(6, num) && !mCameraLensDataMap.containsValue(cameraLensData)) {
                                    mAllCameraIDsSet.add(formatID);
                                    mCameraLensDataMap.put(formatID, cameraLensData);
                                    break;
                                }
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
            } catch (Exception ignored) {

            }
            for (int num = 0; num < 121; num++) {
                try {
                    String formatID = String.valueOf(num);
                    if(isSamsung){
                        formatID = "0-" + formatID;
                    }
                    CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(String.valueOf(num));
                    log("BitAnalyser:" + num + ":" + intToReverseBinary(num));
                    CameraLensData cameraLensData = createNewCameraLensData(formatID, cameraCharacteristics);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        boolean isLogical = !cameraCharacteristics.getPhysicalCameraIds().isEmpty();
                        if (!getBit(6, num) && !mCameraLensDataMap.containsValue(cameraLensData) && !isLogical) {
                            mAllCameraIDsSet.add(formatID);
                            mCameraLensDataMap.put(formatID, cameraLensData);
                        }
                    } else {
                        if (!getBit(6, num) && !mCameraLensDataMap.containsValue(cameraLensData)) {
                            mAllCameraIDsSet.add(formatID);
                            mCameraLensDataMap.put(formatID, cameraLensData);
                        }
                    }
                } catch (Exception ignored) {
                }
            }
            if(mAllCameraIDsSet.size() == 0) {
                for(int i = 0; i<2;i++){
                    try {
                        String formatID = String.valueOf(i);
                        if(isSamsung){
                            formatID = "0-" + formatID;
                        }
                        CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(String.valueOf(i));
                        CameraLensData cameraLensData = createNewCameraLensData(formatID, cameraCharacteristics);
                        mAllCameraIDsSet.add(formatID);
                        mCameraLensDataMap.put(formatID, cameraLensData);
                        } catch (Exception ignored) {
                        }
                }
            }

        findLensZoomFactor(mCameraLensDataMap);
    }

    private CameraLensData createNewCameraLensData(String cameraId, CameraCharacteristics characteristics) {
        CameraLensData cameraLensData = new CameraLensData(cameraId);
        cameraLensData.setFacing(characteristics.get(CameraCharacteristics.LENS_FACING));
        cameraLensData.setCameraFocalLength(characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)[0]);
        cameraLensData.setCameraAperture(characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)[0]);
        cameraLensData.setCamera35mmFocalLength((36.0f / characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE).getWidth() * cameraLensData.getCameraFocalLength()));
        cameraLensData.setFlashSupported(characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE));
        return cameraLensData;
    }

    /**
     * This method finds the optical zoom factor of multiple camera lenses with respect to the Main Camera for each facing(ie. Front and Back)
     * <p>
     * Note: Here, it is assumed that the first camera in the list for each facing is the Main Camera for that facing.
     *
     * @param mCameraLensData Map of all valid CameraLensData objects
     */
    private void findLensZoomFactor(Map<String, CameraLensData> mCameraLensData) {
        CameraLensData mainBack = null;
        CameraLensData mainFront = null;
        for (Map.Entry<String, CameraLensData> entry : mCameraLensData.entrySet()) {
            CameraLensData cameraLensData = entry.getValue();
            if (cameraLensData.getFacing() == CameraCharacteristics.LENS_FACING_FRONT) {
                if (mainFront == null) {
                    mainFront = cameraLensData;
                }
                cameraLensData.setZoomFactor(cameraLensData.getCamera35mmFocalLength() / mainFront.getCamera35mmFocalLength());
            } else if (cameraLensData.getFacing() == CameraCharacteristics.LENS_FACING_BACK) {
                if (mainBack == null) {
                    mainBack = cameraLensData;
                }
                cameraLensData.setZoomFactor(cameraLensData.getCamera35mmFocalLength() / mainBack.getCamera35mmFocalLength());
            }
        }
    }

    private void log(String msg) {
        Log.d(TAG, msg);
    }

    private void save() {
        mSettingsManager.set(_CAMERAS, CAMERA_COUNT_KEY, mAllCameraIDsSet.size());
        mSettingsManager.set(_CAMERAS, ALL_CAMERA_IDS_KEY, mAllCameraIDsSet);

        //Serialise CameraLensData objects to JSON and store them to mCameraLensDataJSONSet
        mCameraLensDataMap.forEach((id, lensData) -> mCameraLensDataJSONSet.add(GSON.toJson(lensData)));
        //Save mCameraLensDataJSONSet to SharedPreferences
        mSettingsManager.set(_CAMERAS, ALL_CAMERA_LENS_KEY, mCameraLensDataJSONSet);
    }

    //Getters===========================================================================================================

    /**
     * @return the list of scanned Camera IDs
     */
    public String[] getCameraIdList() {
        log("CameraCount:" + mAllCameraIDsSet.size()
                + ", CameraIDs:" + Arrays.toString(mAllCameraIDsSet.toArray(new String[0])));
        return mAllCameraIDsSet.toArray(new String[0]);
    }

    /**
     * @return the map of CameraLensData
     */
    public Map<String, CameraLensData> getCameraLensDataMap() {
        Log.d(TAG,"LensData : \n" + mCameraLensDataMap);
        return mCameraLensDataMap;
    }

    //Bit analyzer for AUX number=======================================================================================

    private boolean getBit(int pos, int val) {
        return ((val >> (pos - 1)) & 1) == 1;
    }

    private String intToReverseBinary(int num) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 8; i++) {
            sb.append(getBit(i, num) ? "1" : "0");
        }
        return sb.toString();
    }

    /** True when a camera id identifies an In-Sensor Zoom (ISZ) virtual lens. */
    public static boolean isIszVirtual(String cameraId) {
        return IszLensUtil.isIszVirtual(cameraId);
    }

    /**
     * Builds a 3-segment ISZ id that routes to the same logical/physical sensor as
     * the base id while remaining distinct from it. Works for both plain ("3") and
     * composite ("0-3") base ids; every composite-id consumer only reads segments
     * [0] and [1], so the trailing virtual marker is ignored for routing.
     */
    public static String composeIszVirtualId(String baseCameraId) {
        return IszLensUtil.composeIszVirtualId(baseCameraId);
    }

    /** Physical id (after the last {@code -}) of a (possibly composite) camera id. */
    public static int physicalIdFrom(String cameraId) {
        return IszLensUtil.physicalIdFrom(cameraId);
    }

    /**
     * Appends an ISZ virtual-lens overlay to every physical sensor that carries an
     * {@code ISZ} config in SensorSpecifics. The virtual lens clones the real lens
     * (so it routes to the same sensor) but inflates {@code zoomFactor} by the ISZ
     * zoom ratio, producing a distinct lens-switcher entry (e.g. 3.1x x 2.0 = 6.2x).
     *
     * <p>The overlay is regenerated on every construction so it stays in sync with
     * any edited/fetched SensorSpecifics config; it is not persisted.</p>
     */
    private void injectIszVirtualLenses() {
        try {
            SensorSpecifics specifics = PhotonCamera.getSpecificSensor();
            if (specifics == null) return;
            // Snapshot to avoid ConcurrentModification on the backing map.
            List<CameraLensData> real = new ArrayList<>(mCameraLensDataMap.values());
            for (CameraLensData base : real) {
                int physicalId = physicalIdFrom(base.getCameraId());
                SpecificSettingSensor isz = specifics.getIszForSensor(physicalId);
                if (isz == null || isz.iszKey == null || isz.iszZoomRatio <= 0f) continue;
                String virtualId = composeIszVirtualId(base.getCameraId());
                if (mCameraLensDataMap.containsKey(virtualId)) continue;

                CameraLensData virtual = new CameraLensData(virtualId);
                virtual.setFacing(base.getFacing());
                virtual.setCameraFocalLength(base.getCameraFocalLength());
                virtual.setCameraAperture(base.getCameraAperture());
                virtual.setCamera35mmFocalLength(base.getCamera35mmFocalLength());
                virtual.setZoomFactor(base.getZoomFactor() * isz.iszZoomRatio);
                virtual.setFlashSupported(base.getFlashSupported());
                mCameraLensDataMap.put(virtualId, virtual);
                log("Injected ISZ virtual lens: " + virtualId + " @ " + virtual.getZoomFactor() + "x");
            }
        } catch (Exception e) {
            log("Failed to inject ISZ virtual lenses: " + Log.getStackTraceString(e));
        }
    }
}
