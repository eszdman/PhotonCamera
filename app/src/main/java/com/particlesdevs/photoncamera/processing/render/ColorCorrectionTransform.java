package com.particlesdevs.photoncamera.processing.render;

import android.util.Log;

import java.util.Locale;
import java.util.Scanner;

public class ColorCorrectionTransform {
    private static final String TAG = "ColorCorrectionTransform";

    public enum CorrectionMode {
        MATRIX,
        MATRIXES,
        CUBE,
        CUBES;
    }

    public CorrectionMode correctionMode = CorrectionMode.MATRIX;
    ;
    public ColorCorrectionCube[] cubes;
    public float[] point1, point2;
    public float[] matrix;
    public float[] matrix2;

    public ColorCorrectionTransform() {
        cubes = new ColorCorrectionCube[2];
        cubes[0] = new ColorCorrectionCube();
        cubes[1] = new ColorCorrectionCube();
        matrix = new float[9];
        matrix2 = new float[9];
        point1 = new float[3];
        point2 = new float[3];
    }

    public float[] combineMatrix(float[] whitePoint) {
        float combineK = (whitePoint[0] + whitePoint[1]) / whitePoint[2];
        combineK -= ((point2[0] + point2[1]) / point2[2]);
        combineK *= 1.0 / (((point1[0] + point1[1]) / point1[2]) - ((point2[0] + point2[1]) / point2[2]));
        float[] outp = new float[9];
        for (int i = 0; i < 9; i++) {
            outp[i] = matrix2[i] * (1.f - combineK) + matrix[i] * combineK;
        }
        return outp;
    }

    public void FillCCT(Scanner in) {
        in.useDelimiter("\n");
        in.useLocale(Locale.US);
        String type = in.nextLine().toUpperCase();
        in.useDelimiter(",");
        Log.d(TAG, "type:" + type);
        switch (type) {
            case "MATRIX": {
                in.nextLine();
                for (int i = 0; i < 3; i++) {
                    readLineToMatrix(in, matrix, i*3);
                }
                break;
            }
            case "MATRIXES": {
                correctionMode = CorrectionMode.MATRIXES;
                in.nextLine();
                readLineToMatrix(in, point1, 0);
                in.nextLine();
                for (int i = 0; i < 3; i++) {
                    readLineToMatrix(in, matrix, i*3);
                }
                in.nextLine();
                readLineToMatrix(in, point2, 0);
                in.nextLine();
                for (int i = 0; i < 3; i++) {
                    readLineToMatrix(in, matrix2, i*3);
                }
                break;
            }
            case "CUBES": {
                correctionMode = CorrectionMode.CUBES;
                cubes[0].FillCube(in, true);
                cubes[1].FillCube(in, true);
                break;
            }
            case "CUBE": {
                correctionMode = CorrectionMode.CUBE;
                cubes[0].FillCube(in, false);
                break;
            }
        }
    }

    private void readLineToMatrix(Scanner in, float[] resultMatrix, int matrixIndex) {
        String[] inp = in.nextLine().split(",");
        for(int i=0; i<3;i++) {
            resultMatrix[matrixIndex+i] = Float.parseFloat(inp[i]);
        }
    }
}
