package com.particlesdevs.photoncamera.gallery.adapters;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DepthPageTransformerTest {
    private DepthPageTransformer depthPageTransformer;
    private View view;

    @Before
    public void setUp() throws Exception {
        depthPageTransformer = new DepthPageTransformer();
        view = new View(InstrumentationRegistry.getInstrumentation().getTargetContext());
    }

    // [-infinity, -1)
    @Test
    public void testVEC1(){
        depthPageTransformer.transformPage(view, -2);
        assertEquals(0f, view.getAlpha());
    }

    // [-1, 0]
    @Test
    public void testVEC2(){
        float expected = 1f;
        float[] positions = {-1, 0};
        for(float position : positions){
            depthPageTransformer.transformPage(view, position);
            assertEquals(expected, view.getAlpha());
        }
    }
    // (0, 1]
    @Test
    public void testVEC3(){
        float[] positions = {0.1f, 1f};
        float[] expected = {1-positions[0], 1-positions[1]};

        for(int i=0; i< positions.length; i++){
            depthPageTransformer.transformPage(view, positions[i]);
            assertEquals(expected[i], view.getAlpha());
        }
    }
}
gg