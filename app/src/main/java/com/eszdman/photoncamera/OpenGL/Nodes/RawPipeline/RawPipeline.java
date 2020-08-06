package com.eszdman.photoncamera.OpenGL.Nodes.RawPipeline;

import com.eszdman.photoncamera.OpenGL.GLBasePipeline;
import com.eszdman.photoncamera.OpenGL.GLCoreBlockProcessing;
import com.eszdman.photoncamera.OpenGL.GLFormat;
import com.eszdman.photoncamera.OpenGL.GLInterface;
import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.Render.Parameters;
import com.eszdman.photoncamera.api.Interface;

import java.nio.ByteBuffer;
import java.util.ArrayList;

public class RawPipeline extends GLBasePipeline {
    public float sensivity = 1.f;
    public ByteBuffer rawInput;
    public ArrayList<ByteBuffer> images;
    public ByteBuffer Run(){
        Parameters parameters = Interface.i.parameters;
        GLCoreBlockProcessing glproc = new GLCoreBlockProcessing(parameters.rawSize, new GLFormat(GLFormat.DataType.UNSIGNED_16));
        //GLContext glContext = new GLContext(parameters.rawSize.x,parameters.rawSize.y);
        glint = new GLInterface(glproc);
        glint.parameters = parameters;
        //add(new Debug(R.raw.debugraw,"DebugRaw"));
        add(new AlignAndMerge(R.raw.boxdown22,"AlignAndMerge"));
        //add(new RawSensivity(R.raw.rawsensivity,"RawSensivity"));
        return runAllRaw();
    }
}
