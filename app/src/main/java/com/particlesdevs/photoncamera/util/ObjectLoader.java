package com.particlesdevs.photoncamera.util;

import android.content.Context;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class ObjectLoader {
    private final Context context;

    public ObjectLoader(Context context) {
        this.context = context;
    }

    public void SaveObject(Object object, String name) {
        try (
                ObjectOutputStream objectOutputStream = new ObjectOutputStream(context.openFileOutput(name, Context.MODE_PRIVATE))
        ) {
            objectOutputStream.writeObject(object);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Object GetObject(String name) {
        Object output = null;
        try (
                ObjectInputStream objectInputStream = new ObjectInputStream(context.openFileInput(name))
        ) {
            output = objectInputStream.readObject();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        return output;
    }
}
