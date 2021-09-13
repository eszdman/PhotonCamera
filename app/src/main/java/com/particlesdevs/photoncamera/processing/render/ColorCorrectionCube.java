package com.particlesdevs.photoncamera.processing.render;

import android.util.Log;

import java.util.Scanner;

public class ColorCorrectionCube {
    private static String TAG = "ColorCorrectionCube";
    public float[][] cube;
    float ColorRatio;
    public ColorCorrectionCube(){
        cube = new float[3][9];
    }
    public void FillCube(Scanner sc, boolean getWP){
        float[] point = new float[3];
        Log.d(TAG, "s:"+sc.nextLine());
        if(getWP) {
            String[] points = sc.nextLine().split(",");
            point[0] = Float.parseFloat(points[0]);
            point[1] = Float.parseFloat(points[1]);
            point[2] = Float.parseFloat(points[2]);
            ColorRatio = (point[0]+point[1]) / point[2];
            sc.nextLine();
        }
        for(int i =0; i<3;i++){
            if(i!=0)
                try {
                    sc.nextLine();
                } catch (Exception ignored){}
            for(int j =0; j<3;j++){
                String in = sc.nextLine();
                Log.d(TAG,in);
                String[] inp = in.split(",");
                cube[i][j*3] = Float.parseFloat(inp[0]);
                cube[i][j*3 + 1] = Float.parseFloat(inp[1]);
                cube[i][j*3 + 2] = Float.parseFloat(inp[2]);
            }
        }
    }
    public float[][]Combine(ColorCorrectionCube lower,float[] temp){
        temp[2]+= 0.00000001;
        temp[0]+= 0.00000001;
        float ratio = (temp[0]+temp[1])/temp[2];
        ratio-=lower.ColorRatio;
        ratio*=1.f/(ColorRatio-lower.ColorRatio);
        float[][] combined = new float[3][9];
        for(int i = 0; i<3;i++){
            for(int j=0; j<9;j++){
                combined[i][j] = lower.cube[i][j]*(1.f-ratio)+cube[i][j]*ratio;
            }
        }
        return combined;
    }
}
