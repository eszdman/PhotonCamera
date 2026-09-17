package com.particlesdevs.photoncamera.processing.opengl.scripts;

import android.graphics.Point;
import com.particlesdevs.photoncamera.util.Log;

import com.particlesdevs.photoncamera.processing.opengl.GLBuffer;
import com.particlesdevs.photoncamera.processing.opengl.GLContext;
import com.particlesdevs.photoncamera.processing.opengl.GLFormat;
import com.particlesdevs.photoncamera.processing.opengl.GLImage;
import com.particlesdevs.photoncamera.processing.opengl.GLProg;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;


public class GLHistogram implements AutoCloseable{
    GLContext context;
    GLProg glProg;
    GLBuffer[] buffers = new GLBuffer[4];
    int histSize;
    public int[][] outputArr = new int[4][histSize];
    GLFormat histFormat = new GLFormat(GLFormat.DataType.UNSIGNED_32);
    private boolean externalContext = false;
    public boolean Rc = true;
    public boolean Gc = true;
    public boolean Bc = true;
    public boolean Ac = true;
    public boolean Custom = false;
    public int resize = 3;
    public float[] exposure = new float[4];
    public String CustomProgram = "";
    public String CustomShader = "";
    public float input1, input2;
    /** Optional row-major 3x3 kernel pre-filter for custom shaders that
     * support it (merge/noisehist with SPATIAL_KERNEL): null = identity. */
    public float[] customKernel;
    public GLHistogram(int size) {
        this(new GLContext(1,1),size);
    }
    public GLHistogram() {
        this(new GLContext(1,1),256);
    }
    public GLHistogram(GLContext context) {
        this(context,256);
    }
    public GLHistogram(GLContext context, int size) {
        histSize = size;
        this.context = context;
        externalContext = true;
        glProg = context.mProgram;
        for (int i = 0; i < 4; i++) {
            exposure[i] = 1.0f;
        }

        buffers[0] = new GLBuffer(histSize,histFormat);
        buffers[1] = new GLBuffer(histSize,histFormat);
        buffers[2] = new GLBuffer(histSize,histFormat);
        buffers[3] = new GLBuffer(histSize,histFormat);
    }
    public GLHistogram(GLProg glProg, int size) {
        histSize = size;
        this.glProg = glProg;
        externalContext = true;
        for (int i = 0; i < 4; i++) {
            exposure[i] = 1.0f;
        }
        buffers[0] = new GLBuffer(histSize,histFormat);
        buffers[1] = new GLBuffer(histSize,histFormat);
        buffers[2] = new GLBuffer(histSize,histFormat);
        buffers[3] = new GLBuffer(histSize,histFormat);
    }
    public int[][] Compute(GLImage input){
        GLTexture texture = new GLTexture(input);
        int[][] out = Compute(texture);
        input.close();
        return out;
    }
    public int[][] Compute(GLTexture input){
        // Custom shaders (noisehist) run the full-grid dispatch; the standard
        // shader covers a 2x2 lattice patch per invocation (bit-identical
        // bins, proven by in-run A/B during development).
        return computeWithSpt(input, Custom ? 1 : 4);
    }
    private int[][] computeWithSpt(GLTexture input, int spt){
        long time = System.currentTimeMillis();
        input.Bufferize();
        int tile = 8;
        glProg.setDefine("SCALE",resize);
        glProg.setDefine("HISTSIZE", histSize);
        //glProg.setDefine("HISTMPY", (float)(histSize-1));
        glProg.setDefine("COL_R", Rc);
        glProg.setDefine("COL_G", Gc);
        glProg.setDefine("COL_B", Bc);
        glProg.setDefine("COL_A", Ac);
        glProg.setDefine("COL_CUSTOM", Custom);
        glProg.setDefine("CUSTOM_PROGRAM", CustomProgram);
        glProg.setDefine("SPATIAL_KERNEL", customKernel != null);

        glProg.setLayout(tile,tile,1);
        if(CustomShader.isEmpty())
            glProg.useAssetProgram("GLHistogram/histogram",true);
        else {
            glProg.useAssetProgram(CustomShader, true);
        }
        if (customKernel != null)
            glProg.setVarFloats("spatialKernel", customKernel);
        glProg.setTexture("inTexture",input);
        float histMpy = (float)(histSize-1);
        glProg.setVar("exposure", exposure[0] * histMpy, exposure[1] * histMpy, exposure[2] * histMpy, exposure[3] * histMpy);
        glProg.setVar("input1", input1);
        glProg.setVar("input2", input2);
        glProg.setBufferCompute("histogramRed",buffers[0]);
        glProg.setBufferCompute("histogramGreen",buffers[1]);
        glProg.setBufferCompute("histogramBlue",buffers[2]);
        glProg.setBufferCompute("histogramAlpha",buffers[3]);
        if (spt == 1) {
            glProg.computeAuto(new Point(input.mSize.x/resize, input.mSize.y/resize), 1);
        } else {
            // Cover ceil(lattice/2) per dimension; out-of-lattice invocations
            // sample nothing (bounds-checked), so overshoot is harmless.
            glProg.computeAuto(new Point(input.mSize.x/resize/2 + 1, input.mSize.y/resize/2 + 1), 1);
        }
        if (Rc)
            outputArr[0] = buffers[0].readBufferIntegers(true);
        if (Gc)
            outputArr[1] = buffers[1].readBufferIntegers(true);
        if (Bc)
            outputArr[2] = buffers[2].readBufferIntegers(true);
        if (Ac)
            outputArr[3] = buffers[3].readBufferIntegers(true);
        Log.d("GLHistogram"," elapsed:"+(System.currentTimeMillis()-time)+" ms");
        return outputArr;
    }

    @Override
    public void close() {
        if(!externalContext) {
            glProg.close();
            context.close();
        }
        for (GLBuffer buffer : buffers) {
            if (buffer != null) {
                buffer.close();
            }
        }
    }
}
