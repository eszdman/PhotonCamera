package com.particlesdevs.photoncamera.processing.opengl;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.util.Log;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.util.AssetLoader;
import com.particlesdevs.photoncamera.util.Utilities;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import static com.particlesdevs.photoncamera.processing.ImageSaver.jpgFilePathToSave;

public class GLUtils {
    private final GLProg glProg;
    private GLCoreBlockProcessing glProcessing;

    public GLUtils(GLCoreBlockProcessing blockProcessing) {
        glProg = blockProcessing.mProgram;
        glProcessing = blockProcessing;
    }
    public GLTexture blurfast(GLTexture in, double size){
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
        glProg.closed = true;
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
        glProg.closed = true;
        return out2;
    }
    public void bluxVH(GLTexture in,GLTexture out, double size,boolean horizontal){
        glProg.setDefine("tvar",in.mFormat.getTemVar());
        glProg.setDefine("tscal",in.mFormat.getScalar());
        glProg.setDefine("TSAMP",in.mFormat.getTemSamp());
        glProg.setDefine("INSIZE", Utilities.addP(in.mSize,0));
        glProg.setDefine("SIZE",(float)size*0.5f);
        glProg.setDefine("KSIZE",((int)size - 1)/2);
        if(horizontal) glProg.setDefine("INP","tvar inp = tvar(texelFetch(InputBuffer, (xy+ivec2(i,0)), 0)"+out.mFormat.getTemExt()+");");
        else glProg.setDefine("INP","tvar inp = tvar(texelFetch(InputBuffer, (xy+ivec2(0,i)), 0)"+out.mFormat.getTemExt()+");");
        glProg.useProgram(PhotonCamera.getAssetLoader().getString("blurvh.glsl"));
        glProg.setTexture("InputBuffer",in);
        glProg.drawBlocks(out,out.mSize);
        glProg.closed = true;
    }
    public GLTexture blursmall(GLTexture in, int kersize,double size){
        GLTexture out = new GLTexture(in);
        return blursmall(in,out,kersize,size);
    }
    public GLTexture blursmall(GLTexture in,GLTexture out, int kersize,double size){
        glProg.useProgram("#version 300 es\n" +
                "#define tvar "+in.mFormat.getTemVar()+"\n" +
                "#define tscal "+in.mFormat.getScalar()+"\n" +
                "precision mediump float;\n" +
                "precision mediump "+in.mFormat.getTemSamp()+";\n" +
                "uniform "+in.mFormat.getTemSamp()+" InputBuffer;\n" +
                "uniform int yOffset;\n" +
                "out tvar Output;\n" +
                "#define size1 "+(size)+"\n" +
                "#define MSIZE1 "+(int)kersize+"\n" +
                "float normpdf(in float x, in float sigma){return 0.39894*exp(-0.5*x*x/(sigma*sigma))/sigma;}\n" +
                "void main() {\n" +
                "    ivec2 xy = ivec2(gl_FragCoord.xy);\n" +
                "    xy+=ivec2(0,yOffset);\n" +
                "    const int kSize = (MSIZE1-1)/2;\n" +
                "    float kernel[MSIZE1];\n" +
                "    tvar mask = tvar(0.0);\n" +
                "    float pdfsize = 0.0;\n" +
                "    for (int j = 0; j <= kSize; ++j) kernel[kSize+j] = kernel[kSize-j] = normpdf(float(j), size1);\n" +
                "    for (int i=-kSize; i <= kSize; ++i){\n" +
                "        for (int j=-kSize; j <= kSize; ++j){\n" +
                "            float pdf = kernel[kSize+j]*kernel[kSize+i];\n" +
                "            tvar inp = tvar(texelFetch(InputBuffer, (xy+ivec2(i,j)), 0)"+in.mFormat.getTemExt()+");\n" +
                "            if(length(inp"+in.mFormat.getLimExt()+") > 1.0/1000.0) {\n"+
                "            mask+=inp*pdf;\n" +
                "            pdfsize+=pdf;\n" +
                "            }\n" +
                "        }\n" +
                "    }\n" +
                "    mask/=pdfsize;\n" +
                "    Output = mask;\n" +
                "}\n");
        glProg.setTexture("InputBuffer",in);
        glProg.drawBlocks(out);
        glProg.closed = true;
        return out;
    }
    public GLTexture fastdown(GLTexture in, int k){
        return fastdown(in,k,(double)k*0.3);
    }
    public GLTexture fastdown(GLTexture in, int k,double blur){
        glProg.useProgram("#version 300 es\n" +
                "#define tvar "+in.mFormat.getTemVar()+"\n" +
                "#define tscal "+in.mFormat.getScalar()+"\n" +
                "precision highp float;\n" +
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
        glProg.closed = true;
        glProg.useProgram("#version 300 es\n" +
                "#define tvar "+out.mFormat.getTemVar()+"\n" +
                "#define tscal "+out.mFormat.getScalar()+"\n" +
                "precision highp float;\n" +
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
        glProg.closed = true;
        return out2;
    }

    public GLTexture gaussdown(GLTexture in, int k){
        GLTexture out = new GLTexture((in.mSize.x/k) + (k)-1,(in.mSize.y/k) + (k)-1,in.mFormat);
        return gaussdown(in,out,k,(double)k*1.3);
    }
    public GLTexture gaussdown(GLTexture in, int k,double blur){
        GLTexture out = new GLTexture((in.mSize.x/k) + (k)-1,(in.mSize.y/k) + (k)-1,in.mFormat);
        return gaussdown(in,out,k,blur);
    }
    public GLTexture gaussdown(GLTexture in,GLTexture out, int k,double blur){
        glProg.useProgram("#version 300 es\n" +
                "precision highp "+in.mFormat.getTemSamp()+";\n" +
                "precision highp float;\n" +
                "#define tvar "+in.mFormat.getTemVar()+"\n" +
                "#define tscal "+in.mFormat.getScalar()+"\n" +
                "uniform "+in.mFormat.getTemSamp()+" InputBuffer;\n" +
                "uniform int yOffset;\n" +
                "out tvar Output;\n" +
                //"#define size1 ("+(double)k*0.3+")\n" +
                "#define transpose ("+(int)((k/2)+1)+")\n" +
                "#define size1 ("+blur/((k/2)+1)+")\n" +
                "#define MSIZE1 5\n" +
                "#define resize ("+k+")\n" +
                "float normpdf(in float x, in float sigma){return 0.39894*exp(-0.5*x*x/(sigma*sigma))/sigma;}\n" +
                "void main() {\n" +
                "    ivec2 xy = ivec2(gl_FragCoord.xy);\n" +
                "    xy+=ivec2(0,yOffset-resize);\n" +
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
        //GLTexture out = new GLTexture((int)((in.mSize.x/(double)k)+0.5),(int)((in.mSize.y/(double)k)+0.5),in.mFormat);
        glProg.drawBlocks(out);
        glProg.closed = true;
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
                //"#import interpolation\n" +
                "void main() {\n" +
                "    ivec2 xy = ivec2(gl_FragCoord.xy);\n" +
                //"    xy+=ivec2(resize/2,yOffset+resize/2);\n" +
                "    xy+=ivec2(0,yOffset);\n" +
                "    xy/=resize;\n" +
                "    Output = tvar(texelFetch(InputBuffer, (xy), 0)" +in.mFormat.getTemExt()+");\n" +
                //"    Output = tvar(textureBicubic(InputBuffer, (vec2(xy)/vec2(size)))"+in.mFormat.getTemExt()+");\n" +
                "}\n");
        glProg.setTexture("InputBuffer",in);
        glProg.setVar("size",in.mSize.x*k,in.mSize.y*k);
        //glProg.setVar("sizein",in.mSize.x*k,in.mSize.y*k);
        GLTexture out = new GLTexture(in.mSize.x*k,in.mSize.y*k,in.mFormat);
        glProg.drawBlocks(out);
        glProg.closed = true;
        //return blur(out,k-1);
        return out;
    }
    public GLTexture medianpatch(GLTexture in, Point size){
        GLTexture out = new GLTexture(size,in.mFormat);
        return medianpatch(in,out);
    }
    public GLTexture medianpatch(GLTexture in, GLTexture out){
        glProg.setDefine("RES",(((float)in.mSize.x)/out.mSize.x),(((float)in.mSize.y)/out.mSize.y));
        glProg.useProgram("#version 300 es\n" +
                "precision highp "+in.mFormat.getTemSamp()+";\n" +
                "precision highp float;\n" +
                "#define RES (1.0,1.0)\n" +
                "#define tvar "+in.mFormat.getTemVar()+"\n" +
                "#define tscal "+in.mFormat.getScalar()+"\n" +
                "uniform "+in.mFormat.getTemSamp()+" InputBuffer;\n" +
                "out tvar Output;\n" +
                "#import median\n" +
                "void main() {\n" +
                "    vec2 xy = vec2(gl_FragCoord.xy);\n" +
                "    tvar temp;\n" +
                "    tvar temp2;\n" +
                "    tvar v[5];\n" +
                "    v[0] = tvar(texelFetch(InputBuffer, ivec2(vec2(xy+vec2(-1,-1))*(vec2(RES)*0.75)),0)"+in.mFormat.getTemExt()+");\n" +
                "    v[1] = tvar(texelFetch(InputBuffer, ivec2(vec2(xy+vec2(-1,0))*(vec2(RES)*0.75)),0)"+in.mFormat.getTemExt()+");\n" +
                "    v[2] = tvar(texelFetch(InputBuffer, ivec2(vec2(xy+vec2(-1, 1))*(vec2(RES)*0.75)),0)"+in.mFormat.getTemExt()+");\n" +
                "    v[3] = tvar(texelFetch(InputBuffer, ivec2(vec2(xy+vec2(0,-1))*(vec2(RES)*0.75)),0)"+in.mFormat.getTemExt()+");\n" +
                "    v[4] = tvar(texelFetch(InputBuffer, ivec2(vec2(xy+vec2(0,0))*(vec2(RES)*0.75)),0)"+in.mFormat.getTemExt()+");\n" +
                "    temp2 = tvar(texelFetch(InputBuffer, ivec2(vec2(xy+vec2(0,1))*(vec2(RES)*0.75)),0)"+in.mFormat.getTemExt()+");\n" +
                "    mnmx6(v[0], v[1], v[2], v[3], v[4], temp2);\n" +
                "    temp2 = tvar(texelFetch(InputBuffer, ivec2(vec2(xy+vec2(1,-1))*(vec2(RES)*0.75)),0)"+in.mFormat.getTemExt()+");\n" +
                "    mnmx5(v[1], v[2], v[3], v[4], temp2);\n" +
                "    temp2 = tvar(texelFetch(InputBuffer, ivec2(vec2(xy+vec2(1,0))*(vec2(RES)*0.75)),0)"+in.mFormat.getTemExt()+");\n" +
                "    mnmx4(v[2], v[3], v[4], temp2);\n" +
                "    temp2 = tvar(texelFetch(InputBuffer, ivec2(vec2(xy+vec2(1,1))*(vec2(RES)*0.75)),0)"+in.mFormat.getTemExt()+");\n" +
                "    mnmx3(v[3], v[4], temp2);\n" +
                "    Output = tvar(v[4]);\n" +
                "}\n");
        glProg.setTexture("InputBuffer",in);
        glProg.drawBlocks(out,out.mSize);
        glProg.closed = true;
        return out;
    }
    public GLTexture patch(GLTexture in, Point size){
        GLTexture out = new GLTexture(size,in.mFormat);
        return patch(in,out);
    }
    public GLTexture patch(GLTexture in, GLTexture out){
        glProg.setDefine("RES",(((float)in.mSize.x)/out.mSize.x),(((float)in.mSize.y)/out.mSize.y));
        glProg.useProgram("#version 300 es\n" +
                "precision highp "+in.mFormat.getTemSamp()+";\n" +
                "precision highp float;\n" +
                "#define RES (1.0,1.0)\n" +
                "#define tvar "+in.mFormat.getTemVar()+"\n" +
                "#define tscal "+in.mFormat.getScalar()+"\n" +
                "uniform "+in.mFormat.getTemSamp()+" InputBuffer;\n" +
                "uniform ivec2 size;" +
                "out tvar Output;\n" +
                "#import interpolation\n" +
                "void main() {\n" +
                "    vec2 xy = vec2(gl_FragCoord.xy);\n" +
                "    Output = tvar(texelFetch(InputBuffer, ivec2(vec2(xy)*vec2(RES)),0)"+in.mFormat.getTemExt()+");\n" +
                "}\n");
        glProg.setTexture("InputBuffer",in);
        glProg.setVar("size",(int)(out.mSize.x),(int)(out.mSize.y));
        glProg.drawBlocks(out,out.mSize);
        glProg.closed = true;
        return out;
    }
    public GLTexture medianDown(GLTexture in,float k){
        GLTexture out = new GLTexture(new Point((int)(in.mSize.x/k),(int)(in.mSize.y/k)),in.mFormat);
        return medianDown(in,out,k);
    }
    public GLTexture medianDown(GLTexture in,GLTexture out, float k){
        glProg.setDefine("RESIZE",k);
        glProg.useProgram(R.raw.mediandown);
        glProg.setTexture("InputBuffer",in);
        glProg.drawBlocks(out);
        return out;
    }
    public GLTexture interpolate(GLTexture in, int k){
        GLTexture out = new GLTexture((int)(in.mSize.x*k),(int)(in.mSize.y*k),in.mFormat);
        return interpolate(in,out,k);
    }
    public GLTexture interpolate(GLTexture in, Point nsize){
        GLTexture out = new GLTexture(nsize,in.mFormat);
        return interpolate(in,out);
    }
    public GLTexture interpolate(GLTexture in, double k){
        GLTexture out = new GLTexture((int)(in.mSize.x*k),(int)(in.mSize.y*k),in.mFormat);
        return interpolate(in,out,k);
    }
    public GLTexture interpolate(GLTexture in,GLTexture out){
        glProg.useProgram("#version 300 es\n" +
                "precision highp "+in.mFormat.getTemSamp()+";\n" +
                "precision highp float;\n" +
                "#define tvar "+in.mFormat.getTemVar()+"\n" +
                "#define tscal "+in.mFormat.getScalar()+"\n" +
                "uniform "+in.mFormat.getTemSamp()+" InputBuffer;\n" +
                "uniform int yOffset;\n" +
                "uniform ivec2 size;" +
                "out tvar Output;\n" +
                "#import interpolation\n" +
                "void main() {\n" +
                "    vec2 xy = vec2(gl_FragCoord.xy);\n" +
                "    xy+=vec2(0,yOffset);\n" +
                "    Output = tvar(textureBicubic(InputBuffer, (vec2(xy)/vec2(size)))"+in.mFormat.getTemExt()+");\n" +
                "}\n");
        glProg.setTexture("InputBuffer",in);
        glProg.setVar("size",(int)(out.mSize.x),(int)(out.mSize.y));
        glProg.drawBlocks(out,out.mSize);
        glProg.closed = true;
        return out;
    }
    public GLTexture interpolate(GLTexture in,GLTexture out, double k){
        return interpolate(in,out,out.mSize,k);
    }
    public GLTexture interpolate(GLTexture in,GLTexture out,Point outSize, double k){
        glProg.useProgram("#version 300 es\n" +
                "precision highp "+in.mFormat.getTemSamp()+";\n" +
                "precision highp float;\n" +
                "#define tvar "+in.mFormat.getTemVar()+"\n" +
                "#define tscal "+in.mFormat.getScalar()+"\n" +
                "uniform "+in.mFormat.getTemSamp()+" InputBuffer;\n" +
                "uniform int yOffset;\n" +
                "uniform ivec2 size;" +
                "out tvar Output;\n" +
                "#import interpolation\n" +
                "void main() {\n" +
                "    vec2 xy = vec2(gl_FragCoord.xy);\n" +
                "    xy+=vec2(0,yOffset);\n" +
                "    Output = tvar(textureBicubic(InputBuffer, (vec2(xy)/vec2(size)))"+in.mFormat.getTemExt()+");\n" +
                "}\n");
        glProg.setTexture("InputBuffer",in);
        glProg.setVar("size",(int)(in.mSize.x*k),(int)(in.mSize.y*k));
        glProg.drawBlocks(out,outSize);
        glProg.closed = true;
        return out;
    }
    public void Convolve(GLTexture in, GLTexture out, float[] kernel, boolean centered,boolean abs){
        String center = "";
        if(centered) center = "Output += 0.5;\n";
        glProg.useProgram("#version 300 es\n" +
                "precision highp "+in.mFormat.getTemSamp()+";\n" +
                "precision highp float;\n" +
                "#define tvar "+in.mFormat.getTemVar()+"\n" +
                "#define tscal "+in.mFormat.getScalar()+"\n" +
                "uniform "+in.mFormat.getTemSamp()+" InputBuffer;\n" +
                "uniform mat3 kernel;" +
                "out tvar Output;\n" +
                "void main() {\n" +
                "    ivec2 xy = ivec2(gl_FragCoord.xy);\n" +
                "    Output += tvar(texelFetch(InputBuffer, xy+ivec2(-1,-1),0)"+in.mFormat.getTemExt()+")*kernel[0][0];\n" +
                "    Output += tvar(texelFetch(InputBuffer, xy+ivec2(-1,0),0)"+in.mFormat.getTemExt()+")*kernel[0][1];\n" +
                "    Output += tvar(texelFetch(InputBuffer, xy+ivec2(-1,1),0)"+in.mFormat.getTemExt()+")*kernel[0][2];\n" +
                "    Output += tvar(texelFetch(InputBuffer, xy+ivec2(0,-1),0)"+in.mFormat.getTemExt()+")*kernel[1][0];\n" +
                "    Output += tvar(texelFetch(InputBuffer, xy+ivec2(0,0),0)"+in.mFormat.getTemExt()+")*kernel[1][1];\n" +
                "    Output += tvar(texelFetch(InputBuffer, xy+ivec2(0,1),0)"+in.mFormat.getTemExt()+")*kernel[1][2];\n" +
                "    Output += tvar(texelFetch(InputBuffer, xy+ivec2(1,-1),0)"+in.mFormat.getTemExt()+")*kernel[2][0];\n" +
                "    Output += tvar(texelFetch(InputBuffer, xy+ivec2(1,0),0)"+in.mFormat.getTemExt()+")*kernel[2][1];\n" +
                "    Output += tvar(texelFetch(InputBuffer, xy+ivec2(1,1),0)"+in.mFormat.getTemExt()+")*kernel[2][2];\n" +
                        center+
                //"    Output/=9.0;" +
                "}\n");
        glProg.setTexture("InputBuffer",in);
        glProg.setVar("kernel",kernel);
        glProg.drawBlocks(out,out.mSize);
        glProg.closed = true;
    }
    public void Corners(GLTexture dxy,GLTexture out){
        glProg.setDefine("tvar",dxy.mFormat.getTemVar());
        glProg.setDefine("tscal",dxy.mFormat.getScalar());
        glProg.setDefine("TSAMP",dxy.mFormat.getTemSamp());
        glProg.setDefine("INSIZE", Utilities.addP(dxy.mSize,0));
        //glProg.setDefine("SIZE",size);
        glProg.useProgram(PhotonCamera.getAssetLoader().getString("corners.glsl"));
        glProg.setTexture("InputBufferdxy",dxy);
        glProg.drawBlocks(out,dxy.mSize);
        glProg.closed = true;
    }
    public void ConvDiff(GLTexture in, GLTexture out, float gradientShift){
        ConvDiff(in,out,gradientShift,0);
    }
    public void ConvDiff(GLTexture in, GLTexture out, float gradientShift, float rotation){
        glProg.setDefine("tvar",in.mFormat.getTemVar());
        glProg.setDefine("tscal",in.mFormat.getScalar());
        glProg.setDefine("TSAMP",in.mFormat.getTemSamp());
        glProg.setDefine("INSIZE", in.mSize);
        glProg.setDefine("GRADSHIFT",gradientShift);
        if(out.mFormat.mChannels >= 3) glProg.setDefine("TEXSIZE", 3);
        glProg.useProgram(PhotonCamera.getAssetLoader().getString("convdiff.glsl"));
        glProg.setVar("rotation",rotation);
        glProg.setTexture("InputBuffer",in);
        glProg.drawBlocks(out,in.mSize);
        glProg.closed = true;
    }

    public void Maximaze(GLTexture in,GLTexture in2, GLTexture out){
        glProg.setDefine("tvar",in.mFormat.getTemVar());
        glProg.setDefine("tscal",in.mFormat.getScalar());
        glProg.setDefine("TSAMP",in.mFormat.getTemSamp());
        glProg.setDefine("INSIZE", Utilities.addP(in.mSize,-1));
        glProg.useProgram(PhotonCamera.getAssetLoader().getString("maximaze.glsl"));
        glProg.setTexture("InputBuffer",in);
        glProg.setTexture("InputBuffer2",in2);
        glProg.drawBlocks(out,out.mSize);
        glProg.closed = true;
    }

    public GLTexture median(GLTexture in,Point transposing){
        GLTexture output = new GLTexture(in);
        return median(in,output,transposing);
    }
    public GLTexture median(GLTexture in,GLTexture out,Point transposing){
        glProg.setDefine("TRANSPOSE",transposing);
        glProg.useProgram(R.raw.medianfilter);
        glProg.setTexture("InputBuffer", in);
        glProg.drawBlocks(out,out.mSize);
        return out;
    }
    public GLTexture mpy(GLTexture in, float[] vecmat){
        return mpy(in,vecmat, new GLTexture(in));
    }
    public GLTexture mpy(GLTexture in, float[] vecmat,GLTexture out){
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
                "    Output = tvar(texelFetch(InputBuffer, xy, 0));\n" +
                "    Output.rgb=clamp(Output.rgb*colorvec,0.0,1.0);\n" +
                "}\n");
        glProg.setTexture("InputBuffer",in);
        glProg.setVar("colorvec",vecmat);
        glProg.drawBlocks(out);
        glProg.closed = true;
        return out;
    }
    //Linear operation between 2 textures
    public GLTexture ops(GLTexture in1,GLTexture in2,GLTexture out, String operation){
        return ops(in1,in2,out,operation,"",0);
    }
    public GLTexture ops(GLTexture in1,GLTexture in2, String operation){
        GLTexture out = new GLTexture(in1);
        return ops(in1,in2,out,operation,"",0);
    }
    public GLTexture ops(GLTexture in1,GLTexture in2,GLTexture out, String operation,String operation2,int interpolate){
        String imp = "";
        String tex1 = "    tvar in1 = (texelFetch(InputBuffer, xy, 0)"+out.mFormat.getTemExt()+");\n";
        String tex2 = "    tvar in2 = (texelFetch(InputBuffer2, xy, 0)"+out.mFormat.getTemExt()+");\n";
        if(interpolate != 0){
            imp = "uniform ivec2 size;\n" +
                  "#import interpolation\n";
        }
        if(interpolate >= 1){
            tex1 = "    tvar in1 = (textureBicubic(InputBuffer, vec2(gl_FragCoord.xy)/vec2(size))"+out.mFormat.getTemExt()+");\n";
        }
        if(interpolate >= 2){
            tex2 = "    tvar in2 = (textureBicubic(InputBuffer2, vec2(gl_FragCoord.xy)/vec2(size))"+out.mFormat.getTemExt()+");\n";
        }
            glProg.useProgram("#version 300 es\n" +
                "precision highp "+in1.mFormat.getTemSamp()+";\n" +
                "precision highp float;\n" +
                "#define tvar "+in1.mFormat.getTemVar()+"\n" +
                "#define tscal "+in1.mFormat.getScalar()+"\n" +
                "uniform "+in1.mFormat.getTemSamp()+" InputBuffer;\n" +
                "uniform "+in2.mFormat.getTemSamp()+" InputBuffer2;\n" +
                "uniform int yOffset;\n" +
                "out tvar Output;\n" +
                imp+
                "void main() {\n" +
                "    ivec2 xy = ivec2(gl_FragCoord.xy);\n" +
                "    xy+=ivec2(0,yOffset);\n" +
                tex1 +
                tex2 +
                "    Output = tvar("+operation+")"+operation2+";\n" +
                "}\n");
        glProg.setTexture("InputBuffer",in1);
        glProg.setTexture("InputBuffer2",in2);
        if(interpolate != 0) glProg.setVar("size",out.mSize);
        glProg.drawBlocks(out);
        glProg.closed = true;
        return out;
    }
    public GLTexture ops(GLTexture in, String operation,String operation2){
        return ops(in,"",operation,operation2);
    }
    public GLTexture ops(GLTexture in, String operation){
        return ops(in,"",operation,"");
    }
    public GLTexture ops(GLTexture in1,String add, String operation,String operation2){
        glProg.useProgram("#version 300 es\n" +
                "precision highp "+in1.mFormat.getTemSamp()+";\n" +
                "precision highp float;\n" +
                "#define tvar "+in1.mFormat.getTemVar()+"\n" +
                "#define tscal "+in1.mFormat.getScalar()+"\n" +
                "uniform "+in1.mFormat.getTemSamp()+" InputBuffer;\n" +
                "uniform int yOffset;\n" +
                "out tvar Output;\n" +
                add+"\n" +
                "void main() {\n" +
                "    ivec2 xy = ivec2(gl_FragCoord.xy);\n" +
                "    xy+=ivec2(0,yOffset);\n" +
                "    tvar in1 = (texelFetch(InputBuffer, xy, 0));\n" +
                "    Output = tvar("+operation+")"+operation2+";\n" +
                "}\n");
        glProg.setTexture("InputBuffer",in1);
        GLTexture out = new GLTexture(in1);
        glProg.drawBlocks(out);
        glProg.closed = true;
        return out;
    }
    public GLTexture convertVec4(GLTexture in1,String operation){
        glProg.useProgram("#version 300 es\n" +
                "precision highp "+in1.mFormat.getTemSamp()+";\n" +
                "precision highp float;\n" +
                "#define tvar "+in1.mFormat.getTemVar()+"\n" +
                "#define tscal "+in1.mFormat.getScalar()+"\n" +
                "uniform "+in1.mFormat.getTemSamp()+" InputBuffer;\n" +
                "uniform int yOffset;\n" +
                "out vec4 Output;\n" +
                "void main() {\n" +
                "    ivec2 xy = ivec2(gl_FragCoord.xy);\n" +
                "    xy+=ivec2(0,yOffset);\n" +
                "    tvar in1 = tvar(texelFetch(InputBuffer, xy, 0)"+in1.mFormat.getTemExt()+");\n" +
                "    Output = vec4("+operation+");\n" +
                "}\n");
        glProg.setTexture("InputBuffer",in1);
        GLTexture out = new GLTexture(in1.mSize,new GLFormat(GLFormat.DataType.FLOAT_16,4));
        glProg.drawBlocks(out);
        return out;
    }
    public GLTexture splitby(GLTexture in,GLTexture out, int split,int step){
        glProg.useProgram("#version 300 es\n" +
                "precision highp "+in.mFormat.getTemSamp()+";\n" +
                "precision highp float;\n" +
                "#define tvar "+in.mFormat.getTemVar()+"\n" +
                "#define tscal "+in.mFormat.getScalar()+"\n" +
                "uniform "+in.mFormat.getTemSamp()+" InputBuffer;\n" +
                "uniform int yOffset;\n" +
                "out tvar Output;\n" +
                "#define splitby ("+split+")\n" +
                "#define step ("+step+")\n" +
                "void main() {\n" +
                "    ivec2 xy = ivec2(gl_FragCoord.xy);\n" +
                "    xy+=ivec2(0,yOffset);\n" +
                "    Output = tvar(texelFetch(InputBuffer, xy*splitby + ivec2(step%splitby,step/splitby), 0)"+in.mFormat.getTemExt()+");\n" +
                "}\n");
        glProg.setTexture("InputBuffer",in);
        glProg.drawBlocks(out);
        return out;
    }
    public GLTexture conglby(GLTexture in,GLTexture out,GLTexture prevout, int split,int step){
        glProg.useProgram("#version 300 es\n" +
                "precision highp "+in.mFormat.getTemSamp()+";\n" +
                "precision highp float;\n" +
                "#define tvar "+in.mFormat.getTemVar()+"\n" +
                "#define tscal "+in.mFormat.getScalar()+"\n" +
                "uniform "+in.mFormat.getTemSamp()+" InputBuffer;\n" +
                "uniform "+prevout.mFormat.getTemSamp()+" PrevOut;\n" +
                "uniform int yOffset;\n" +
                "out tvar Output;\n" +
                "#define splitby ("+split+")\n" +
                "#define step ("+step+")\n" +
                "void main() {\n" +
                "    ivec2 xy = ivec2(gl_FragCoord.xy);\n" +
                "    xy+=ivec2(0,yOffset);\n" +
                "    if((xy.x%splitby)+(xy.y%splitby)*splitby == step){\n" +
                "    Output = tvar(texelFetch(InputBuffer, xy/splitby, 0)"+in.mFormat.getTemExt()+");\n" +
                "    } else \n" +
                "    Output = tvar(texelFetch(PrevOut, xy, 0)"+in.mFormat.getTemExt()+");\n" +
                "}\n");
        glProg.setTexture("InputBuffer",in);
        glProg.setTexture("PrevOut",prevout);
        glProg.drawBlocks(out);
        return out;
    }

    public static class Pyramid {
        public GLTexture[] gauss;
        public GLTexture[] laplace;
        public Point[] sizes;
        private GLProg glProg;
        private GLUtils glUtils;
        public int levels;
        public double step;
        public void releasePyramid(){
            for (GLTexture tex : gauss) {
                tex.close();
            }
            for (GLTexture tex : laplace) {
                tex.close();
            }
        }
        public void fillPyramid(GLTexture input){
            gauss[0] = input;
            GLTexture[] upscale = new GLTexture[gauss.length - 1];
            boolean autostep = step == 0;
            for (int i = 1; i < gauss.length; i++) {
                if(autostep && i < 2) step = 2; else step = 4;
                Point insize = gauss[i-1].mSize;
                if(insize.x <= step+2 || insize.y <= step+2) step = 2;
                glUtils.interpolate(gauss[i - 1],gauss[i]);
            }
            System.arraycopy(gauss, 1, upscale, 0, upscale.length);
            glProg.useProgram(PhotonCamera.getAssetLoader().getString("pyramiddiff.glsl"));
            for (int i = 0; i < laplace.length; i++) {
                glProg.setTexture("target", gauss[i]);
                glProg.setTexture("base", upscale[i]);
                glProg.setVar("size",sizes[i]);
                glProg.drawBlocks(laplace[i]);
                Log.d("Pyramid","diff:"+laplace[i].mSize+" downscaled:"+gauss[i].mSize+" upscale:"+upscale[i].mSize);
            }
        }
        public GLTexture getGauss(int number){
            if(number > sizes.length || number < 0) return null;
            if(number == 0) return gauss[0];
            GLTexture t1 = glUtils.interpolate(gauss[0],sizes[number]);
            GLTexture out = glUtils.blursmall(t1,3,1.4);
            t1.close();
            return out;
        }
        public GLTexture getLaplace(int number){
            if(number > sizes.length || number < 0) return null;
            glProg.useProgram(PhotonCamera.getAssetLoader().getString("pyramiddiff.glsl"));
            GLTexture downscaled = getGauss(number);
            glProg.setTexture("target", downscaled);
            glProg.setTexture("base", getGauss(number+1));
            glProg.setVar("size",sizes[number]);
            //glProg.setTexture("base", downscaled[i]);
            //glProg.setTexture("target", upscale[i]);
            //Reuse of amirzaidi code // Reuse the upsampled texture.
            GLTexture out = new GLTexture(sizes[number],gauss[0].mFormat);
            glProg.drawBlocks(out);
            //upscale[i].close();
            Log.d("Pyramid","diff:"+out.mSize);
            return out;
        }
    }
    public Pyramid createPyramid(int levels, GLTexture input){
        return createPyramid(levels,2,input);
    }
    public Pyramid createPyramidTex(int levels, int step, GLTexture input){
        Pyramid pyramid = new Pyramid();
        pyramid.levels = levels;
        pyramid.step = step;
        pyramid.glProg = glProg;
        pyramid.glUtils = this;
        GLTexture[] downscaled = new GLTexture[levels];
        downscaled[0] = input;

        GLTexture[] upscale = new GLTexture[downscaled.length - 1];
        pyramid.sizes = new Point[downscaled.length];
        pyramid.sizes[0] = new Point(input.mSize);
        boolean autostep = step == 0;
        for (int i = 1; i < downscaled.length; i++) {
            if(autostep && i < 2) step = 2; else step = 4;
            Point insize = downscaled[i-1].mSize;
            if(insize.x <= step+2 || insize.y <= step+2) step = 2;
            downscaled[i] = new GLTexture(new Point(insize.x/step,insize.y/step),input.mFormat);
            pyramid.sizes[i] = new Point(pyramid.sizes[i-1].x/step,pyramid.sizes[i-1].y/step);
            //Log.d("Pyramid","downscale:"+pyramid.sizes[i]);
        }
        for (int i = 0; i < upscale.length; i++) {
            upscale[i] = downscaled[i+1];
            //Log.d("Pyramid","upscale:"+pyramid.sizes[i]);
        }
        GLTexture[] diff = new GLTexture[upscale.length];
        for (int i = 0; i < diff.length; i++) {
            diff[i] = new GLTexture(pyramid.sizes[i],upscale[i].mFormat);
        }
        pyramid.gauss = downscaled;
        pyramid.laplace = diff;
        return pyramid;
    }
    public Pyramid createPyramid(int levels, double step, GLTexture input){
        Pyramid pyramid = new Pyramid();
        pyramid.glProg = glProg;
        pyramid.glUtils = this;
        pyramid.levels = levels;
        pyramid.step = step;
        GLTexture[] downscaled = new GLTexture[levels];
        downscaled[0] = input;

        GLTexture[] upscale = new GLTexture[downscaled.length - 1];
        pyramid.sizes = new Point[downscaled.length];
        pyramid.sizes[0] = new Point(input.mSize);
        boolean autostep = step == 0;
        for (int i = 1; i < downscaled.length; i++) {
            //if(autostep && i < 2) step = 2; else step = 4;
            //downscaled[i] = gaussdown(downscaled[i - 1],step);
            Point insize = downscaled[i-1].mSize;
            if(autostep && (insize.x <= step+2 || insize.y <= step+2)) step = 2;
            int sizex = (int)(insize.x/step);
            int sizey = (int)(insize.y/step);
            sizex = Math.max(1,sizex);
            sizey = Math.max(1,sizey);
            downscaled[i] = interpolate(downscaled[i - 1],new Point(sizex,sizey));
            GLTexture old = downscaled[i];
            downscaled[i] = blursmall(downscaled[i],3,1.4);
            old.close();
            //downscaled[i] = medianDown(downscaled[i-1],new GLTexture(new Point(sizex,sizey),downscaled[i-1].mFormat), (float) step);
            pyramid.sizes[i] = new Point((int)(pyramid.sizes[i-1].x/step),(int)(pyramid.sizes[i-1].y/step));
            Log.d("Pyramid","downscale:"+pyramid.sizes[i]);
        }
        for (int i = 0; i < upscale.length; i++) {
            //upscale[i] = (interpolate(downscaled[i + 1],pyramid.sizes[i]));
            upscale[i] = downscaled[i+1];
            Log.d("Pyramid","upscale:"+pyramid.sizes[i]);
            //Log.d("Pyramid","point:"+pyramid.sizes[i]+" after:"+upscale[i].mSize);
        }

        glProg.useProgram(PhotonCamera.getAssetLoader().getString("pyramiddiff.glsl"));
        GLTexture[] diff = new GLTexture[upscale.length];
        for (int i = 0; i < diff.length; i++) {
            glProg.setTexture("target", downscaled[i]);
            glProg.setTexture("base", upscale[i]);
            glProg.setVar("size",pyramid.sizes[i]);
            //glProg.setTexture("base", downscaled[i]);
            //glProg.setTexture("target", upscale[i]);
            //Reuse of amirzaidi code // Reuse the upsampled texture.
            diff[i] = new GLTexture(pyramid.sizes[i],upscale[i].mFormat);
            glProg.drawBlocks(diff[i]);
            //upscale[i].close();
            Log.d("Pyramid","diff:"+diff[i].mSize+" downscaled:"+downscaled[i].mSize+" upscale:"+upscale[i].mSize);
        }
        pyramid.gauss = downscaled;
        pyramid.laplace = diff;
        return pyramid;
    }
    public Bitmap GenerateBitmap(Point size){
        return GenerateBitmap(size,4);
    }
    public Bitmap GenerateBitmap(Point size, int channels){
        GLFormat bitmapF = new GLFormat(GLFormat.DataType.UNSIGNED_8, channels);
        Bitmap preview = Bitmap.createBitmap((int)(((double)size.x*channels)/4), size.y, bitmapF.getBitmapConfig());
        preview.copyPixelsFromBuffer(glProcessing.drawBlocksToOutput(size, bitmapF));
        return preview;
    }
    public Bitmap SaveProgResult(Point size){
        return SaveProgResult(size, "");
    }
    public Bitmap SaveProgResult(Point size, String namesuffix){
        return SaveProgResult(size, namesuffix, 4,".jpg");
    }
    public Bitmap SaveProgResult(Point size, String namesuffix, int channels,String ext){
        Bitmap preview = GenerateBitmap(size,channels);
        if(!namesuffix.equals("")) {
            File debug = new File(jpgFilePathToSave.toString().replace(".jpg","") + namesuffix + ext);
            FileOutputStream fOut = null;
            try {
                debug.createNewFile();
                fOut = new FileOutputStream(debug);
            } catch (IOException e) {
                e.printStackTrace();
            }
            preview.compress(Bitmap.CompressFormat.JPEG, 100, fOut);
        }
        return preview;
    }
    public Bitmap Result(Point size, String namesuffix, ByteBuffer in){
        return Result(size, namesuffix, 4,".jpg",in);
    }
    public Bitmap Result(Point size, String namesuffix, int channels,String ext,ByteBuffer in){
        GLFormat bitmapF = new GLFormat(GLFormat.DataType.UNSIGNED_8, channels);
        Bitmap preview = Bitmap.createBitmap((int)(((double)size.x*channels)/4), size.y, bitmapF.getBitmapConfig());
        preview.copyPixelsFromBuffer(in);
        if(!namesuffix.equals("")) {
            File debug = new File(jpgFilePathToSave.toString().replace(".jpg","") + namesuffix + ext);
            FileOutputStream fOut = null;
            try {
                debug.createNewFile();
                fOut = new FileOutputStream(debug);
            } catch (IOException e) {
                e.printStackTrace();
            }
            preview.compress(Bitmap.CompressFormat.JPEG, 100, fOut);
        }
        return preview;
    }
}
