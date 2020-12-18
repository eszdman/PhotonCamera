package com.eszdman.photoncamera.pro;

import android.content.Context;
import android.os.Build;
import android.widget.Toast;

import androidx.core.util.Pair;

import com.eszdman.photoncamera.app.PhotonCamera;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;

public class SupportedDevice {
    private Context context;
    private final Map<String, Boolean> mSupportedDevice = new LinkedHashMap<>();
    public SupportedDevice(Context context1){
        context = context1;
        try {
            LoadSupported();
        } catch (Exception e){
            Toast.makeText(context,"Can't load config",Toast.LENGTH_SHORT).show();
        }
    }
    public boolean isSupported(){
        if(mSupportedDevice.containsKey(Build.BRAND.toLowerCase()+":"+Build.DEVICE.toLowerCase())){
            Toast.makeText(context,"PhotonCamera Pro",Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(context,"Unsupported device, PhotonCamera - limited",Toast.LENGTH_SHORT).show();
        }
        Toast.makeText(context,"Config:"+ Build.BRAND+":"+Build.DEVICE,Toast.LENGTH_SHORT).show();
        return true;
    }
    private void LoadSupported() throws IOException {
        //mSupportedDevice.put("xiaomi:cepheus",true);
        URL supportedList = new URL("https://github.com/eszdman/PhotonCamera/blob/dev/app/SupportedList.txt");
        HttpURLConnection conn=(HttpURLConnection) supportedList.openConnection();
        conn.setConnectTimeout(200); // timing out in a minute
        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String str;
        while ((str = in.readLine()) != null) {
            mSupportedDevice.put(str,true);
        }
        in.close();
    }
}
