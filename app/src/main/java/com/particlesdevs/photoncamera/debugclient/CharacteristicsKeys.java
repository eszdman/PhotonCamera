package com.particlesdevs.photoncamera.debugclient;

import android.hardware.camera2.CameraCharacteristics;

import com.particlesdevs.photoncamera.capture.CaptureController;

import java.io.PrintWriter;
import java.util.List;


class CharacteristicsKeys implements Command{
    private String[] commands;
    private PrintWriter mBufferOut;
    List<CameraCharacteristics.Key<?>> keys;
    public CharacteristicsKeys(String[] str){
        commands = str;
        keys = CaptureController.mCameraCharacteristics.getKeys();
    }

    @Override
    public void command(){
        StringBuilder keysStr = new StringBuilder();
        for(CameraCharacteristics.Key<?> key : keys){
            keysStr.append(key.getName());
            keysStr.append("\n");
        }
        mBufferOut.println(keysStr.toString());
    }
}



