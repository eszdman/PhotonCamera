package com.particlesdevs.photoncamera.pro;

import android.os.Build;

import com.particlesdevs.photoncamera.settings.SettingsManager;
import com.particlesdevs.photoncamera.util.HttpLoader;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;

public class SensorSpecifics {
    public SpecificSettingSensor[] specificSettingSensor;
    public SensorSpecifics(SettingsManager mSettingsManager){
        int count = 1;
        String device = Build.BRAND.toLowerCase() + "/" + Build.DEVICE.toLowerCase();
        String fullSpec = "";
        ArrayList<String> inputStr = new ArrayList<String>();
        try {
            BufferedReader indevice = HttpLoader.readURL("https://raw.githubusercontent.com/eszdman/PhotonCamera/dev/app/specific/sensors/" + device + ".txt");
            String str;
            count = 0;
            while ((str = indevice.readLine()) != null) {
                if(str.contains("sensor")) count++;
                inputStr.add(str+"\n");
            }
            specificSettingSensor = new SpecificSettingSensor[count];
            for (String str2 : inputStr) {
                if(str2.contains("sensor")) count++;
                inputStr.add(str2+"\n");
            }

        } catch (Exception ignored) {}
        try {

        }catch (Exception e){

        }
    }

}
