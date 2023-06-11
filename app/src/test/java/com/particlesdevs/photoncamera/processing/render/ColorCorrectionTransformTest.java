package com.particlesdevs.photoncamera.processing.render;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Scanner;

import static org.junit.Assert.*;

public class ColorCorrectionTransformTest {
    public static String testInputMatrix = "MATRIX\n\n1.0,1.1,1.2\n1.3,1.4,1.5\n1.6,1.7,1.8\n";
    public static String testInputMatrixes = "MATRIXES\n\n0.0,0.1,0.2\n\n1.0,1.1,1.2\n1.3,1.4,1.5\n1.6,1.7,1.8\n\n2.0,2.1,2.2\n\n3.0,3.1,3.2\n3.3,3.4,3.5\n3.6,3.7,3.8\n";
    public static String testInputCube = "CUBE\ndebugCube\n0.1,0.2,0.3\n0.4,0.5,0.6\n0.7,0.8,0.9\n\n1.1,1.2,1.3\n1.4,1.5,1.6\n1.7,1.8,1.9\n\n2.1,2.2,2.3\n2.4,2.5,2.6\n2.7,2.8,2.9\n";
    public static String testInputCubes = "CUBES\ndebugCubes1\n0.1,0.2,0.3\n\n0.1,0.2,0.3\n0.4,0.5,0.6\n0.7,0.8,0.9\n\n1.1,1.2,1.3\n1.4,1.5,1.6\n1.7,1.8,1.9\n\n2.1,2.2,2.3\n2.4,2.5,2.6\n2.7,2.8,2.9\ndebugCubes2\n0.1,0.2,0.3\n\n0.1,0.2,0.3\n0.4,0.5,0.6\n0.7,0.8,0.9\n\n1.1,1.2,1.3\n1.4,1.5,1.6\n1.7,1.8,1.9\n\n2.1,2.2,2.3\n2.4,2.5,2.6\n2.7,2.8,2.9\n";
    public static float[] assertMatrix = {1.0F,1.1F,1.2F,1.3F,1.4F,1.5F,1.6F,1.7F,1.8F};
    public static float[] assertMatrix2 = {3.0F,3.1F,3.2F,3.3F,3.4F,3.5F,3.6F,3.7F,3.8F};
    public static float[] assertPoint = {0.0F,0.1F,0.2F};
    public static float[] assertPoint2 = {2.0F,2.1F,2.2F};

    public static float[][] assertCube = {{0.1F,0.2F,0.3F,0.4F,0.5F,0.6F,0.7F,0.8F,0.9F}, {1.1F,1.2F,1.3F,1.4F,1.5F,1.6F,1.7F,1.8F,1.9F}, {2.1F,2.2F,2.3F,2.4F,2.5F,2.6F,2.7F,2.8F,2.9F}};

    public static float[] assertCombine = {1.7333333F, 1.8333333F, 1.9333334F, 2.0333333F, 2.1333332F, 2.2333333F, 2.3333335F, 2.4333334F, 2.5333333F};


    ColorCorrectionTransform testColorCorrectionTransform = new ColorCorrectionTransform();

    /**
     * Purpose: Read String to Matrix
     * Input : String
     * Expected
     *  assertMatrix = testColorCorrectionTransform.matrix
     */
    @Test
    public void FillCCTTestMatrix() {
        InputStream input = new ByteArrayInputStream(testInputMatrix.getBytes());
        System.setIn(input);
        Scanner in = new Scanner(System.in);
        testColorCorrectionTransform.FillCCT(in);
        assertArrayEquals(assertMatrix, testColorCorrectionTransform.matrix,0);
        in.close();
    }

    /**
     * Purpose: Read String to Matrixes
     * Input : String
     * Expected
     *  assertMatrix = testColorCorrectionTransform.matrix
     *  assertMatrix2 = testColorCorrectionTransform.matrix2
     *  assertPoint = testColorCorrectionTransform.point1
     *  assertPoint2 = testColorCorrectionTransform.point2
     */
    @Test
    public void FillCCTTestMatrixes() {
        InputStream input = new ByteArrayInputStream(testInputMatrixes.getBytes());
        System.setIn(input);
        Scanner in = new Scanner(System.in);
        testColorCorrectionTransform.FillCCT(in);
        assertArrayEquals(assertMatrix, testColorCorrectionTransform.matrix,0);
        assertArrayEquals(assertMatrix2, testColorCorrectionTransform.matrix2,0);
        assertArrayEquals(assertPoint, testColorCorrectionTransform.point1,0);
        assertArrayEquals(assertPoint2, testColorCorrectionTransform.point2,0);
        in.close();
    }

    /**
     * Purpose: Read String to Cube
     * Input : String
     * Expected
     *  assertCube = testColorCorrectionTransform.cube[0]
     */
    @Test
    public void FillCCTTestCube() {
        InputStream input = new ByteArrayInputStream(testInputCube.getBytes());
        System.setIn(input);
        Scanner in = new Scanner(System.in);
        testColorCorrectionTransform.FillCCT(in);
        assertArrayEquals(assertCube[0], testColorCorrectionTransform.cubes[0].cube[0],0);
        assertArrayEquals(assertCube[1], testColorCorrectionTransform.cubes[0].cube[1],0);
        assertArrayEquals(assertCube[2], testColorCorrectionTransform.cubes[0].cube[2],0);
        in.close();
    }

    /**
     * Purpose: Read String to Cubes
     * Input : String
     * Expected
     *  1.0F = testColorCorrectionTransform.cubes[0].ColorRatio
     *  assertCube = testColorCorrectionTransform.cube[0]
     */
    @Test
    public void FillCCTTestCubes() {
        InputStream input = new ByteArrayInputStream(testInputCubes.getBytes());
        System.setIn(input);
        Scanner in = new Scanner(System.in);
        testColorCorrectionTransform.FillCCT(in);
        assertEquals(1.0,testColorCorrectionTransform.cubes[0].ColorRatio,0);
        assertArrayEquals(assertCube[0], testColorCorrectionTransform.cubes[0].cube[0],0);
        assertArrayEquals(assertCube[1], testColorCorrectionTransform.cubes[0].cube[1],0);
        assertArrayEquals(assertCube[2], testColorCorrectionTransform.cubes[0].cube[2],0);
        in.close();
    }

    /**
     * Purpose: combine value of matrix
     * Input : float[]
     * Expected
     *  assertCombine = output of combineMatrix
     */
    @Test
    public void combineMatrixTest() {
        InputStream input = new ByteArrayInputStream(testInputMatrixes.getBytes());
        System.setIn(input);
        Scanner in = new Scanner(System.in);
        testColorCorrectionTransform.FillCCT(in);
        float[] output = testColorCorrectionTransform.combineMatrix(new float[]{0.1F, 0.2F, 0.3F});
        assertArrayEquals(assertCombine, output,0.0000001F);
    }
}
