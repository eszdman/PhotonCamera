package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import android.graphics.Point;
import android.util.Log;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.Scanner;

public class CorrectingFlow extends Node {

    public CorrectingFlow() {
        super(0, "CorrectingFlow");
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
    @Override
    public void Run() {
        /*File corrFlow = new File(FileManager.sPHOTON_TUNING_DIR,"correctingFlow.txt");
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

        if(basePipeline.mParameters.sensorSpecifics == null ||
                basePipeline.mParameters.sensorSpecifics.aberrationCorrection == null ||
                basePipeline.mParameters.sensorSpecifics.aberrationCorrection[0] == 0.0
                        && basePipeline.mParameters.sensorSpecifics.aberrationCorrection[1] == 0.0) {
            WorkingTexture = previousNode.WorkingTexture;
            glProg.closed = true;
            return;
        }
        float[] correction = basePipeline.mParameters.sensorSpecifics.aberrationCorrection;
        //correctingFlowRG = new GLTexture(FlowXY,new GLFormat(GLFormat.DataType.FLOAT_16,4), FloatBuffer.wrap(parsedFlowRG),GL_LINEAR, GL_CLAMP_TO_EDGE);
        //correctingFlowB = new GLTexture(FlowXY,new GLFormat(GLFormat.DataType.FLOAT_16,2), FloatBuffer.wrap(parsedFlowB),GL_LINEAR, GL_CLAMP_TO_EDGE);
        glProg.setDefine("SIZE",basePipeline.mParameters.rawSize);
        glProg.setDefine("C", correction[0],correction[1]);
        glProg.setDefine("RC",correction[2],correction[3]);
        glProg.setDefine("GC",correction[4],correction[5]);
        glProg.setDefine("BC",correction[6],correction[7]);
        glProg.useProgram(R.raw.correctingflow);
        glProg.setTexture("InputBuffer",previousNode.WorkingTexture);
        //glProg.setTexture("CorrectingFlowRG", correctingFlowRG);
        //glProg.setTexture("CorrectingFlowB", correctingFlowB);
        WorkingTexture = basePipeline.getMain();
        glProg.drawBlocks(WorkingTexture);
        glProg.closed = true;
    }
}
