package com.particlesdevs.photoncamera.debugclient;

import android.annotation.SuppressLint;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.media.Image;
import android.util.Log;

import com.particlesdevs.photoncamera.api.CameraReflectionApi;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.capture.CaptureController;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static android.hardware.camera2.CaptureResult.STATISTICS_HOT_PIXEL_MAP;

public class DebugClient {
    private PrintWriter mBufferOut;
    // used to read messages from the server
    private BufferedReader mBufferIn;
    private String mServerMessage;

    private boolean mRun = false;
    public DebugClient(String ip, String port){
        new Thread(() -> {
            try {
            mRun = true;
            Log.d("TCP Client", "Connecting...");
            InetAddress serverAddr = InetAddress.getByName(ip);
            //create a socket to make the connection with the server
            Socket socket = new Socket(serverAddr, Integer.parseInt(port));

            try {

                //sends the message to the server
                mBufferOut = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);

                mBufferOut.println("PhotonCamera Connected!");

                //receives the message which the server sends back
                mBufferIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));


                //in this while the client listens for the messages sent by the server
                while (mRun) {

                    mServerMessage = mBufferIn.readLine();//0x0a
                    if (mServerMessage != null) {
                        //call the method messageReceived from MyActivity class
                        ParseControl(mServerMessage);

                    }

                }

                } catch (Exception e){e.printStackTrace();}
            } catch (IOException e) {e.printStackTrace();}
        }).start();
    }
    private void setKey(CaptureRequest.Builder builder,CaptureRequest.Key<?> key, String type, String value){
        switch (type){
            case "LONG":{
                builder.set((CaptureRequest.Key<Long>)key,Long.parseLong(value));
            }
            case "INTEGER":{
                builder.set((CaptureRequest.Key<Integer>)key,Integer.parseInt(value));
            }
            case "STRING":{
                builder.set((CaptureRequest.Key<String>)key,value);
            }
            case "FLOAT":{
                builder.set((CaptureRequest.Key<Float>)key,Float.parseFloat(value));
            }
            case "DOUBLE":{
                builder.set((CaptureRequest.Key<Double>)key,Double.parseDouble(value));
            }
        }
    }
    private String getObjectString(Object input){
        Class clasObj = input.getClass();
        String name = clasObj.getName();
        switch (name) {
            case "[B": {
                return (Arrays.toString((byte[]) input));
            }
            case "[F": {
                return (Arrays.toString((float[]) input));
            }
            case "[I": {
                return (Arrays.toString((int[]) input) );
            }
            case "[J": {
                return (Arrays.toString((long[]) input));
            }
            case "[D": {
                return (Arrays.toString((double[]) input));
            }
            case "[S": {
                return (Arrays.toString((short[]) input));
            }
            case "[C": {
                return (Arrays.toString((char[]) input));
            }
            case "[Landroid.hardware.camera2.params.Face;": {
                return (Arrays.toString((android.hardware.camera2.params.Face[]) input));
            }
            case "[Landroid.util.Pair;": {
                return (Arrays.toString((android.util.Pair<?,?>[]) input));
            }
            case "[Landroid.util.Rational;": {
                return (Arrays.toString((android.util.Rational[]) input));
            }
            case "[Landroid.hardware.camera2.params.MeteringRectangle;": {
                return (Arrays.toString((android.hardware.camera2.params.MeteringRectangle[]) input));
            }
            case "[Landroid.graphics.Point;": {
                return (Arrays.toString((android.graphics.Point[]) input));
            }
            default: {
                return (input.toString());
            }
        }
    }
    private String previewKeyValue(CaptureResult.Key<?> key) {
        Object obj = CaptureController.mPreviewCaptureResult.get(key);
        return getObjectString(obj);
    }
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
    CaptureRequest.Builder captureRequestBuilder = null;
    private void ParseControl(String mServerMessage){
        String[] commands = mServerMessage.split(":");

        CaptureController controller = PhotonCamera.getCaptureController();
        ArrayList<CaptureRequest.Key<?>> captureKeys = new ArrayList<>(controller.mPreviewRequest.getKeys());
        List<CameraCharacteristics.Key<?>> keys = CaptureController.mCameraCharacteristics.getKeys();
        List<CaptureResult.Key<?>> resultKeys = CaptureController.mPreviewCaptureResult.getKeys();
        //DebugParameters debugParameters = PhotonCamera.getDebugger().debugParameters;
        switch (commands[0]){
            case "CHARACTERISTICS_KEY":{
                Log.v("DebugClient","KeyRequired:"+commands[1]);
                for(CameraCharacteristics.Key<?> key : keys){

                    Log.v("DebugClient","GetKey:"+key.getName());
                    if(commands[1].equals(key.getName())){
                        Log.v("DebugClient","GotKey");
                        mBufferOut.println(CaptureController.mCameraCharacteristics.get(key).toString());
                        return;
                    }
                }
                break;
            }
            case "CHARACTERISTICS_KEYS":{
                StringBuilder keysStr = new StringBuilder();
                for(CameraCharacteristics.Key<?> key : keys){
                    keysStr.append(key.getName());
                    keysStr.append("\n");
                }
                mBufferOut.println(keysStr.toString());
                return;
            }
            case "CAPTURE_KEYS":{
                StringBuilder keysStr = new StringBuilder();
                for(CaptureRequest.Key<?> key : captureKeys){
                    keysStr.append(key.getName());
                    keysStr.append("\n");
                }
                mBufferOut.println(keysStr.toString());
                return;
            }
            case "BUILDER_CREATE":{
                captureRequestBuilder = controller.getDebugCaptureRequestBuilder();
                if(captureRequestBuilder == null)
                    mBufferOut.println("Error at creating builder!");
                mBufferOut.println("Builder created");
                return;
            }
            case "BUILDER_SET":{
                CaptureRequest.Key<?> requestKey = null;
                for(CaptureRequest.Key<?> key : captureKeys){
                    if(key.getName().equals(commands[1])) requestKey = key;
                }
                if(requestKey == null) {
                    mBufferOut.println("Request key is null");
                    return;
                }
                setKey(captureRequestBuilder,requestKey,commands[2],commands[3]);
                return;
            }
            case "PREVIEW_KEY":{
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
            case "PREVIEW_KEYS":{
                StringBuilder keysStr = new StringBuilder();
                for(CaptureResult.Key<?> key : resultKeys){
                    keysStr.append(key.getName());
                    keysStr.append(" ");
                }
                mBufferOut.println(keysStr.toString());
                return;
            }
            case "PREVIEW_KEYS_PRINT":{
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
            case "DEBUG_SHOT":{
                controller.runDebug(captureRequestBuilder);
                break;
            }
        }
    }
    public void sendRaw(Image input){
        int width = input.getPlanes()[0].getRowStride() /
                input.getPlanes()[0].getPixelStride();
        int height = input.getHeight();
        mBufferOut.print((short)width);
        mBufferOut.print(",");
        mBufferOut.print((short)height);
        mBufferOut.print(",");
        ShortBuffer buffer = input.getPlanes()[0].getBuffer().asShortBuffer();
        short[] values = new short[input.getPlanes()[0].getBuffer().remaining()/2];
        buffer.get(values);
        for (short value : values) {
            mBufferOut.print(value);
            mBufferOut.print(",");
        }
        mBufferOut.println(" ");
        mBufferOut.flush();
    }
}
