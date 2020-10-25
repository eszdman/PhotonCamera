package com.eszdman.photoncamera.processing.opengl;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.hardware.camera2.CaptureResult;
import android.util.Log;

import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.processing.opengl.nodes.Node;
import com.eszdman.photoncamera.processing.parameters.IsoExpoSelector;
import com.eszdman.photoncamera.ui.camera.CameraFragment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import javax.microedition.khronos.opengles.GL;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_LINEAR;
import static com.eszdman.photoncamera.processing.ImageSaver.imageFileToSave;

public class GLUtils {
    private final GLProg glProg;
    private GLCoreBlockProcessing glProcessing;

    public GLUtils(GLCoreBlockProcessing blockProcessing) {
        glProg = blockProcessing.mProgram;
        glProcessing = blockProcessing;
    }

    public GLTexture blur(GLTexture in, double size){
        glProg.useProgram("#version 300 es\n" +
                "#define tvar "+in.mFormat.getTemVar()+"\n" +
                "#define tscal "+in.mFormat.getScalar()+"\n" +
                "precision mediump float;\n" +
                "precision mediump "+in.mFormat.getTemSamp()+";\n" +
                "uniform "+in.mFormat.getTemSamp()+" InputBuffer;\n" +
                "uniform int yOffset;\n" +
                "out tvar Output;\n" +
                "#define size1 "+((double)(size)*0.5)+"\n" +
                "#define MSIZE1 "+(int)size+"\n" +
                "float normpdf(in float x, in float sigma){return 0.39894*exp(-0.5*x*x/(sigma*sigma))/sigma;}\n" +
                "void main() {\n" +
                "    ivec2 xy = ivec2(gl_FragCoord.xy);\n" +
                "    xy+=ivec2(0,yOffset);\n" +
                "    const int kSize = (MSIZE1-1)/2;\n" +
                //"    float kernel[MSIZE1];\n" +
                "    tvar mask = tvar(0.0);\n" +
                "    float pdfsize = 0.0;\n" +
                //"    for (int j = 0; j <= kSize; ++j) kernel[kSize+j] = kernel[kSize-j] = normpdf(float(j), size1);\n" +
                //"    for (int i=-kSize; i <= kSize; ++i){\n" +
                "        for (int j=-kSize; j <= kSize; ++j){\n" +
                //"            float pdf = kernel[kSize+j];\n" +
                "            tvar inp = tvar(texelFetch(InputBuffer, (xy+ivec2(0,j)), 0)"+in.mFormat.getTemExt()+");\n" +
                "            if(length(inp"+in.mFormat.getLimExt()+") > 1.0/1000.0) {\n"+
                "            float pdf = normpdf(float(abs(j)), size1);\n" +
                "            mask+=inp*pdf;\n" +
                "            pdfsize+=pdf;\n" +
                "            }\n" +
                "        }\n" +
                //"    }\n" +
                "    mask/=pdfsize;\n" +
                "    Output = mask;\n" +
                "}\n");
        glProg.setTexture("InputBuffer",in);
        GLTexture out = new GLTexture(in);
        glProg.drawBlocks(out);
        glProg.close();
        glProg.useProgram("#version 300 es\n" +
                "#define tvar "+out.mFormat.getTemVar()+"\n" +
                "#define tscal "+out.mFormat.getScalar()+"\n" +
                "precision mediump float;\n" +
                "precision mediump "+out.mFormat.getTemSamp()+";\n" +
                "uniform "+in.mFormat.getTemSamp()+" InputBuffer;\n" +
                "uniform int yOffset;\n" +
                "out tvar Output;\n" +
                "#define size1 "+((double)(size)*0.5)+"\n" +
                "#define MSIZE1 "+(int)size+"\n" +
                "float normpdf(in float x, in float sigma){return 0.39894*exp(-0.5*x*x/(sigma*sigma))/sigma;}\n" +
                "void main() {\n" +
                "    ivec2 xy = ivec2(gl_FragCoord.xy);\n" +
                "    xy+=ivec2(0,yOffset);\n" +
                "    const int kSize = (MSIZE1-1)/2;\n" +
                //"    float kernel[MSIZE1];\n" +
                "    tvar mask = tvar(0.0);\n" +
                "    float pdfsize = 0.0;\n" +
                //"    for (int j = 0; j <= kSize; ++j) kernel[kSize+j] = kernel[kSize-j] = normpdf(float(j), size1);\n" +
                "    for (int i=-kSize; i <= kSize; ++i){\n" +
                //"        for (int j=-kSize; j <= kSize; ++j){\n" +
                //"            float pdf = kernel[kSize+i];\n" +
                "            tvar inp = tvar(texelFetch(InputBuffer, (xy+ivec2(i,0)), 0)"+out.mFormat.getTemExt()+");\n" +
                "            if(length(inp"+in.mFormat.getLimExt()+") > 1.0/1000.0) {\n"+
                "            float pdf = normpdf(float(abs(i)), size1);\n" +
                "            mask+=inp*pdf;\n" +
                "            pdfsize+=pdf;\n" +
                "            }\n" +
                "        }\n" +
                //"    }\n" +
                "    mask/=pdfsize;\n" +
                "    Output = mask;\n" +
                "}\n");
        glProg.setTexture("InputBuffer",out);
        GLTexture out2 = new GLTexture(out);
        glProg.drawBlocks(out2);
        out.close();
        glProg.close();
        return out2;
    }
    public GLTexture fastdown(GLTexture in, int k){
        return fastdown(in,k,(double)k*0.3);
    }
    public GLTexture fastdown(GLTexture in, int k,double blur){
        glProg.useProgram("#version 300 es\n" +
                "#define tvar "+in.mFormat.getTemVar()+"\n" +
                "#define tscal "+in.mFormat.getScalar()+"\n" +
                "precision mediump float;\n" +
                "precision mediump "+in.mFormat.getTemSamp()+";\n" +
                "uniform "+in.mFormat.getTemSamp()+" InputBuffer;\n" +
                "uniform int yOffset;\n" +
                "out tvar Output;\n" +
                "#define size1 ("+blur+")\n" +
                "#define transpose ("+(int)((4.5/k)+1)+")\n" +
                "#define resize ("+k+")\n" +
                "#define MSIZE1 5\n" +
                "float normpdf(in float x, in float sigma){return 0.39894*exp(-0.5*x*x/(sigma*sigma))/sigma;}\n" +
                "void main() {\n" +
                "    ivec2 xy = ivec2(gl_FragCoord.xy);\n" +
                "    xy+=ivec2(0,yOffset);\n" +
                "    xy*=ivec2(1,resize);\n" +
                "    const int kSize = (MSIZE1-1)/2;\n" +
                "    tvar mask = tvar(0.0);\n" +
                "    float pdfsize = 0.0;\n" +
                "        for (int j=-kSize; j <= kSize; ++j){\n" +
                "            tvar inp = tvar(texelFetch(InputBuffer, (xy+ivec2(0,j*transpose)), 0)"+in.mFormat.getTemExt()+");\n" +
                "            if(length(inp"+in.mFormat.getLimExt()+") > 1.0/1000.0) {\n"+
                "            float pdf = normpdf(float(abs(j)), size1);\n" +
                "            mask+=inp*pdf;\n" +
                "            pdfsize+=pdf;\n" +
                "            }\n" +
                "        }\n" +
                "    mask/=pdfsize;\n" +
                "    Output = mask;\n" +
                "}\n");
        glProg.setTexture("InputBuffer",in);
        GLTexture out = new GLTexture(in.mSize.x,in.mSize.y/2,in.mFormat,null);
        glProg.drawBlocks(out);
        glProg.close();
        glProg.useProgram("#version 300 es\n" +
                "#define tvar "+out.mFormat.getTemVar()+"\n" +
                "#define tscal "+out.mFormat.getScalar()+"\n" +
                "precision mediump float;\n" +
                "precision mediump "+out.mFormat.getTemSamp()+";\n" +
                "uniform "+in.mFormat.getTemSamp()+" InputBuffer;\n" +
                "uniform int yOffset;\n" +
                "out tvar Output;\n" +
                "#define size1 ("+blur+")\n" +
                "#define transpose ("+(int)((4.5/k)+1)+")\n" +
                "#define MSIZE1 5\n" +
                "#define resize ("+k+")\n" +
                "float normpdf(in float x, in float sigma){return 0.39894*exp(-0.5*x*x/(sigma*sigma))/sigma;}\n" +
                "void main() {\n" +
                "    ivec2 xy = ivec2(gl_FragCoord.xy);\n" +
                "    xy+=ivec2(0,yOffset);\n" +
                "    xy*=ivec2(resize,1);\n" +
                "    const int kSize = (MSIZE1-1)/2;\n" +
                "    tvar mask = tvar(0.0);\n" +
                "    float pdfsize = 0.0;\n" +
                "    for (int i=-kSize; i <= kSize; ++i){\n" +
                "            tvar inp = tvar(texelFetch(InputBuffer, (xy+ivec2(i*transpose,0)), 0)"+out.mFormat.getTemExt()+");\n" +
                "            if(length(inp"+in.mFormat.getLimExt()+") > 1.0/1000.0) {\n"+
                "            float pdf = normpdf(float(abs(i)), size1);\n" +
                "            mask+=inp*pdf;\n" +
                "            pdfsize+=pdf;\n" +
                "            }\n" +
                "        }\n" +
                "    mask/=pdfsize;\n" +
                "    Output = mask;\n" +
                "}\n");
        glProg.setTexture("InputBuffer",out);
        GLTexture out2 = new GLTexture(in.mSize.x/2,in.mSize.y/2,in.mFormat,null);
        glProg.drawBlocks(out2);
        out.close();
        glProg.close();
        return out2;
    }

    public GLTexture gaussdown(GLTexture in, int k){
        return gaussdown(in,k,(double)k*0.3);
    }
    public GLTexture gaussdown(GLTexture in, int k,double blur){
        glProg.useProgram("#version 300 es\n" +
                "#define tvar "+in.mFormat.getTemVar()+"\n" +
                "#define tscal "+in.mFormat.getScalar()+"\n" +
                "uniform "+in.mFormat.getTemSamp()+" InputBuffer;\n" +
                "uniform int yOffset;\n" +
                "out tvar Output;\n" +
                //"#define size1 ("+(double)k*0.3+")\n" +
                "#define size1 ("+blur+")\n" +
                "#define transpose ("+(int)((4.5/k)+1)+")\n" +
                "#define MSIZE1 5\n" +
                "#define resize ("+k+")\n" +
                "float normpdf(in float x, in float sigma){return 0.39894*exp(-0.5*x*x/(sigma*sigma))/sigma;}\n" +
                "void main() {\n" +
                "    ivec2 xy = ivec2(gl_FragCoord.xy);\n" +
                "    xy+=ivec2(0,yOffset);\n" +
                "    xy*=resize;\n" +
                "    const int kSize = (MSIZE1-1)/2;\n" +
                "    float kernel[MSIZE1];\n" +
                "    tvar mask = tvar(0.0);\n" +
                "    float pdfsize = 0.0;\n" +
                "    for (int j = 0; j <= kSize; ++j) kernel[kSize+j] = kernel[kSize-j] = normpdf(float(j), size1);\n" +
                "    for (int i=-kSize; i <= kSize; ++i){\n" +
                "        for (int j=-kSize; j <= kSize; ++j){\n" +
                "            float pdf = kernel[kSize+j]*kernel[kSize+i];\n" +
                "            vec4 inp = texelFetch(InputBuffer, (xy+ivec2(i*transpose,j*transpose)), 0);\n" +
                "            if(length(inp) > 1.0/10000.0){\n" +
                "                mask+=tvar(inp)*pdf;\n" +
                "                pdfsize+=pdf;\n" +
                "            }\n" +
                "        }\n" +
                "    }\n" +
                "    mask/=pdfsize;\n" +
                "    Output = mask;\n" +
                "}\n");
        glProg.setTexture("InputBuffer",in);
        GLTexture out = new GLTexture((in.mSize.x/k) + k-1,(in.mSize.y/k) + k-1,in.mFormat,null);
        glProg.drawBlocks(out);
        glProg.close();
        return out;
    }

    public GLTexture upscale(GLTexture in, int k){
        glProg.useProgram("#version 300 es\n" +
                "#define tvar "+in.mFormat.getTemVar()+"\n" +
                "#define tscal "+in.mFormat.getScalar()+"\n" +
                "uniform "+in.mFormat.getTemSamp()+" InputBuffer;\n" +
                "uniform int yOffset;\n" +
                "uniform ivec2 size;" +
                "uniform ivec2 sizein;" +
                "out tvar Output;\n" +
                "#define resize ("+k+")\n" +
                //"float normpdf(in float x, in float sigma){return 0.39894*exp(-0.5*x*x/(sigma*sigma))/sigma;}\n
                /*
                "tvar interpolate(vec2 coords){\n" +
                "vec2 fltin = coords*vec2(sizein);\n" +
                "ivec2 coordsin = ivec2(fltin);\n" +
                "fltin-=vec2(coordsin)" +
                //"if(length(fltin) == 0.0{\n" +
                //    "return tvar(texelFetch(InputBuffer, (coordsin), 0)"+in.mFormat.getTemExt()+");\n" +
                //    "}\n" +
                "return tvar(texelFetch(InputBuffer, (coordsin), 0)"+in.mFormat.getTemExt()+")" +
                "+(tvar(texelFetch(InputBuffer, (coordsin+ivec2(0,0)), 0)"+in.mFormat.getTemExt()+")" +
                "-tvar(texelFetch(InputBuffer, (coordsin+ivec2(0,0)), 0)"+in.mFormat.getTemExt()+"))*fltin.x" +
                "+(tvar(texelFetch(InputBuffer, (coordsin+ivec2(0,0)), 0)"+in.mFormat.getTemExt()+")" +
                "-tvar(texelFetch(InputBuffer, (coordsin+ivec2(0,0)), 0)"+in.mFormat.getTemExt()+"))*fltin.y;" +
                "\n" +
                "}\n" +
                */
                "#import interpolation\n" +
                "void main() {\n" +
                "    ivec2 xy = ivec2(gl_FragCoord.xy);\n" +
                "    xy+=ivec2(resize/2,yOffset+resize/2);\n" +
                //"    xy/=resize;\n" +
                //"    Output = tvar(texelFetch(InputBuffer, (xy), 0)" +in.mFormat.getTemExt()+");\n" +
                "    Output = tvar(textureBicubic(InputBuffer, (vec2(xy)/vec2(size)))"+in.mFormat.getTemExt()+");\n" +
                "}\n");
        glProg.setTexture("InputBuffer",in);
        glProg.setVar("size",in.mSize.x*k,in.mSize.y*k);
        //glProg.setVar("sizein",in.mSize.x*k,in.mSize.y*k);
        GLTexture out = new GLTexture(in.mSize.x*k,in.mSize.y*k,in.mFormat,null);
        glProg.drawBlocks(out);
        glProg.close();
        //return blur(out,k-1);
        return out;
    }
    public GLTexture downscale(GLTexture in, int k){
        glProg.useProgram("#version 300 es\n" +
                "#define tvar "+in.mFormat.getTemVar()+"\n" +
                "#define tscal "+in.mFormat.getScalar()+"\n" +
                "uniform "+in.mFormat.getTemSamp()+" InputBuffer;\n" +
                "uniform int yOffset;\n" +
                "uniform ivec2 size;" +
                "uniform ivec2 sizein;" +
                "out tvar Output;\n" +
                "#define resize ("+k+")\n" +
                //"float normpdf(in float x, in float sigma){return 0.39894*exp(-0.5*x*x/(sigma*sigma))/sigma;}\n
                /*
                "tvar interpolate(vec2 coords){\n" +
                "vec2 fltin = coords*vec2(sizein);\n" +
                "ivec2 coordsin = ivec2(fltin);\n" +
                "fltin-=vec2(coordsin)" +
                //"if(length(fltin) == 0.0{\n" +
                //    "return tvar(texelFetch(InputBuffer, (coordsin), 0)"+in.mFormat.getTemExt()+");\n" +
                //    "}\n" +
                "return tvar(texelFetch(InputBuffer, (coordsin), 0)"+in.mFormat.getTemExt()+")" +
                "+(tvar(texelFetch(InputBuffer, (coordsin+ivec2(0,0)), 0)"+in.mFormat.getTemExt()+")" +
                "-tvar(texelFetch(InputBuffer, (coordsin+ivec2(0,0)), 0)"+in.mFormat.getTemExt()+"))*fltin.x" +
                "+(tvar(texelFetch(InputBuffer, (coordsin+ivec2(0,0)), 0)"+in.mFormat.getTemExt()+")" +
                "-tvar(texelFetch(InputBuffer, (coordsin+ivec2(0,0)), 0)"+in.mFormat.getTemExt()+"))*fltin.y;" +
                "\n" +
                "}\n" +
                */
                "void main() {\n" +
                "    ivec2 xy = ivec2(gl_FragCoord.xy);\n" +
                "    xy+=ivec2(0,yOffset+0);\n" +
                "    xy*=resize;\n" +
                "    Output = tvar(texelFetch(InputBuffer, (xy), 0)"+in.mFormat.getTemExt()+");\n" +
                //"    Output = tvar(texture(InputBuffer, (vec2(xy)/vec2(size)))"+in.mFormat.getTemExt()+");\n" +
                "}\n");
        glProg.setTexture("InputBuffer",in);
        //glProg.setVar("size",in.mSize.x*k,in.mSize.y*k);
        //glProg.setVar("sizein",in.mSize.x*k,in.mSize.y*k);
        GLTexture out = new GLTexture((in.mSize.x/k) + k-1,(in.mSize.y/k) + k-1,in.mFormat,null);
        glProg.drawBlocks(out);
        glProg.close();
        //return blur(out,k-1);
        return out;
    }
    public GLTexture median(GLTexture in,Point transposing){
        glProg.useProgram(R.raw.medianfilter);
        glProg.setTexture("InputBuffer", in);
        glProg.setVar("transpose",transposing);
        GLTexture output = new GLTexture(in);
        glProg.drawBlocks(output);
        return output;
    }
    public GLTexture mpy(GLTexture in, float[] vecmat){
        String vecext = "vec3";
        if(vecmat.length == 9) vecext = "mat3";
        glProg.useProgram("#version 300 es\n" +
                "precision highp "+in.mFormat.getTemSamp()+";\n" +
                "precision highp float;\n" +
                "#define tvar "+in.mFormat.getTemVar()+"\n" +
                "#define tscal "+in.mFormat.getScalar()+"\n" +
                "uniform "+in.mFormat.getTemSamp()+" InputBuffer;\n" +
                "uniform "+vecext+" colorvec;\n" +
                "uniform int yOffset;\n" +
                "out tvar Output;\n" +
                "void main() {\n" +
                "    ivec2 xy = ivec2(gl_FragCoord.xy);\n" +
                "    xy+=ivec2(0,yOffset);\n" +
                "    Output = tvar(texelFetch(InputBuffer, xy, 0).rgb*colorvec,1.0);\n" +
                "}\n");
        glProg.setTexture("InputBuffer",in);
        glProg.setVar("colorvec",vecmat);
        GLTexture out = new GLTexture(in);
        glProg.drawBlocks(out);
        glProg.close();
        return out;
    }
    //Linear operation between 2 textures
    public GLTexture ops(GLTexture in1,GLTexture in2, String operation){
        return ops(in1,in2,operation,"");
    }
    public GLTexture ops(GLTexture in1,GLTexture in2, String operation,String operation2){
        glProg.useProgram("#version 300 es\n" +
                "precision highp "+in1.mFormat.getTemSamp()+";\n" +
                "precision highp float;\n" +
                "#define tvar "+in1.mFormat.getTemVar()+"\n" +
                "#define tscal "+in1.mFormat.getScalar()+"\n" +
                "uniform "+in1.mFormat.getTemSamp()+" InputBuffer;\n" +
                "uniform "+in2.mFormat.getTemSamp()+" InputBuffer2;\n" +
                "uniform int yOffset;\n" +
                "out tvar Output;\n" +
                "void main() {\n" +
                "    ivec2 xy = ivec2(gl_FragCoord.xy);\n" +
                "    xy+=ivec2(0,yOffset);\n" +
                "    tvar in1 = (texelFetch(InputBuffer, xy, 0));\n" +
                "    tvar in2 = (texelFetch(InputBuffer2, xy, 0));\n" +
                "    Output = tvar("+operation+")"+operation2+";\n" +
                "}\n");
        glProg.setTexture("InputBuffer",in1);
        glProg.setTexture("InputBuffer2",in2);
        GLTexture out = new GLTexture(in1);
        glProg.drawBlocks(out);
        glProg.close();
        return out;
    }
    public static class Pyramid {
        public GLTexture[] gauss;
        public GLTexture[] laplace;
        public void releasePyramid(){
            for (GLTexture tex : gauss) {
                tex.close();
            }
            for (GLTexture tex : laplace) {
                tex.close();
            }
        }
    }
    public Pyramid createPyramid(int levels, GLTexture input){
        return createPyramid(levels,2,input);
    }
    public Pyramid createPyramid(int levels, int step, GLTexture input){

        GLTexture[] downscaled = new GLTexture[levels];
        downscaled[0] = input;
        GLTexture[] upscale = new GLTexture[downscaled.length - 1];
        for (int i = 1; i < downscaled.length; i++) {
            //downscaled[i] = downscale(blur(downscaled[i - 1],5),step);
            //downscaled[i] = gaussdown(downscaled[i - 1],step,0.1);
            downscaled[i] = gaussdown(downscaled[i - 1],step,0.6);
        }
        for (int i = 0; i < upscale.length; i++) {
            //upscale[i] = blur(upscale(downscaled[i + 1],step),1);
            upscale[i] = (upscale(downscaled[i + 1],step));
        }
         GLTexture[] diff = new GLTexture[upscale.length];
        glProg.useProgram("" +
                "#version 300 es\n" +
                "precision mediump float;\n" +
                "uniform sampler2D target;\n" +
                "uniform sampler2D base;\n" +
                "out vec3 result;\n" +
                "uniform int yOffset;\n" +
                "void main() {\n" +
                "    ivec2 xyCenter = ivec2(gl_FragCoord.xy);\n" +
                "    xyCenter+=ivec2(0,yOffset);\n" +
                "    result = texelFetch(target, xyCenter, 0).xyz - texelFetch(base, xyCenter, 0).xyz;\n" +
                "}\n"
        );
       for (int i = 0; i < diff.length; i++) {
            glProg.setTexture("target", downscaled[i]);
            glProg.setTexture("base", upscale[i]);
            //glProg.setTexture("base", downscaled[i]);
            //glProg.setTexture("target", upscale[i]);
            //Reuse of amirzaidi code // Reuse the upsampled texture.
            diff[i] = new GLTexture(upscale[i]);
            glProg.drawBlocks(diff[i]);
        }
        for (GLTexture glTexture : upscale) {
            glTexture.close();
        }
        Pyramid pyramid = new Pyramid();
        pyramid.gauss = downscaled;
        pyramid.laplace = diff;
        glProg.close();
        return pyramid;
    }
    public Pyramid createPyramidES(int levels, GLTexture input){
        return createPyramidES(levels,2,input);
    }
    public Pyramid createPyramidES(int levels, int step, GLTexture input){

        GLTexture[] downscaled = new GLTexture[levels];
        downscaled[0] = input;
        GLTexture[] gaussscaled = new GLTexture[downscaled.length - 1];
        for (int i = 1; i < downscaled.length; i++) {
            downscaled[i] = gaussdown(downscaled[i - 1],step);
        }
        for (int i = 0; i < gaussscaled.length; i++) {
            gaussscaled[i] = blur(downscaled[i],5);
        }
        GLTexture[] diff = new GLTexture[gaussscaled.length];
        glProg.useProgram("" +
                "#version 300 es\n" +
                "precision mediump float;\n" +
                "uniform sampler2D target;\n" +
                "uniform sampler2D base;\n" +
                "out vec3 result;\n" +
                "uniform int yOffset;\n" +
                "void main() {\n" +
                "    ivec2 xyCenter = ivec2(gl_FragCoord.xy);\n" +
                "    xyCenter+=ivec2(0,yOffset);\n" +
                "    result = texelFetch(target, xyCenter, 0).xyz - texelFetch(base, xyCenter, 0).xyz;\n" +
                "}\n"
        );
        for (int i = 0; i < diff.length; i++) {
            glProg.setTexture("base", downscaled[i]);
            glProg.setTexture("target", gaussscaled[i]);
            diff[i] = new GLTexture(gaussscaled[i]);
            glProg.drawBlocks(diff[i]);
        }
        for (GLTexture glTexture : gaussscaled) {
            glTexture.close();
        }
        Pyramid pyramid = new Pyramid();
        pyramid.gauss = downscaled;
        pyramid.laplace = diff;
        glProg.close();
        return pyramid;
    }
    public void SaveProgResult(Point size, String namesuffix){
        SaveProgResult(size, namesuffix, 4);
    }
    public void SaveProgResult(Point size, String namesuffix, int channels){
        GLFormat bitmapF = new GLFormat(GLFormat.DataType.UNSIGNED_8, channels);
        Bitmap preview = Bitmap.createBitmap((int)(((double)size.x*channels)/4), size.y, bitmapF.getBitmapConfig());
        File debug = new File(imageFileToSave.getAbsolutePath()+namesuffix+".jpg");
        FileOutputStream fOut = null;
        try {
            debug.createNewFile();
            fOut = new FileOutputStream(debug);
        } catch (IOException e) {
            e.printStackTrace();
        }
        preview.copyPixelsFromBuffer(glProcessing.drawBlocksToOutput(size, bitmapF));
        preview.compress(Bitmap.CompressFormat.JPEG, 97, fOut);
    }
}
