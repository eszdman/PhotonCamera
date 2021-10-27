package com.particlesdevs.photoncamera.debugclient;

import com.particlesdevs.photoncamera.util.FileManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class Debugger {
    public DebugParameters debugParameters = new DebugParameters();
    public DebugClient debugClient;
    public Debugger(){
        File debugClientSettings = new File(FileManager.sPHOTON_TUNING_DIR,"DebugClient.txt");
        if(debugClientSettings.exists()){
            try {
                BufferedReader bufferedReader = new BufferedReader(new FileReader(debugClientSettings));
                String[] ipPort = bufferedReader.readLine().split(":");
                debugClient = new DebugClient(ipPort[0],ipPort[1]);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
