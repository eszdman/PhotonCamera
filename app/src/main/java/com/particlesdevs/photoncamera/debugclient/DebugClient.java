package com.particlesdevs.photoncamera.debugclient;

import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.media.Image;
import android.util.Log;

import com.particlesdevs.photoncamera.capture.CaptureController;

import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ShortBuffer;
import java.util.Arrays;


public class DebugClient {
    private PrintWriter mBufferOut;
    // used to read messages from the server
    private BufferedReader mBufferIn;
    private String mServerMessage;

    private boolean mRun = false;
    public DebugClient(String ip, String port){
        new Thread(() -> {
            receiveMessageFromServer(ip, port);
        }).start();
    }

    private void receiveMessageFromServer(String ip, String port) {
        try {
            mRun = true;
            Log.d("TCP Client", "Connecting...");
            InetAddress serverAddr = InetAddress.getByName(ip);
            //create a socket to make the connection with the server
            Socket socket = new Socket(serverAddr, Integer.parseInt(port));

            try {
                sendMessage(socket);
                receiveMessage(socket);
            } catch (Exception e){e.printStackTrace();}
        } catch (IOException e) {e.printStackTrace();}
    }

    private void receiveMessage(Socket socket) throws IOException {
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
    }
    private void sendMessage(Socket socket) throws IOException {
        //sends the message to the server
        mBufferOut = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
        mBufferOut.println("PhotonCamera Connected!");
    }

    public static void setKey(CaptureRequest.Builder builder,CaptureRequest.Key<?> key, String type, String value){
        try {
            RequestKeyType keySet = RequestKeyType.createKeyType(builder, key, type, value);
            keySet.setKey();
        }catch (Exception e){
            e.toString();
        }
    }

    public static String getObjectString(Object input){
        String name = getClassName(input);
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

    @NotNull
    private static String getClassName(Object input) {
        Class<?> clasObj = input.getClass();
        String name = clasObj.getName();
        return name;
    }

    public static String previewKeyValue(CaptureResult.Key<?> key) {
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
        /*
        String[] commands = mServerMessage.split(":");

        CaptureController controller = PhotonCamera.getCaptureController();
        ArrayList<CaptureRequest.Key<?>> captureKeys = new ArrayList<>(controller.mPreviewInputRequest.getKeys());
        ArrayList<CaptureRequest.Key<?>> captureRequestKeys = new ArrayList<>(CaptureController.mPreviewCaptureRequest.getKeys());
        List<CameraCharacteristics.Key<?>> keys = CaptureController.mCameraCharacteristics.getKeys();
        List<CaptureResult.Key<?>> resultKeys = CaptureController.mPreviewCaptureResult.getKeys();
        //DebugParameters debugParameters = PhotonCamera.getDebugger().debugParameters;
        */

        Command control = Command.getInstance(mServerMessage);
        control.command();

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
