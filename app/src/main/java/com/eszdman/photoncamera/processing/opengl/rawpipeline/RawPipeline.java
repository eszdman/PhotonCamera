package com.eszdman.photoncamera.processing.opengl.rawpipeline;

import android.media.Image;

import com.eszdman.photoncamera.processing.opengl.GLBasePipeline;
import com.eszdman.photoncamera.processing.opengl.GLCoreBlockProcessing;
import com.eszdman.photoncamera.processing.opengl.GLFormat;
import com.eszdman.photoncamera.processing.opengl.GLInterface;
import com.eszdman.photoncamera.processing.render.Parameters;
import com.eszdman.photoncamera.app.PhotonCamera;

import java.nio.ByteBuffer;
import java.util.ArrayList;

public class RawPipeline extends GLBasePipeline {
    public float sensivity = 1.f;
    public ArrayList<ByteBuffer> images;
    public ArrayList<Image> imageobj;
    public ByteBuffer Run(){
        Parameters parameters = PhotonCamera.getParameters();
        GLCoreBlockProcessing glproc = new GLCoreBlockProcessing(parameters.rawSize, new GLFormat(GLFormat.DataType.UNSIGNED_16));
        //GLContext glContext = new GLContext(parameters.rawSize.x,parameters.rawSize.y);
        glint = new GLInterface(glproc);
        glint.parameters = parameters;
        //add(new Debug(R.raw.debugraw,"DebugRaw"));
        add(new AlignAndMerge(0,"AlignAndMerge"));
        return runAllRaw();
    }
}
