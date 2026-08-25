package com.particlesdevs.photoncamera.processing.opengl;

import android.graphics.Bitmap;
import android.graphics.Point;
import com.particlesdevs.photoncamera.util.Log;

import com.particlesdevs.photoncamera.api.Settings;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;
import com.particlesdevs.photoncamera.processing.render.Parameters;

import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;

import static android.opengl.GLES20.GL_FRAMEBUFFER;
import static android.opengl.GLES20.GL_FRAMEBUFFER_BINDING;
import static android.opengl.GLES20.glBindFramebuffer;
import static android.opengl.GLES20.glGetIntegerv;
import static com.particlesdevs.photoncamera.processing.opengl.GLCoreBlockProcessing.checkEglError;
import static com.particlesdevs.photoncamera.util.FileManager.sPHOTON_TUNING_DIR;

public class GLBasePipeline implements AutoCloseable {
    public final ArrayList<Node> Nodes = new ArrayList<>();
    public GLInterface glint = null;
    private long timeStart;
    private static final String TAG = "BasePipeline";
    private final int[] bind = new int[1];
    public GLTexture main1,main2,main3,main4, main5;
    public Settings mSettings;
    public Parameters mParameters;
    public Properties mProp;
    private final boolean loggedTuning = false;
    private final String Name;
    public Point workSize;
    public float noiseS;
    public float noiseO;
    private String currentProg;

    public int texnum = 0;

    public GLBasePipeline(String name){
        Name = name;
        Properties properties = new Properties();
        try {
            File init = new File(sPHOTON_TUNING_DIR, "PhotonCameraTuning.ini");
            if(!init.exists()) {
                init.createNewFile();
                /*InputStream inputStream = PhotonCamera.getAssetLoader().getInputStream("tuning/PhotonCameraTuning.ini");
                byte[] buffer = new byte[inputStream.available()];
                inputStream.read(buffer);
                OutputStream outputStream = new FileOutputStream(init);
                outputStream.write(buffer);
                outputStream.close();*/
            }
            properties.load(new FileInputStream(init));
        } catch (Exception e) {
            Log.e("PostPipeline","Error at loading properties");
            e.printStackTrace();
        }
        mProp = properties;
    }
    public GLTexture getMain(){
        if(texnum == 1) {
            texnum = 2;
            return main2;
        } else {
            texnum = 1;
            return main1;
        }
    }

    // Swaps main3 with main1 and main2
    public GLTexture swap3() {
        if(texnum == 1) {
            GLTexture temp = main1;
            main1 = main3;
            main3 = temp;
            return main1;
        } else {
            GLTexture temp = main2;
            main2 = main3;
            main3 = temp;
            return main2;
        }
    }

    private void tuningLog(String name, String value){
        if(loggedTuning) Log.d("Tuning",name+" = "+ value);
    }
    public boolean getTuning(String name, boolean Default){
        tuningLog(Name+"_"+name,String.valueOf(Default));
        return Boolean.parseBoolean(mProp.getProperty(Name+"_"+name,String.valueOf(Default)));
    }
    public float getTuning(String name,float Default){
        tuningLog(Name+"_"+name,String.valueOf(Default));
        return Float.parseFloat(mProp.getProperty(Name+"_"+name,String.valueOf(Default)));
    }
    public float[] getTuning(String name,float[] Default){
        String ins = Arrays.toString(Default).replace("[","").replace("]","");
        tuningLog(Name+"_"+name,ins);
        String inp = mProp.getProperty(Name+"_"+name, ins);
        String[] divided = inp.split(",");
        float[] output = new float[Default.length];
        for(int i = 0; i<divided.length;i++){
            output[i] = Float.parseFloat(divided[i]);
        }
        return output;
    }
    public double getTuning(String name,double Default){
        tuningLog(Name+"_"+name,String.valueOf(Default));
        return Double.parseDouble(mProp.getProperty(Name+"_"+name,String.valueOf(Default)));
    }
    public short getTuning(String name,short Default){
        tuningLog(Name+"_"+name,String.valueOf(Default));
        return Short.parseShort(mProp.getProperty(Name+"_"+name,String.valueOf(Default)));
    }
    public int getTuning(String name,int Default){
        tuningLog(Name+"_"+name,String.valueOf(Default));
        return Integer.parseInt(mProp.getProperty(Name+"_"+name,String.valueOf(Default)));
    }

    public void startTimeMeasure() {
        timeStart = System.currentTimeMillis();
    }

    public void endTimeMeasure(String Name) {
        Log.d("Pipeline", "Node:" + Name + " elapsed:" + (System.currentTimeMillis() - timeStart) + " ms");
    }

    public void add(Node in) {
        if (Nodes.size() != 0) in.previousNode = Nodes.get(Nodes.size() - 1);
        in.basePipeline = this;
        in.glInt = glint;
        in.glUtils = glint.glUtils;
        in.glProg = glint.glProgram;
        Nodes.add(in);
    }

    private void lastI() {
        glGetIntegerv(GL_FRAMEBUFFER_BINDING, bind, 0);
        checkEglError("glGetIntegerv");
    }

    private void lastR() {
        glBindFramebuffer(GL_FRAMEBUFFER, bind[0]);
        checkEglError("glBindFramebuffer");
    }

    public GLImage runAll() {
        runAllInternal(null);
        return glint.glProcessing.mOut;
    }

    /**
     * Runs the node chain and streams the final render directly into
     * {@code sink}'s pixel memory (software ARGB_8888), avoiding any
     * intermediate full-frame output buffer. Returns {@code sink}.
     */
    public Bitmap runAll(Bitmap sink) {
        runAllInternal(sink);
        return sink;
    }

    private void runAllInternal(Bitmap sink) {
        lastI();
        for (int i = 0; i < Nodes.size(); i++) {
            prepareNode(Nodes.get(i),i);
            startTimeMeasure();
            Nodes.get(i).Run();
            endTimeMeasure(Nodes.get(i).Name);
            if (i != Nodes.size() - 1) {
                drawProgramTexture(Nodes.get(i));
            }
            Nodes.get(i).AfterRun();
        }
        if(texnum == 1){
            if (main2 != null) main2.close();
        }else {
            if (main1 != null) main1.close();
        }
        if (sink != null) {
            glint.glProcessing.drawBlocksToOutput(sink);
        } else {
            glint.glProcessing.drawBlocksToOutput();
        }
        if(texnum == 1){
            if (main1 != null) main1.close();
        }else {
            if (main2 != null) main2.close();
        }
        if (main3 != null) main3.close();
        glint.glProgram.close();
        Nodes.clear();
    }

    public ByteBuffer runAllRaw() {
        lastI();
        for (int i = 0; i < Nodes.size(); i++) {
            prepareNode(Nodes.get(i),i);
            startTimeMeasure();
            Nodes.get(i).Run();
            if (i != Nodes.size() - 1) {
                Log.d(TAG, "i:" + i + " size:" + Nodes.size());
                drawProgramTexture(Nodes.get(i));
            }
            Nodes.get(i).AfterRun();
            endTimeMeasure(Nodes.get(i).Name);
        }
        glint.glProgram.drawBlocks(Nodes.get(Nodes.size() - 1).GetProgTex());
        if(texnum == 1){
            if (main2 != null) main2.close();
        }else {
            if (main1 != null) main1.close();
        }
        glint.glProcessing.drawBlocksToOutput();
        if(texnum == 1){
            if (main1 != null) main1.close();
        }else {
            if (main2 != null) main2.close();
        }
        if (main3 != null) main3.close();
        glint.glProgram.close();
        Nodes.clear();
        return glint.glProcessing.mOutBuffer;
    }

    private void drawProgramTexture(Node node) {
        if(!glint.glProgram.closed) {
            glint.glProgram.drawBlocks(node.GetProgTex());
            glint.glProgram.closed = true;
        }
    }

    private void prepareNode(Node node, int index) {
        node.mProp = mProp;
        node.BeforeCompile();
        node.Compile();
        node.BeforeRun();
        if (index == Nodes.size() - 1) {
            lastR();
        }
    }

    @Override
    public void close() {
        if (glint != null) {
            // Ensure any pending Adreno work is complete before eglTerminate.
            try {
                android.opengl.GLES30.glFinish();
            } catch (Exception ignored) {}
            // glProcessing owns the EGL context (extends GLContext); closing it
            // already terminates the context, so glContext is redundant but guarded.
            GLCoreBlockProcessing proc = glint.glProcessing;
            if (proc != null) {
                try {
                    proc.close();
                } catch (Exception ignored) {}
                glint.glProcessing = null;
            }
            if (glint.glContext != null && glint.glContext != proc) {
                try {
                    glint.glContext.close();
                } catch (Exception ignored) {}
                glint.glContext = null;
            }
            if (glint.glProgram != null) {
                try {
                    glint.glProgram.close();
                } catch (Exception ignored) {}
            }
            glint = null;
        }
        try {
            GLTexture.notClosed();
        } catch (Exception ignored) {}
    }
}
