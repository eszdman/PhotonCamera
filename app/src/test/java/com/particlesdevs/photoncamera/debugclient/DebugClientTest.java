package com.particlesdevs.photoncamera.debugclient;

import android.hardware.camera2.CaptureRequest;

import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockedStatic;


public class DebugClientTest {
    @Mock
    CaptureRequest.Builder builder;
    @Mock
    CaptureRequest.Key<?> key;

    /**
     * Purpose : Test setKey() method to return valid object.
     */
    @org.junit.Test(expected = IllegalStateException.class)
    public void testSetKey() {
        assertTrue(RequestKeyType.createKeyType(builder, key,"LONG","value") instanceof  RequestKeyLong) ;
        assertTrue(RequestKeyType.createKeyType(builder, key,"INTEGER","value") instanceof  RequestKeyInteger) ;
        assertTrue(RequestKeyType.createKeyType(builder, key,"STRING","value") instanceof  RequestKeyString) ;
        assertTrue(RequestKeyType.createKeyType(builder, key,"FLOAT","value") instanceof  RequestKeyFloat) ;
        assertTrue(RequestKeyType.createKeyType(builder, key,"DOUBLE","value") instanceof  RequestKeyDouble) ;
        RequestKeyType.createKeyType(builder, key,"ERROR","value");
    }

    /**
     * Purpose : Test getObjectString() method to return valid object.
     */
    @Test
    public void testGetObjectString() {
        //Debugger debugger = new Debugger();
        //DebugClient debugClient = debugger.debugClient;
        byte[] byteArray = {1, 2, 3};
        float[] floatArray = {1.0f, 2.0f, 3.0f};
        int[] intArray = {1, 2, 3};
        long[] longArray = {1L, 2L, 3L};
        double[] doubleArray = {1.0, 2.0, 3.0};
        short[] shortArray = {1, 2, 3};
        char[] charArray = {'a', 'b', 'c'};
        android.hardware.camera2.params.Face[] faceArray = new android.hardware.camera2.params.Face[3];
        android.util.Pair<?, ?>[] pairArray = new android.util.Pair[3];
        android.util.Rational[] rationalArray = new android.util.Rational[3];
        android.hardware.camera2.params.MeteringRectangle[] rectangleArray = new android.hardware.camera2.params.MeteringRectangle[3];
        android.graphics.Point[] pointArray = new android.graphics.Point[3];
        Object customObject = new Object();

        assertEquals("[1, 2, 3]", DebugClient.getObjectString(byteArray));
        assertEquals("[1.0, 2.0, 3.0]", DebugClient.getObjectString(floatArray));
        assertEquals("[1, 2, 3]", DebugClient.getObjectString(intArray));
        assertEquals("[1, 2, 3]", DebugClient.getObjectString(longArray));
        assertEquals("[1.0, 2.0, 3.0]", DebugClient.getObjectString(doubleArray));
        assertEquals("[1, 2, 3]", DebugClient.getObjectString(shortArray));
        assertEquals("[a, b, c]", DebugClient.getObjectString(charArray));
        assertEquals("[null, null, null]", DebugClient.getObjectString(faceArray));
        assertEquals("[null, null, null]", DebugClient.getObjectString(pairArray));
        assertEquals("[null, null, null]", DebugClient.getObjectString(rationalArray));
        assertEquals("[null, null, null]", DebugClient.getObjectString(rectangleArray));
        assertEquals("[null, null, null]", DebugClient.getObjectString(pointArray));
        assertEquals(customObject.toString(), DebugClient.getObjectString(customObject));
    }

}