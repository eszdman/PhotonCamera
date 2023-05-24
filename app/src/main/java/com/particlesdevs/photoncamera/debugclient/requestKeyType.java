package com.particlesdevs.photoncamera.debugclient;

import android.hardware.camera2.CaptureRequest;

public interface requestKeyType {
    static requestKeyType createKeyType(CaptureRequest.Builder builder, CaptureRequest.Key<?> key, String type, String value) {
        switch (type){
            case "LONG":{
                return new requestKeyLong(builder, key, value);
            }
            case "INTEGER":{
                return new requestKeyInteger(builder, key, value);
            }
            case "STRING":{
                return new requestKeyString(builder, key, value);
            }
            case "FLOAT":{
                return new requestKeyFloat(builder, key, value);
            }
            case "DOUBLE":{
                return new requestKeyDouble(builder, key, value);
            }
            default:
                throw new IllegalStateException("Unexpected value: " + type);
        }
    }

    public void setKey();
}
class requestKeyLong implements requestKeyType{
    private CaptureRequest.Builder builder;
    private CaptureRequest.Key<?> key;
    String value;
    public requestKeyLong(CaptureRequest.Builder builder,CaptureRequest.Key<?> key,String value){
        this.builder = builder;
        this.key = key;
        this.value = value;
    }
    @Override
    public void setKey(){
        builder.set((CaptureRequest.Key<Long>) key,Long.parseLong(value));
    }
}
class requestKeyInteger implements requestKeyType{
    private CaptureRequest.Builder builder;
    private CaptureRequest.Key<?> key;
    String value;
    public requestKeyInteger(CaptureRequest.Builder builder,CaptureRequest.Key<?> key,String value){
        this.builder = builder;
        this.key = key;
        this.value = value;
    }
    @Override
    public void setKey(){
        builder.set((CaptureRequest.Key<Integer>) key,Integer.parseInt(value));
    }
}

class requestKeyString implements requestKeyType{
    private CaptureRequest.Builder builder;
    private CaptureRequest.Key<?> key;
    String value;
    public requestKeyString(CaptureRequest.Builder builder,CaptureRequest.Key<?> key,String value){
        this.builder = builder;
        this.key = key;
        this.value = value;
    }
    @Override
    public void setKey(){
        builder.set((CaptureRequest.Key<String>) key, value);
    }
}
class requestKeyFloat implements requestKeyType{
    private CaptureRequest.Builder builder;
    private CaptureRequest.Key<?> key;
    String value;
    public requestKeyFloat(CaptureRequest.Builder builder,CaptureRequest.Key<?> key,String value){
        this.builder = builder;
        this.key = key;
        this.value = value;
    }
    @Override
    public void setKey(){
        builder.set((CaptureRequest.Key<Float>) key,Float.parseFloat(value));
    }
}
class requestKeyDouble implements requestKeyType{
    private CaptureRequest.Builder builder;
    private CaptureRequest.Key<?> key;
    String value;
    public requestKeyDouble(CaptureRequest.Builder builder,CaptureRequest.Key<?> key,String value){
        this.builder = builder;
        this.key = key;
        this.value = value;
    }
    @Override
    public void setKey(){
        builder.set((CaptureRequest.Key<Double>) key,Double.parseDouble(value));

    }
}

