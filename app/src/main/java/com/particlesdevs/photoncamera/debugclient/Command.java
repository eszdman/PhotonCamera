package com.particlesdevs.photoncamera.debugclient;

import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.util.Log;

import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.capture.CaptureController;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import static com.particlesdevs.photoncamera.debugclient.DebugClient.*;

//Keys
//CHARACTERISTICS_KEY
//CHARACTERISTICS_KEYS
//CAPTURE_KEYS
//BUILDER_CREATE
//BUILDER_SET
//PREVIEW_KEY
//PREVIEW_KEYS
//PREVIEW_KEYS_PRINT
//DEBUG_SHOT
public interface Command {
    public void command();
    static Command getInstance(String mServerMessage){
        String[] commands = mServerMessage.split(":");
        switch (commands[0]){
            case "CHARACTERISTICS_KEY":{
                return new CharacteristicsKey(commands);
            }
            case "CHARACTERISTICS_KEYS":{
                return new CharacteristicsKeys(commands);
            }
            case "CAPTURE_KEYS":{
                return new CaptureKeys(commands);
            }
            case "BUILDER_CREATE":{
                return new BuilderCreate(commands);
            }
            case "BUILDER_SET":{
                return new BuilderSet(commands);
            }
            case "PREVIEW_KEY":{
                return new PreviewKey(commands);
            }
            case "PREVIEW_KEYS":{
                return new PreviewKeys(commands);
            }
            case "PREVIEW_REQUEST_KEYS":{
                return new PreviewRequestKeys(commands);
            }
            case "PREVIEW_REQUEST_KEYS_PRINT":{
                return new PreviewRequestKeysPrint(commands);
            }
            case "PREVIEW_KEYS_PRINT":{
                return new PreviewKeysPrint(commands);
            }
            case "DEBUG_SHOT":{
                return new DebugShot(commands);
            }
        }
        return null;
    }

}

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
class CaptureKeys implements Command{
    private String[] commands;
    private PrintWriter mBufferOut;
    CaptureController controller;
    CaptureRequest.Builder captureRequestBuilder = null;
    public CaptureKeys(String[] str){
        commands = str;
        controller = PhotonCamera.getCaptureController();
    }
    @Override
    public void command(){
        captureRequestBuilder = controller.getDebugCaptureRequestBuilder();
        if(captureRequestBuilder == null)
            mBufferOut.println("Error at creating builder!");
        mBufferOut.println("Builder created");
    }
}

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

class PreviewKeys implements Command{
    private  String[] commands;
    private PrintWriter mBufferOut;
    List<CaptureResult.Key<?>> resultKeys;

    public PreviewKeys(String[] str){
        commands = str;
        resultKeys = CaptureController.mPreviewCaptureResult.getKeys();
    }
    @Override
    public void command(){
        StringBuilder keysStr = new StringBuilder();
        for(CaptureResult.Key<?> key : resultKeys){
            keysStr.append(key.getName());
            keysStr.append(" ");
        }
        mBufferOut.println(keysStr.toString());
        return;
    }
}

class PreviewRequestKeys implements Command{
    private PrintWriter mBufferOut;
    private ArrayList<CaptureRequest.Key<?>> captureRequestKeys;

    public PreviewRequestKeys(String[] str){
        captureRequestKeys = new ArrayList<>(CaptureController.mPreviewCaptureRequest.getKeys());
    }
    @Override
    public void command() {
        StringBuilder keysStr = new StringBuilder();
        for(CaptureRequest.Key<?> key : captureRequestKeys){
            keysStr.append(key.getName());
            keysStr.append(" ");
        }
        mBufferOut.println(keysStr.toString());
        return;
    }
}

class PreviewRequestKeysPrint implements Command{
    private ArrayList<CaptureRequest.Key<?>> captureRequestKeys;
    private PrintWriter mBufferOut;

    public PreviewRequestKeysPrint(String[] str){
        captureRequestKeys = new ArrayList<>(CaptureController.mPreviewCaptureRequest.getKeys());
    }
    @Override
    public void command() {
        StringBuilder keysStr = new StringBuilder();
        for(CaptureRequest.Key<?> key : captureRequestKeys){
            keysStr.append(key.getName());
            keysStr.append("=");
            keysStr.append(getObjectString(CaptureController.mPreviewCaptureRequest.get(key)));
            keysStr.append(" ");
        }
        mBufferOut.println(keysStr.toString());
        return;
    }
}

class PreviewKeysPrint implements Command{
    private PrintWriter mBufferOut;
    private List<CaptureResult.Key<?>> resultKeys;

    public PreviewKeysPrint(String[] str){
        resultKeys = CaptureController.mPreviewCaptureResult.getKeys();
    }
    @Override
    public void command() {
        StringBuilder keysStr = new StringBuilder();
        for(CaptureResult.Key<?> key : resultKeys){
            keysStr.append(key.getName());
            keysStr.append("=");
            keysStr.append(previewKeyValue(key));
            keysStr.append(" ");
        }
        mBufferOut.println(keysStr.toString());
        return;
    }
}

class DebugShot implements Command{
    private CaptureController controller;
    private CaptureRequest.Builder captureRequestBuilder = null;

    public DebugShot(String[] str){
        controller = PhotonCamera.getCaptureController();
    }
    @Override
    public void command() {
        controller.runDebug(captureRequestBuilder);
    }
}