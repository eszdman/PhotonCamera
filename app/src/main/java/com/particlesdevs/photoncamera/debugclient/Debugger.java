package com.particlesdevs.photoncamera.debugclient;

import com.particlesdevs.photoncamera.util.FileManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class Debugger {
    public DebugClient debugClient;
    public Debugger(){
        File debugClientSettings = new File(FileManager.sPHOTON_TUNING_DIR,"DebugClient.txt");
        String[] ipPort = readDebugClientFile();
        makeDebugClient(ipPort[0],ipPort[1]);
    }
    private String[] readDebugClientFile(){
        File debugClientSettings = new File(FileManager.sPHOTON_TUNING_DIR,"DebugClient.txt");
        if(debugClientSettings.exists()) {
            try {
                BufferedReader bufferedReader = new BufferedReader(new FileReader(debugClientSettings));
                String[] ipPort = bufferedReader.readLine().split(":");
                return ipPort;

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    private void makeDebugClient(String ip, String port){
        debugClient = new DebugClient(ip, port);
    }
}
