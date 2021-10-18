package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import android.graphics.Point;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.processing.opengl.GLFormat;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;
import com.particlesdevs.photoncamera.util.FileManager;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.FloatBuffer;
import java.util.Scanner;

public class CorrectingFlow extends Node {

    public CorrectingFlow() {
        super(0, "CorrectingFlow");
    }

    @Override
    public void Compile() {}

    @Override
    public void AfterRun() {
        if(correctingFlow != null) correctingFlow.close();
    }
    void ParseFlow(File flow) throws FileNotFoundException {
        Scanner sc = new Scanner(flow);
        String[] sizexy = sc.nextLine().toLowerCase().split(",");
        int x = Integer.parseInt(sizexy[0]);
        int y = Integer.parseInt(sizexy[1]);
        FlowXY = new Point(x*3,y);
        parsedFlow = new float[x*y + x*3*2];
        int cnt = 0;
        sc.nextLine();
        for(int i =0; i<y; i++){
            String[] xS = sc.nextLine().split(",");
            for (String s : xS) {
                parsedFlow[cnt] = Float.parseFloat(s);
                cnt++;
            }
        }
        for(int i =0; i<parsedFlow.length;i++)
            if(i%2 == 0)
                parsedFlow[i] /= (float) basePipeline.mParameters.rawSize.x / 4;
             else
                parsedFlow[i]/= (float) basePipeline.mParameters.rawSize.y /4;
    }
    float[] parsedFlow;
    Point FlowXY;
    GLTexture correctingFlow;
    @Override
    public void Run() {

        File corrFlow = new File(FileManager.sPHOTON_TUNING_DIR,"correctingFlow.txt");
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
        }
        correctingFlow = new GLTexture(FlowXY,new GLFormat(GLFormat.DataType.FLOAT_16,2), FloatBuffer.wrap(parsedFlow));
        glProg.setDefine("SIZE",basePipeline.mParameters.rawSize);
        glProg.useProgram(R.raw.correctingflow);
        glProg.setTexture("InputBuffer",previousNode.WorkingTexture);
        glProg.setTexture("CorrectingFlow",correctingFlow);
        WorkingTexture = basePipeline.getMain();
        glProg.drawBlocks(WorkingTexture);
        glProg.closed = true;
    }
}
