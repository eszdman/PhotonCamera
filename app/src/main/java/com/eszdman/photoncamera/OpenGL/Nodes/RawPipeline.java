package com.eszdman.photoncamera.OpenGL.Nodes;

import android.graphics.Bitmap;

import com.eszdman.photoncamera.OpenGL.GLCoreBlockProcessing;
import com.eszdman.photoncamera.OpenGL.GLFormat;
import com.eszdman.photoncamera.OpenGL.GLInterface;
import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.Render.Parameters;

import java.nio.ByteBuffer;

import static com.eszdman.photoncamera.api.ImageSaver.outimg;

public class RawPipeline extends BasePipeline {
    public float sensivity = 1.f;
    public ByteBuffer rawInput;
    public GLCoreBlockProcessing glproc;
    public ByteBuffer Run(Parameters parameters){
        //GLCoreBlockProcessing glproc = new GLCoreBlockProcessing(parameters.rawSize, new GLFormat(GLFormat.DataType.UNSIGNED_16));
        glint = new GLInterface(glproc);
        glint.parameters = parameters;
        //add(new Debug(R.raw.debugraw,"DebugRaw"));
        add(new RawSensivity(R.raw.rawsensivity,"RawSensivity"));
        return runAllRaw();
    }
}
