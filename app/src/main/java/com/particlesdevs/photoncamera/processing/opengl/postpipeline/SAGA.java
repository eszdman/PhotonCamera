package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.processing.ImageFrame;
import com.particlesdevs.photoncamera.processing.opengl.GLFormat;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;

import java.util.ArrayList;

//SUPER
//ANTIALIAS
//GREEN
//ALIGNMENT
public class SAGA extends Node {
    private ArrayList<ImageFrame> images;
    public SAGA() {
        super(0, "SAGA");
    }

    @Override
    public void Compile() {}

    @Override
    public void Run() {
        images = ((PostPipeline)basePipeline).SAGAIN;
        glProg.useProgram(R.raw.sagamerge);
        GLTexture output =  new GLTexture(basePipeline.mParameters.rawSize,new GLFormat(GLFormat.DataType.UNSIGNED_16),images.get(0).buffer);
        GLTexture align = null;

        for(int i = 1; i<images.size();i++){
            GLTexture input =  new GLTexture(basePipeline.mParameters.rawSize,new GLFormat(GLFormat.DataType.UNSIGNED_16),images.get(i).buffer);
            glProg.setTexture("OutputBuffer",output);
            glProg.setTexture("InputBuffer",output);
            glProg.setTexture("AlignBuffer",align);

            input.close();
        }

    }
}
