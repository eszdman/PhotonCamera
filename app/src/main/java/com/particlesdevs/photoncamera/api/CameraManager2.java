package com.particlesdevs.photoncamera.api;

import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hunter.library.debug.HunterDebug;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.pro.SpecificSetting;
import com.particlesdevs.photoncamera.settings.SettingsManager;
import com.particlesdevs.photoncamera.ui.camera.data.CameraLensData;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
        int[] ids = sp.cameraIDS;
        Log.d("CameraManager2", "Loaded ids:"+ Arrays.toString(ids));
            if (!isLoaded()) {
                if(ids == null)
                    scanAllCameras(cameraManager);
                else {
                    for (int id : ids) {
                        try {
                            CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(String.valueOf(id));
                            CameraLensData cameraLensData = createNewCameraLensData(String.valueOf(id), cameraCharacteristics);
                            mAllCameraIDsSet.add(String.valueOf(id));
                            mCameraLensDataMap.put(String.valueOf(id), cameraLensData);
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
    }
    private void initExt(CameraManager cameraManager, int[] ids) {
        for (int num : ids) {
            try {
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

    private void loadFromSave(CameraManager cameraManager,int ids[]){
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

            for (int num = 0; num < 121; num++) {
                try {
                    CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(String.valueOf(num));
                    log("BitAnalyser:" + num + ":" + intToReverseBinary(num));
                    CameraLensData cameraLensData = createNewCameraLensData(String.valueOf(num), cameraCharacteristics);
                    if (!getBit(6, num) && !mCameraLensDataMap.containsValue(cameraLensData)) {
                        mAllCameraIDsSet.add(String.valueOf(num));
                        mCameraLensDataMap.put(String.valueOf(num), cameraLensData);
                    }
                } catch (Exception ignored) {
                }
            }
            if(mAllCameraIDsSet.size() == 0) {
                for(int i = 0; i<2;i++){
                    try {
                    CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(String.valueOf(i));
                    CameraLensData cameraLensData = createNewCameraLensData(String.valueOf(i), cameraCharacteristics);
                    mAllCameraIDsSet.add(String.valueOf(i));
                    mCameraLensDataMap.put(String.valueOf(i), cameraLensData);
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
}
