package com.eszdman.photoncamera.OpenGL;

import android.annotation.SuppressLint;

import com.eszdman.photoncamera.api.Interface;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class GLInterface {
    GLProg loader;

    @SuppressLint("NewApi")
    static public String loadShader(int fragment) {
        StringBuilder source = new StringBuilder();

        BufferedReader reader = new BufferedReader(new InputStreamReader(Interface.i.mainActivity.getResources().openRawResource(fragment)));
        for (Object line : reader.lines().toArray()) {
            source.append((String) line);
        }
        return source.toString();
    }
}
