package com.eszdman.photoncamera.processing.opengl;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import static com.eszdman.photoncamera.processing.ImageSaver.imageFileToSave;

public class GLUtils {
    private final GLProg glProg;
    private GLCoreBlockProcessing glProcessing;

    public GLUtils(GLCoreBlockProcessing blockProcessing) {
        glProg = blockProcessing.mProgram;
        glProcessing = blockProcessing;
    }

    public GLTexture blur(GLTexture in, int size){
        glProg.useProgram("#version 300 es\n" +
                "#define tvar "+in.mFormat.getTemVar()+"\n" +
                "#define tscal "+in.mFormat.getScalar()+"\n" +
                "precision mediump float;\n" +
                "precision mediump "+in.mFormat.getTemSamp()+";\n" +
                "uniform "+in.mFormat.getTemSamp()+" InputBuffer;\n" +
                "uniform int yOffset;\n" +
                "out tvar Output;\n" +
                "#define size1 "+((double)(size)*0.5)+"\n" +
                "#define MSIZE1 "+size+"\n" +
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
                "            tvar inp = tvar(texelFetch(InputBuffer, (xy+ivec2(0,j)), 0)."+in.mFormat.getTemExt()+");\n" +
                "            if(length(inp."+in.mFormat.getLimExt()+") > 1.0/1000.0) {\n"+
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
                "#define MSIZE1 "+size+"\n" +
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
                "            tvar inp = tvar(texelFetch(InputBuffer, (xy+ivec2(i,0)), 0)."+out.mFormat.getTemExt()+");\n" +
                "            if(length(inp."+in.mFormat.getLimExt()+") > 1.0/1000.0) {\n"+
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
    public GLTexture gaussdown(GLTexture in, int k){
        glProg.useProgram("#version 300 es\n" +
                "#define tvar "+in.mFormat.getTemVar()+"\n" +
                "#define tscal "+in.mFormat.getScalar()+"\n" +
                "uniform "+in.mFormat.getTemSamp()+" InputBuffer;\n" +
                "uniform int yOffset;\n" +
                "out tvar Output;\n" +
                "#define size1 ("+(double)k*0.3+")\n" +
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
                "    for (int j = 0; j <= kSize; ++j) kernel[kSize+j] = kernel[kSize-j] = normpdf(float(j), 1.5);\n" +
                "    for (int i=-kSize; i <= kSize; ++i){\n" +
                "        for (int j=-kSize; j <= kSize; ++j){\n" +
                "            float pdf = kernel[kSize+j]*kernel[kSize+i];\n" +
                "            vec4 inp = texelFetch(InputBuffer, (xy+ivec2(i*2,j*2)), 0);\n" +
                "            if(length(inp) > 1.0/1000.0){\n" +
                "                mask+=tvar(inp)*pdf;\n" +
                "                pdfsize+=pdf;\n" +
                "            }\n" +
                "        }\n" +
                "    }\n" +
                "    mask/=pdfsize;\n" +
                "    Output = mask;\n" +
                "}\n");
        glProg.setTexture("InputBuffer",in);
        GLTexture out = new GLTexture(in.mSize.x/k,in.mSize.y/k,in.mFormat,null);
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
                "out tvar Output;\n" +
                "#define resize ("+k+")\n" +
                "float normpdf(in float x, in float sigma){return 0.39894*exp(-0.5*x*x/(sigma*sigma))/sigma;}\n" +
                "void main() {\n" +
                "    ivec2 xy = ivec2(gl_FragCoord.xy);\n" +
                "    xy+=ivec2(0,yOffset);\n" +
                "    xy/=resize;\n" +
                "    Output = tvar(texelFetch(InputBuffer, (xy), 0)."+in.mFormat.getTemExt()+");\n" +
                "}\n");
        glProg.setTexture("InputBuffer",in);
        GLTexture out = new GLTexture(in.mSize.x*k,in.mSize.y*k,in.mFormat,null);
        glProg.drawBlocks(out);
        glProg.close();
        return out;
    }
    public GLTexture mpy(GLTexture in, float[] vecmat){
        String vecext = "vec3";
        if(vecmat.length == 9) vecext = "mat3";
        glProg.useProgram("#version 300 es\n" +
                "precision mediump float;\n" +
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
            downscaled[i] = gaussdown(downscaled[i - 1],step);
        }
        for (int i = 0; i < upscale.length; i++) {
            upscale[i] = upscale(downscaled[i + 1],step);
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
            glProg.setTexture("base", upscale[i]);
            glProg.setTexture("target", downscaled[i]);
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
