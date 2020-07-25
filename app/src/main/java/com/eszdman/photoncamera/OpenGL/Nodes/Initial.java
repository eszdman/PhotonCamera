package com.eszdman.photoncamera.OpenGL.Nodes;

import com.eszdman.photoncamera.OpenGL.GLFormat;
import com.eszdman.photoncamera.OpenGL.GLInterface;
import com.eszdman.photoncamera.OpenGL.GLProg;
import com.eszdman.photoncamera.OpenGL.GLTexture;
import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.Render.Parameters;
import com.eszdman.photoncamera.api.Interface;

import java.nio.FloatBuffer;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_LINEAR;

public class Initial extends Node {
    public Initial(int rid, String name) {
        super(rid, name);
    }
    @Override
    public void Run() {
        Node Previous = super.previousNode;
        GLInterface glint = basePipeline.glint;
        GLProg glProg = glint.glprogram;
        Parameters params = glint.parameters;
        GLTexture GainMapTex = new GLTexture(params.mapsize, new GLFormat(GLFormat.DataType.FLOAT_16,4),FloatBuffer.wrap(params.gainmap),GL_LINEAR,GL_CLAMP_TO_EDGE);
        glProg.setTexture("Fullbuffer",super.previousNode.WorkingTexture);
        glProg.setTexture("GainMap",GainMapTex);
        glProg.servar("RawSizeX",params.rawSize.x);
        glProg.servar("RawSizeY",params.rawSize.y);
        glProg.servar("sensorToIntermediate",params.sensorToProPhoto);
        glProg.servar("intermediateToSRGB",params.proPhotoToSRGB);
        glProg.servar("gain", (float)Interface.i.settings.gain);
        glProg.servar("neutralPoint",params.whitepoint);
        float sat =(float)Interface.i.settings.saturation;
        if(Interface.i.settings.cfaPattern == -2) sat = 0.f;
        glProg.servar("saturation",sat);
        for(int i =0; i<4;i++){
            params.blacklevel[i]/=params.whitelevel;
        }
        glProg.servar("blackLevel",params.blacklevel);
        super.WorkingTexture = new GLTexture(super.previousNode.WorkingTexture.mSize,new GLFormat(GLFormat.DataType.FLOAT_16,4),null);
    }
}
