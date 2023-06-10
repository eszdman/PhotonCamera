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


}