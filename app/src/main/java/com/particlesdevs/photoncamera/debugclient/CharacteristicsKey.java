package com.particlesdevs.photoncamera.debugclient;

import android.hardware.camera2.CameraCharacteristics;
import android.util.Log;

import com.particlesdevs.photoncamera.capture.CaptureController;

import java.io.PrintWriter;
import java.util.List;

class CharacteristicsKey implements Command{
    private String[] commands;
    private PrintWriter mBufferOut;
    private List<CameraCharacteristics.Key<?>> keys;

    public CharacteristicsKey(String[] str){
        commands = str;
        keys = CaptureController.mCameraCharacteristics.getKeys();
    }
    @Override
    public void command(){
        Log.v("DebugClient","KeyRequired:"+commands[1]);
        for(CameraCharacteristics.Key<?> key : keys){

            Log.v("DebugClient","GetKey:"+key.getName());
            if(commands[1].equals(key.getName())){
                Log.v("DebugClient","GotKey");
                mBufferOut.println(CaptureController.mCameraCharacteristics.get(key).toString());
                return;
            }
        }
    }
}

