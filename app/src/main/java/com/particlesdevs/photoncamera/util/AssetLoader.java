package com.particlesdevs.photoncamera.util;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class AssetLoader {
    private final Context context;
    public AssetLoader(Context context){
        this.context = context;
    }
    public File getFile(String name) throws IOException {
        InputStream initialStream = context.getAssets().open(name, AssetManager.ACCESS_BUFFER);
        byte[] buffer = new byte[initialStream.available()];
        initialStream.read(buffer);
        File targetFile = new File(name);
        OutputStream outStream = new FileOutputStream(targetFile);
        outStream.write(buffer);
        outStream.close();
        return targetFile;
    }
}
