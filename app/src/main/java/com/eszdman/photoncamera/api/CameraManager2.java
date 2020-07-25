package com.eszdman.photoncamera.api;

import android.content.SharedPreferences;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static android.content.Context.MODE_PRIVATE;

public class CameraManager2 {
    private static String TAG = "CameraManager2";
    public static CameraManager2 cameraManager2;
    private CameraManager manager;
    private SharedPreferences sharedPreferences;
    private Set<String> mCameraIDs = new HashSet<String>();
    public CameraManager2(CameraManager manag) {
        cameraManager2 = this;
        manager = manag;
        sharedPreferences = Interface.i.mainActivity.getPreferences(MODE_PRIVATE);
        if(!sharedPreferences.getBoolean("GotAux",false)){
            getCameraId();
            save();
        } else {
            mCameraIDs = sharedPreferences.getStringSet("Cameras",null);
        }
    }
    public String[] getCameraIdList(){
        String[] arr = mCameraIDs.toArray(new String[mCameraIDs.size()]);
        int[] idarr = new int[arr.length];
        for(int i =0; i<arr.length;i++) {
            idarr[i] = Integer.parseInt(arr[i]);
        }
        Arrays.sort(idarr);
        for(int i =0; i<arr.length;i++) {
            arr[i] = String.valueOf(idarr[i]);
            Log.d(TAG, "GotArray:" + arr[i]);
        }
        return arr;
    }
    //Bit analyzer for AUX number
    private boolean getBit(int pos, int val){
        return ((val >> (pos - 1)) & 1) == 1;
    }
    private boolean checkCaps(String caps, ArrayList<String> capsarr){
        boolean same = false;
        if(capsarr.size() != 0)
        for(String capsin : capsarr){
            if (capsin.equals(caps)) {
                same = true;
                break;
            }
        }
        return same;
    }
    private void getCameraId() {
        ArrayList<String> CameraIDs = new ArrayList<>();
        ArrayList<String> Caps = new ArrayList<>();
        for(int i =0; i<121;i++){
            CameraIDs.add(String.valueOf(i));
        }
        try {
            for (String nextId : CameraIDs) {
                CameraCharacteristics cameraCharacteristics;
                try {
                    cameraCharacteristics = manager.getCameraCharacteristics(nextId);
                    if (cameraCharacteristics != null) {
                        int id = Integer.parseInt(nextId);
                        Log.d(TAG,"Number:"+nextId+" bit 4:"+getBit(4,id)+" bit 5:"+getBit(5,id)+" bit 6:"+getBit(6,id)+ " bit 7:"+ getBit(7,id)+" bit 8:"+ getBit(8,id));
                        String caps = (String.valueOf(cameraCharacteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)[0])+
                                cameraCharacteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)[0]);
                        Log.d(TAG,"Caps:"+caps);
                        if((id == 0 || id == 1 || !getBit(6,id)) && !checkCaps(caps,Caps)){
                            Caps.add(caps);
                            mCameraIDs.add(nextId);
                        }

                    }
                } catch(Exception ignored){}
            }
        } catch(Exception ignored){}
        mCameraIDs.toArray();
    }

    void save() {
        SharedPreferences.Editor sharedPreferencesEditor = sharedPreferences.edit();
        sharedPreferencesEditor.putBoolean("GotAux", true);
        sharedPreferencesEditor.putStringSet("Cameras",mCameraIDs);
        sharedPreferencesEditor.apply();
    }
}
