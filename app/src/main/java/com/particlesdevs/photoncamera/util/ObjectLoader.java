package com.particlesdevs.photoncamera.util;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class ObjectLoader {
    private Context context;
    public ObjectLoader(Context context){
        this.context = context;
    }
    public void SaveObject(Object object, String name){
        try {
            FileOutputStream fileOutputStream = context.openFileOutput(name, Context.MODE_PRIVATE);
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream);
            objectOutputStream.writeObject(object);
            objectOutputStream.close();
            fileOutputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public Object GetObject(String name){
        Object output = null;
        try {
            FileInputStream fileInputStream = context.openFileInput(name);
            ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream);
            output = objectInputStream.readObject();
            objectInputStream.close();
            fileInputStream.close();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        return output;
    }
}
