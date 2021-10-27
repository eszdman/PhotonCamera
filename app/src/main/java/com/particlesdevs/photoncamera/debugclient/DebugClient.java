package com.particlesdevs.photoncamera.debugclient;

import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.util.Log;

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
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.List;

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
    CaptureRequest.Builder captureRequestBuilder = null;
    private void ParseControl(String mServerMessage){
        String[] commands = mServerMessage.split(":");
        List<CameraCharacteristics.Key<?>> keys = CaptureController.mCameraCharacteristics.getKeys();
        CaptureController controller = PhotonCamera.getCaptureController();
        List<CaptureRequest.Key<?>> captureKeys = controller.mPreviewRequest.getKeys();
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
            case "DEBUG_SHOT":{
                controller.debugCapture(captureRequestBuilder);
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
        mBufferOut.println();
    }
}
