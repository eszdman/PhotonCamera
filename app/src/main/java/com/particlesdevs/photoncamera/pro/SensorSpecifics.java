package com.particlesdevs.photoncamera.pro;

import android.os.Build;

import com.particlesdevs.photoncamera.settings.SettingsManager;
import com.particlesdevs.photoncamera.util.HttpLoader;

import java.io.BufferedReader;
import java.io.IOException;

public class SensorSpecifics {
    public SpecificSettingSensor[] specificSettingSensor;
    public SensorSpecifics(SettingsManager mSettingsManager){
        int count = 1;
        String device = Build.BRAND.toLowerCase() + "/" + Build.DEVICE.toLowerCase();
        String fullSpec = "";
        try {
            BufferedReader indevice = HttpLoader.readURL("https://raw.githubusercontent.com/eszdman/PhotonCamera/dev/app/specific/sensors/" + device + ".txt");
            StringBuilder builder = new StringBuilder();
            String str;
            count = 0;
            while ((str = indevice.readLine()) != null) {
                builder.append(str).append("\n");
                if(str.contains("sensor")) count++;
                String[] caseS = str.split("=");
            }
        } catch (Exception ignored) {}
        try {

        }catch (Exception e){

        }
    }

}
