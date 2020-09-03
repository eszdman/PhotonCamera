package com.eszdman.photoncamera.OpenGL.Nodes.PostPipeline;

import android.graphics.Point;

import com.eszdman.photoncamera.OpenGL.GLFormat;
import com.eszdman.photoncamera.OpenGL.GLInterface;
import com.eszdman.photoncamera.OpenGL.GLProg;
import com.eszdman.photoncamera.OpenGL.GLTexture;
import com.eszdman.photoncamera.OpenGL.Nodes.Node;
import com.eszdman.photoncamera.Render.Converter;
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
        GLTexture TonemapCoeffs = new GLTexture(new Point(256,1),new GLFormat(GLFormat.DataType.FLOAT_16,1),FloatBuffer.wrap(Interface.getSettings().tonemap));
        glProg.setTexture("TonemapTex",TonemapCoeffs);
        glProg.setTexture("Fullbuffer",super.previousNode.WorkingTexture);
        glProg.setTexture("GainMap",GainMapTex);
        glProg.setvar("RawSizeX",params.rawSize.x);
        glProg.setvar("RawSizeY",params.rawSize.y);
        glProg.setvar("toneMapCoeffs", Converter.CUSTOM_ACR3_TONEMAP_CURVE_COEFFS);
        glProg.setvar("sensorToIntermediate",params.sensorToProPhoto);
        glProg.setvar("intermediateToSRGB",params.proPhotoToSRGB);
        glProg.setvar("gain", (float)Interface.getSettings().gain);
        glProg.setvar("neutralPoint",params.whitepoint);
        float sat =(float)Interface.getSettings().saturation;
        if(Interface.getSettings().cfaPattern == -2) sat = 0.f;
        glProg.setvar("saturation",sat);
        for(int i =0; i<4;i++){
            params.blacklevel[i]/=params.whitelevel;
        }
        glProg.setvar("blackLevel",params.blacklevel);
        super.WorkingTexture = new GLTexture(super.previousNode.WorkingTexture.mSize,new GLFormat(GLFormat.DataType.FLOAT_16,4),null);
    }
}
