package com.eszdman.photoncamera.OpenGL.Scripts;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.util.Log;

import com.eszdman.photoncamera.OpenGL.GLFormat;
import com.eszdman.photoncamera.OpenGL.GLOneScript;
import com.eszdman.photoncamera.OpenGL.GLProg;
import com.eszdman.photoncamera.OpenGL.GLTexture;

public class GaussianResize extends GLOneScript {
    public GaussianResize(Point size,int rid, String name) {
        super(size, null, null, rid, name);
    }

    @Override
    public void StartScript() {
        ScriptParams scriptParams = (ScriptParams)additionalParams;
        GLProg glProg = glOne.glprogram;
        if(scriptParams.input == null) {
            glProg.setTexture("InputBuffer",scriptParams.textureInput);
        } else {
            Log.d("Script:"+Name,"Wrong parameters");
        }
        size = new Point(size.x/4,size.y/4);
        WorkingTexture = new GLTexture(size,new GLFormat(GLFormat.DataType.FLOAT_16,4),null);
    }
}
