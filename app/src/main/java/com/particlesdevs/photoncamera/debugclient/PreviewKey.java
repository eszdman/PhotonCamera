package com.particlesdevs.photoncamera.debugclient;

import android.hardware.camera2.CaptureResult;

import com.particlesdevs.photoncamera.capture.CaptureController;

import java.io.PrintWriter;
import java.util.List;

import static com.particlesdevs.photoncamera.debugclient.DebugClient.previewKeyValue;


class PreviewKey implements Command{
    private  String[] commands;
    private PrintWriter mBufferOut;
    List<CaptureResult.Key<?>> resultKeys;

    public PreviewKey(String[] str){
        commands = str;
        resultKeys = CaptureController.mPreviewCaptureResult.getKeys();
    }
    @Override
    public void command(){
        CaptureResult.Key<?> resultKey = null;
        for(CaptureResult.Key<?> key : resultKeys){
            if(key.getName().equals(commands[1])) resultKey = key;
        }
        if(resultKey == null) {
            mBufferOut.println("Result key is null");
            return;
        }
        mBufferOut.println(previewKeyValue(resultKey));
        return;
    }
}

