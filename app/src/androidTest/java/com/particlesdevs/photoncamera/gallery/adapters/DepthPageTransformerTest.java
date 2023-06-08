package com.particlesdevs.photoncamera.gallery.adapters;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DepthPageTransformerTest extends AppCompatActivity {
    private DepthPageTransformer depthPageTransformer;

    @Before
    public void setUp() throws Exception {
        depthPageTransformer = new DepthPageTransformer();
    }

    // [-infinity, -1)
    @Test
    public void test(){
        View view = new View(InstrumentationRegistry.getInstrumentation().getTargetContext());
        depthPageTransformer.transformPage(view, -2);
        assertEquals(0f, view.getAlpha());
    }
}
