package com.particlesdevs.photoncamera.debugclient;

import android.hardware.camera2.CaptureRequest;

import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.capture.CaptureController;

import java.io.PrintWriter;


class BuilderCreate implements Command{
    private  String[] commands;
    private PrintWriter mBufferOut;
    CaptureController controller = PhotonCamera.getCaptureController();
    CaptureRequest.Builder captureRequestBuilder = null;
    public BuilderCreate(String[] str){
        commands = str;
    }
    @Override
    public void command(){
        captureRequestBuilder = controller.getDebugCaptureRequestBuilder();
        if(captureRequestBuilder == null)
            mBufferOut.println("Error at creating builder!");
        mBufferOut.println("Builder created");
    }
}

