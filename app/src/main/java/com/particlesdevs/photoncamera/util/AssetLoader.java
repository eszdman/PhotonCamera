package com.particlesdevs.photoncamera.util;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

public class AssetLoader {
    private final Context context;

    /**
     * Process-wide text-asset cache. Shaders are re-read from the APK on
     * every bind (Amaze alone binds 13 programs x 12 tiles per shot), and the
     * asset set is immutable for the process lifetime, so the decoded strings
     * are cached. Empty results (missing asset) are not cached.
     */
    private static final ConcurrentHashMap<String, String> sTextCache = new ConcurrentHashMap<>();

    public AssetLoader(Context context) {
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

    public InputStream getInputStream(String name) throws IOException {
        return context.getAssets().open(name, AssetManager.ACCESS_BUFFER);
    }
    public String getString(String name) {
        String cached = sTextCache.get(name);
        if (cached != null) {
            return cached;
        }
        String loaded = readString(name);
        if (!loaded.isEmpty()) {
            String prev = sTextCache.putIfAbsent(name, loaded);
            if (prev != null) {
                return prev;
            }
        }
        return loaded;
    }

    private String readString(String name) {
        InputStream initialStream = null;
        try {
            initialStream = context.getAssets().open(name, AssetManager.ACCESS_BUFFER);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (initialStream == null) {
            // Missing asset: callers get an empty string instead of an NPE from
            // the reader (a missing shader must never kill the GL thread).
            return "";
        }
        try {
            BufferedReader br = new BufferedReader(
                    new InputStreamReader(initialStream, StandardCharsets.UTF_8));
            try {
                StringBuilder sb = new StringBuilder();
                String str;
                while ((str = br.readLine()) != null) {
                    sb.append(str).append("\n");
                }
                return sb.toString();
            } finally {
                br.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
    }
}
