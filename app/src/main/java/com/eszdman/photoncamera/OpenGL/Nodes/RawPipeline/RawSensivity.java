package com.eszdman.photoncamera.OpenGL.Nodes.RawPipeline;

import android.util.Log;

import com.eszdman.photoncamera.OpenGL.GLFormat;
import com.eszdman.photoncamera.OpenGL.GLInterface;
import com.eszdman.photoncamera.OpenGL.GLProg;
import com.eszdman.photoncamera.OpenGL.GLTexture;
import com.eszdman.photoncamera.OpenGL.Nodes.Node;
import com.eszdman.photoncamera.Render.Parameters;
import com.eszdman.photoncamera.api.Interface;

public class RawSensivity extends Node {

    public RawSensivity(int rid, String name) {
        super(rid, name);
    }
    @Override
    public void Run() {
        RawPipeline rawPipeline = (RawPipeline)basePipeline;
        GLInterface glint = rawPipeline.glint;
        GLProg glProg = glint.glprogram;
        Parameters params = glint.parameters;
        Log.d("RawSensivity","RawInput:"+rawPipeline.rawInput.remaining());
        GLTexture glTexture = new GLTexture(params.rawSize, new GLFormat(GLFormat.DataType.UNSIGNED_16),rawPipeline.rawInput);
        glProg.setTexture("RawBuffer",glTexture);
        glProg.setvar("whitelevel", (float)Interface.i.parameters.whitelevel);
        glProg.setvar("PostRawSensivity",rawPipeline.sensivity);
        WorkingTexture = new GLTexture(params.rawSize, new GLFormat(GLFormat.DataType.UNSIGNED_16),null);
    }
}
