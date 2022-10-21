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
    public float[] p1, p2;
    public float[] matrix;
    public float[] matrix2;

    public ColorCorrectionTransform() {
        cubes = new ColorCorrectionCube[2];
        cubes[0] = new ColorCorrectionCube();
        cubes[1] = new ColorCorrectionCube();
        matrix = new float[9];
        matrix2 = new float[9];
        p1 = new float[3];
        p2 = new float[3];
    }

    public float[] combineMatrix(float[] wp) {
        float combineK = (wp[0] + wp[1]) / wp[2];
        combineK -= ((p2[0] + p2[1]) / p2[2]);
        combineK *= 1.0 / (((p1[0] + p1[1]) / p1[2]) - ((p2[0] + p2[1]) / p2[2]));
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
                    String[] inp = in.nextLine().split(",");
                    matrix[i * 3] = Float.parseFloat(inp[0]);
                    matrix[i * 3 + 1] = Float.parseFloat(inp[1]);
                    matrix[i * 3 + 2] = Float.parseFloat(inp[2]);
                }
                break;
            }
            case "MATRIXES": {
                correctionMode = CorrectionMode.MATRIXES;
                in.nextLine();
                String[] inp = in.nextLine().split(",");
                p1[0] = Float.parseFloat(inp[0]);
                p1[1] = Float.parseFloat(inp[1]);
                p1[2] = Float.parseFloat(inp[2]);
                in.nextLine();
                for (int i = 0; i < 3; i++) {
                    inp = in.nextLine().split(",");
                    matrix[i * 3] = Float.parseFloat(inp[0]);
                    matrix[i * 3 + 1] = Float.parseFloat(inp[1]);
                    matrix[i * 3 + 2] = Float.parseFloat(inp[2]);
                }
                in.nextLine();
                inp = in.nextLine().split(",");
                p2[0] = Float.parseFloat(inp[0]);
                p2[1] = Float.parseFloat(inp[1]);
                p2[2] = Float.parseFloat(inp[2]);
                in.nextLine();
                for (int i = 0; i < 3; i++) {
                    inp = in.nextLine().split(",");
                    matrix2[i * 3] = Float.parseFloat(inp[0]);
                    matrix2[i * 3 + 1] = Float.parseFloat(inp[1]);
                    matrix2[i * 3 + 2] = Float.parseFloat(inp[2]);
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
}
