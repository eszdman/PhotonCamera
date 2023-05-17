package com.particlesdevs.photoncamera.debugclient;

import android.hardware.camera2.CaptureRequest;

public interface requestKeyType {
    static requestKeyType createKeyType(CaptureRequest.Builder builder, CaptureRequest.Key<?> key, String type, String value) {
        switch (type){
            case "LONG":{
                return (requestKeyType) new requestKeyLong(builder, key, value);
            }
            case "INTEGER":{
                return (requestKeyType) new requestKeyInterger(builder, key, value);
            }
            case "STRING":{
                return (requestKeyType) new requestKeyString(builder, key, value);
            }
            case "FLOAT":{
                return (requestKeyType) new requestKeyFloat(builder, key, value);
            }
            case "DOUBLE":{
                return (requestKeyType) new requestKeyDouble(builder, key, value);
            }
            break;
            default:
                throw new IllegalStateException("Unexpected value: " + type);
        }
    }

    void setKey();
}
public class requestKeyLong{
    private CaptureRequest.Builder builder;
    private CaptureRequest.Key<?> key;
    String value;
    public requestKeyLong(CaptureRequest.Builder builder,CaptureRequest.Key<?> key,String value){
        this.builder = builder;
        this.key = key;
        this.value = value;
    }
    @Override
    void setKey(){
        builder.set((CaptureRequest.Key<Long>) key,Long.parseLong(value));
    }
}
public class requestKeyInterger{
    private CaptureRequest.Builder builder;
    private CaptureRequest.Key<?> key;
    String value;
    public requestKeyInterger(CaptureRequest.Builder builder,CaptureRequest.Key<?> key,String value){
        this.builder = builder;
        this.key = key;
        this.value = value;
    }
    @Override
    void setKey(){
        builder.set((CaptureRequest.Key<Integer>) key,Integer.parseInt(value));
    }
}

public class requestKeyString{
    private CaptureRequest.Builder builder;
    private CaptureRequest.Key<?> key;
    String value;
    public requestKeyString(CaptureRequest.Builder builder,CaptureRequest.Key<?> key,String value){
        this.builder = builder;
        this.key = key;
        this.value = value;
    }
    @Override
    void setKey(){
        builder.set((CaptureRequest.Key<String>) key, value);
    }
}
public class requestKeyFloat{
    private CaptureRequest.Builder builder;
    private CaptureRequest.Key<?> key;
    String value;
    public requestKeyFloat(CaptureRequest.Builder builder,CaptureRequest.Key<?> key,String value){
        this.builder = builder;
        this.key = key;
        this.value = value;
    }
    @Override
    void setKey(){
        builder.set((CaptureRequest.Key<Float>) key,Float.parseFloat(value));
    }
}
public class requestKeyDouble{
    private CaptureRequest.Builder builder;
    private CaptureRequest.Key<?> key;
    String value;
    public requestKeyDouble(CaptureRequest.Builder builder,CaptureRequest.Key<?> key,String value){
        this.builder = builder;
        this.key = key;
        this.value = value;
    }
    @Override
    void setKey(){
        builder.set((CaptureRequest.Key<Double>) key,Double.parseDouble(value));

    }
}

