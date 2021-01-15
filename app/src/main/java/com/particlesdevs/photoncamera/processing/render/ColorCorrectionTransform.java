package com.particlesdevs.photoncamera.processing.render;

import android.util.Log;

import java.util.Locale;
import java.util.Scanner;

public class ColorCorrectionTransform {
    public enum CorrectionMode{
        MATRIX,
        MATRIXES,
        CUBE,
        CUBES;
    }
    CorrectionMode correctionMode;
    ColorCorrectionCube cubes[];
    float[] matrix;
    public ColorCorrectionTransform(){
        cubes = new ColorCorrectionCube[2];
        matrix = new float[3];
    }
    public void FillCCT(Scanner in){
        in.useDelimiter("\n");
        in.useLocale(Locale.US);
        String type = in.nextLine();
        in.useDelimiter(",");
        switch (type){
            case "MATRIX":{
                correctionMode = CorrectionMode.MATRIX;
                for (int i = 0; i < 9; i++) {
                    String inp = in.next();
                    matrix[i] = Float.parseFloat(inp);
                }
                break;
            }
            case "CUBES":{
                correctionMode = CorrectionMode.CUBES;
                cubes[0].FillCube(in);
                cubes[1].FillCube(in);
                break;
            }
        }
    }
}
class ColorCorrectionCube {
    float[][] cube;
    float ColorRatio;
    public ColorCorrectionCube(){
        cube = new float[3][3];
    }
    public void FillCube(Scanner sc){
        sc.useDelimiter(",");
        float[] point = new float[3];
        point[0] = Float.parseFloat(sc.next());
        point[1] = Float.parseFloat(sc.next());
        point[2] = Float.parseFloat(sc.next());
        ColorRatio = point[0]/point[2];
        Log.d("COLORCCT",sc.nextLine());
        for(int i =0; i<3;i++){
            for(int j =0; j<3;j++){

            }
        }
    }
}