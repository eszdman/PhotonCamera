package com.particlesdevs.photoncamera.debugclient;

import android.hardware.camera2.CaptureRequest;

import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.capture.CaptureController;

import java.io.PrintWriter;
import java.util.ArrayList;

import static com.particlesdevs.photoncamera.debugclient.DebugClient.setKey;


class BuilderSet implements Command{
    private  String[] commands;
    private PrintWriter mBufferOut;
    CaptureController controller = PhotonCamera.getCaptureController();
    CaptureRequest.Builder captureRequestBuilder = null;
    ArrayList<CaptureRequest.Key<?>> captureKeys;

    public BuilderSet(String[] str){
        commands = str;
        captureKeys = new ArrayList<>(controller.mPreviewInputRequest.getKeys());
    }
    @Override
    public void command(){
        CaptureRequest.Key<?> requestKey = null;
        for(CaptureRequest.Key<?> key : captureKeys){
            if(key.getName().equals(commands[1])) requestKey = key;
        }
        if(requestKey == null) {
            mBufferOut.println("Request key is null");
            return;
        }
        setKey(captureRequestBuilder,requestKey,commands[2],commands[3]);
    }


}

