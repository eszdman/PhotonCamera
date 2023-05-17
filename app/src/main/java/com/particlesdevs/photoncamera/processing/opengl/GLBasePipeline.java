package com.particlesdevs.photoncamera.processing.opengl;

import android.graphics.Point;
import android.util.Log;

import com.particlesdevs.photoncamera.api.Settings;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;
import com.particlesdevs.photoncamera.processing.render.Parameters;

import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
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
    public GLTexture main1,main2,main3,main4;
    public Settings mSettings;
    public Parameters mParameters;
    public Properties mProp;
    public Point workSize;
    public float noiseS;
    public float noiseO;
    private String currentProg;

    public int texnum = 0;

    public GLBasePipeline(){
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
        closeTextures();
        glint.glProcessing.drawBlocksToOutput();
        closeTextures();
        if (main3 != null) main3.close();
        glint.glProgram.close();
        Nodes.clear();
        return glint.glProcessing.mOut;
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
        drawProgramTexture(Nodes.get(Nodes.size() - 1));
        closeTextures();
        glint.glProcessing.drawBlocksToOutput();
        closeTextures();
        if (main3 != null) main3.close();
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

    private void closeTextures() {
        if (texnum == 1) {
            if (main2 != null) main2.close();
        } else {
            if (main1 != null) main1.close();
        }
    }

    @Override
    public void close() {
        if(glint != null) {
            if (glint.glProcessing != null) glint.glProcessing.close();
            if (glint.glContext != null) glint.glContext.close();
            if (glint.glProgram != null) glint.glProgram.close();
        }
        GLTexture.notClosed();
    }
}
