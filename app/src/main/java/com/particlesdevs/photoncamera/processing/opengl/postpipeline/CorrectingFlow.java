package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import android.graphics.Point;
import com.particlesdevs.photoncamera.util.Log;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;
import com.particlesdevs.photoncamera.processing.render.Parameters;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.Scanner;

public class CorrectingFlow extends Node {

    public CorrectingFlow() {
        super("", "CorrectingFlow");
    }

    @Override
    public void Compile() {}

    @Override
    public void AfterRun() {
        if(correctingFlowRG != null) correctingFlowRG.close();
        if(correctingFlowB != null) correctingFlowB.close();
    }
    void ParseFlow(File flow) throws FileNotFoundException {
        Scanner sc = new Scanner(flow);
        String[] sizexy = sc.nextLine().toLowerCase().split(",");
        int x = Integer.parseInt(sizexy[0]);
        int y = Integer.parseInt(sizexy[1]);
        Log.d(Name,"Flow size:"+x+","+y);
        FlowXY = new Point(x,y);
        parsedFlow = new float[x*y*3*2];
        int cnt = 0;
        for(int i =0; i<x*y; i++){
            String[] xS = sc.nextLine().split(",");
            for (String s : xS) {
                parsedFlow[cnt] = Float.parseFloat(s);
                cnt++;
            }
        }
        /*for(int i =0; i<parsedFlow.length;i++)
            if(i%2 == 0)
                parsedFlow[i] /= (float) basePipeline.mParameters.rawSize.x / 4;
             else
                parsedFlow[i] /= (float) basePipeline.mParameters.rawSize.y / 4;
         */
        Log.d(Name,"Input:"+ Arrays.toString(parsedFlow));
        for(int i =0; i<parsedFlow.length;i++){
            parsedFlow[i]/=basePipeline.mParameters.rawSize.x/4.0;
        }



        parsedFlowRG = new float[x*y*2*2];
        parsedFlowB = new float[x*y*2];
        int cntr = 0;
        int cntb = 0;
        for(int i =0; i<parsedFlow.length;i+=3*2){
            parsedFlowRG[cntr] = parsedFlow[i];
            parsedFlowRG[cntr + 1] = parsedFlow[i + 1];
            parsedFlowRG[cntr + 2] = parsedFlow[i + 2];
            parsedFlowRG[cntr + 3] = parsedFlow[i + 3];
            cntr+=4;
            parsedFlowB[cntb] = parsedFlow[i + 4];
            parsedFlowB[cntb + 1] = parsedFlow[i + 5];
            cntb++;
        }
    }
    float[] parsedFlow;
    float[] parsedFlowRG;
    float[] parsedFlowB;
    Point FlowXY;
    GLTexture correctingFlowRG;
    GLTexture correctingFlowB;

    // True while the active (non-passthrough) branch rendered this shot.
    boolean cfActive = false;
    @Override
    public int halo() {
        // Warp displacement is data-dependent (few px typical); 0 when the
        // node passes through. Harness-proven per device table.
        if (!willCorrect(basePipeline == null ? null : basePipeline.mParameters)) {
            return 0;
        }
        return 4;
    }

    /**
     * Pure branch predicate shared by Run(), halo() and the T4 engage check:
     * true exactly when this node draws instead of passing through. (The
     * length guard only matters for malformed tables; the field is
     * unconditionally 8 floats from the parser.)
     */
    static boolean willCorrect(Parameters p) {
        if (p == null || p.sensorSpecifics == null
                || p.sensorSpecifics.aberrationCorrection == null) {
            return false;
        }
        float[] c = p.sensorSpecifics.aberrationCorrection;
        return c.length >= 2 && (c[0] != 0.0 || c[1] != 0.0);
    }

    public void Run() {
        cfActive = false;        /*File corrFlow = new File(FileManager.sPHOTON_TUNING_DIR,"correctingFlow.txt");
        if(!corrFlow.exists()) {
            WorkingTexture = previousNode.WorkingTexture;
            glProg.closed = true;
            return;
        }
        try {
            ParseFlow(corrFlow);
        } catch (Exception e){
            WorkingTexture = previousNode.WorkingTexture;
            glProg.closed = true;
            e.printStackTrace();
            return;
        }*/

        if (!willCorrect(basePipeline.mParameters)) {
            WorkingTexture = previousNode.WorkingTexture;
            glProg.closed = true;
            if (((PostPipeline) basePipeline).debugTiledCompare) {
                verifyCorrectingRegions();
            }
            return;
        }
        cfActive = true;
        float[] correction = basePipeline.mParameters.sensorSpecifics.aberrationCorrection;
        //correctingFlowRG = new GLTexture(FlowXY,new GLFormat(GLFormat.DataType.FLOAT_16,4), FloatBuffer.wrap(parsedFlowRG),GL_LINEAR, GL_CLAMP_TO_EDGE);
        //correctingFlowB = new GLTexture(FlowXY,new GLFormat(GLFormat.DataType.FLOAT_16,2), FloatBuffer.wrap(parsedFlowB),GL_LINEAR, GL_CLAMP_TO_EDGE);
        glProg.setDefine("SIZE",previousNode.WorkingTexture.mSize);
        glProg.setDefine("C", correction[0],correction[1]);
        glProg.setDefine("RC",correction[2],correction[3]);
        glProg.setDefine("GC",correction[4],correction[5]);
        glProg.setDefine("BC",correction[6],correction[7]);
        glProg.useAssetProgram("CorrectingFlow/correctingflow");
        glProg.setTexture("InputBuffer",previousNode.WorkingTexture);
        glProg.setVar("u_tileOrigin", 0, tileActive() ? tileY0 : 0);
        //glProg.setTexture("CorrectingFlowRG", correctingFlowRG);
        //glProg.setTexture("CorrectingFlowB", correctingFlowB);
        WorkingTexture = basePipeline.getMain();
        glProg.drawBlocks(WorkingTexture);
        glProg.closed = true;
        if (((PostPipeline) basePipeline).debugTiledCompare) {
            verifyCorrectingRegions();
        }
    }

    /**
     * Harness oracle (debugTiledCompare, T3b): re-renders output bands from
     * the FULL input with the output-space origin and requires bit-exactness
     * vs the full render. No halo mathematics and no edge exclusions: the
     * clamped normalized UVs are identical per pixel on every path. Skipped
     * on the passthrough path (nothing renders). Uniform re-issue per band,
     * no program rebind (see Initial.renderInitialBinds).
     */
    private void verifyCorrectingRegions() {
        if (!cfActive) {
            Log.d("TiledHarness", "correcting strips skipped (passthrough)");
            return;
        }
        GLTexture fullOut = WorkingTexture;
        GLTexture fullIn = previousNode.WorkingTexture;
        int imgW = fullOut.mSize.x;
        int imgH = fullOut.mSize.y;
        float worst = TileDriver.verifyNodeBands(fullOut, imgW, imgH, 512,
                "TiledHarness", (b0, rows) -> {
                    GLTexture reg = new GLTexture(new android.graphics.Point(imgW, rows),
                            fullOut.mFormat);
                    tileY0 = b0;
                    tileY1 = b0 + rows;
                    tileOut = reg;
                    WorkingTexture = reg;
                    glProg.setTexture("InputBuffer", fullIn);
                    glProg.setVar("u_tileOrigin", 0, b0);
                    glProg.drawBlocks(reg);
                    return reg;
                });
        Log.d("TiledHarness", "correcting strips maxDiff=" + worst);
        tileY0 = 0;
        tileY1 = -1;
        tileOut = null;
        WorkingTexture = fullOut;
        glProg.setVar("u_tileOrigin", 0, 0);
        glProg.setTexture("InputBuffer", fullIn);
    }
}
