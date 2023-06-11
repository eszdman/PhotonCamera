package com.particlesdevs.photoncamera.capture;


import android.util.Size;

import org.junit.Before;
import org.junit.Test;

import org.mockito.Mockito;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.Assert.*;

public class CaptureControllerTest {

    @Before
    public void setUp() throws Exception {

    }

    /**
     * Purpose: chooseOptimalSize from several sizes and test for line coverage
     * @TestCase1
     *   Input: sizes {(1920, 1080)}-Expected: (1920, 1080)
     * @TestCase2
     *   Input: sizes {(1980, 1080), (1280, 720), (1600, 900)}-Expected: (1600, 900)
     * @TestCase3
     *   Input: sizes {(1600, 900), (1280, 720)}-Expected: (1600, 900)
     * @TestCase4
     *   Input: sizes {(2560, 1440)}-Expected: (2560, 1440)
     * @throws NoSuchMethodException
     * @throws InvocationTargetException
     * @throws IllegalAccessException
     */
    @Test
    public void testChooseOptimalSize() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException, NullPointerException {
        Method methodChooseOptimalSize = CaptureController.class.getDeclaredMethod("chooseOptimalSize", Size[].class, int.class, int.class, int.class, int.class, Size.class);
        methodChooseOptimalSize.setAccessible(true);

        // Test Case 1
        {
            System.out.println("test1");

            Size choices1_0 = Mockito.mock(Size.class);
            Mockito.when(choices1_0.getWidth()).thenReturn(1920);
            Mockito.when(choices1_0.getHeight()).thenReturn(1080);

            Size[] choices1 = {
                    choices1_0
            };
            int textureViewWidth1 = 1280;
            int textureViewHeight1 = 720;
            int maxWidth1 = 1920;
            int maxHeight1 = 1080;
            Size aspectRatio1 = Mockito.mock(Size.class);
            Mockito.when(aspectRatio1.getWidth()).thenReturn(16);
            Mockito.when(aspectRatio1.getHeight()).thenReturn(9);

            Object args1[] = new Object[]{
                    choices1,
                    textureViewWidth1,
                    textureViewHeight1,
                    maxWidth1,
                    maxHeight1,
                    aspectRatio1
            };

            Size result1 = (Size) methodChooseOptimalSize.invoke(null, args1);
            Size expected1 = Mockito.mock(Size.class);
            Mockito.when(expected1.getWidth()).thenReturn(1920);
            Mockito.when(expected1.getHeight()).thenReturn(1080);

            assertEquals(result1.getHeight(), expected1.getHeight());
            assertEquals(result1.getWidth(), expected1.getWidth());
        }

        // Test Case 2
        {
            System.out.println("test2");
            Size choices2_0 = Mockito.mock(Size.class);
            Mockito.when(choices2_0.getWidth()).thenReturn(1920);
            Mockito.when(choices2_0.getHeight()).thenReturn(1080);
            Size choices2_1 = Mockito.mock(Size.class);
            Mockito.when(choices2_1.getWidth()).thenReturn(1280);
            Mockito.when(choices2_1.getHeight()).thenReturn(720);
            Size choices2_2 = Mockito.mock(Size.class);
            Mockito.when(choices2_2.getWidth()).thenReturn(1600);
            Mockito.when(choices2_2.getHeight()).thenReturn(900);

            Size[] choices2 = {
                    choices2_0,
                    choices2_1,
                    choices2_2
            };
            int textureViewWidth2 = 1600;
            int textureViewHeight2 = 900;
            int maxWidth2 = 1920;
            int maxHeight2 = 1080;
            Size aspectRatio2 = Mockito.mock(Size.class);
            Mockito.when(aspectRatio2.getWidth()).thenReturn(16);
            Mockito.when(aspectRatio2.getHeight()).thenReturn(9);

            Object args2[] = new Object[]{
                    choices2,
                    textureViewWidth2,
                    textureViewHeight2,
                    maxWidth2,
                    maxHeight2,
                    aspectRatio2
            };

            Size result2 = (Size) methodChooseOptimalSize.invoke(null, args2);
            Size expected2 = Mockito.mock(Size.class);
            Mockito.when(expected2.getWidth()).thenReturn(1600);
            Mockito.when(expected2.getHeight()).thenReturn(900);

            assertEquals(result2.getHeight(), expected2.getHeight());
            assertEquals(result2.getWidth(), expected2.getWidth());
        }

        // Test Case 3
        {
            System.out.println("test3");
            Size choices3_0 = Mockito.mock(Size.class);
            Mockito.when(choices3_0.getWidth()).thenReturn(1600);
            Mockito.when(choices3_0.getHeight()).thenReturn(900);
            Size choices3_1 = Mockito.mock(Size.class);
            Mockito.when(choices3_1.getWidth()).thenReturn(1280);
            Mockito.when(choices3_1.getHeight()).thenReturn(720);

            Size[] choices3 = {
                    choices3_0,
                    choices3_1
            };
            int textureViewWidth3 = 1920;
            int textureViewHeight3 = 1080;
            int maxWidth3 = 2560;
            int maxHeight3 = 1440;
            Size aspectRatio3 = Mockito.mock(Size.class);
            Mockito.when(aspectRatio3.getWidth()).thenReturn(16);
            Mockito.when(aspectRatio3.getHeight()).thenReturn(9);

            Object args3[] = new Object[]{
                    choices3,
                    textureViewWidth3,
                    textureViewHeight3,
                    maxWidth3,
                    maxHeight3,
                    aspectRatio3,
            };

            Size result3 = (Size) methodChooseOptimalSize.invoke(null, args3);
            Size expected3 = Mockito.mock(Size.class);
            Mockito.when(expected3.getWidth()).thenReturn(1600);
            Mockito.when(expected3.getHeight()).thenReturn(900);

            assertEquals(result3.getHeight(), expected3.getHeight());
            assertEquals(result3.getWidth(), expected3.getWidth());
        }

        // Test Case 4
        {
            System.out.println("test4");
            Size choices4_0 = Mockito.mock(Size.class);
            Mockito.when(choices4_0.getWidth()).thenReturn(2560);
            Mockito.when(choices4_0.getHeight()).thenReturn(1440);
            Size[] choices4 = {
                    choices4_0
            };
            int textureViewWidth4 = 1280;
            int textureViewHeight4 = 720;
            int maxWidth4 = 1920;
            int maxHeight4 = 1080;
            Size aspectRatio4 = Mockito.mock(Size.class);
            Mockito.when(aspectRatio4.getWidth()).thenReturn(16);
            Mockito.when(aspectRatio4.getHeight()).thenReturn(9);

            Object args4[] = new Object[]{
                    choices4,
                    textureViewWidth4,
                    textureViewHeight4,
                    maxWidth4,
                    maxHeight4,
                    aspectRatio4,
            };

            Size result4 = (Size) methodChooseOptimalSize.invoke(null, args4);
            Size expected4 = Mockito.mock(Size.class);
            Mockito.when(expected4.getWidth()).thenReturn(2560);
            Mockito.when(expected4.getHeight()).thenReturn(1440);
            assertEquals(result4.getWidth(), expected4.getWidth());
            assertEquals(result4.getHeight(), expected4.getHeight());
        }
    }

    /**
     * Purpose: test method getCameraOutputSize(Size[]) with line coverage
     * @TestCase1 - for normal
     *   Input: sizes {(1980, 1080), (1280, 720), (1600, 900)}-Expected: (1920, 1080)
     * @TestCase2 - for largest Size > highResolution
     *   Input: sizes {(1980, 1080), (1280, 720), (8000, 4500)}-Expected: (1920, 1080)
     * @TestCase3 - for input size == 1 and largest size > highResolution
     *   Input: sizes {(8000, 4500)}-Expected: null
     */
    @Test
    public void testGetCameraOutputSize_withOneParameter() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method methodChooseOptimalSize = CaptureController.class.getDeclaredMethod("getCameraOutputSizeTest", Size[].class);
        methodChooseOptimalSize.setAccessible(true);

        // Test case 1: normal
        {
            Size choices1_0 = Mockito.mock(Size.class);
            Mockito.when(choices1_0.getWidth()).thenReturn(1920);
            Mockito.when(choices1_0.getHeight()).thenReturn(1080);
            Size choices1_1 = Mockito.mock(Size.class);
            Mockito.when(choices1_1.getWidth()).thenReturn(1280);
            Mockito.when(choices1_1.getHeight()).thenReturn(720);
            Size choices1_2 = Mockito.mock(Size.class);
            Mockito.when(choices1_2.getWidth()).thenReturn(1600);
            Mockito.when(choices1_2.getHeight()).thenReturn(900);

            Size[] sizes1 = {
                    choices1_0,
                    choices1_1,
                    choices1_2
            };

            Object args1[] = new Object[]{
                    sizes1
            };

            Size result1 = (Size) methodChooseOptimalSize.invoke(null, args1);
            Size expected1 = Mockito.mock(Size.class);
            Mockito.when(expected1.getWidth()).thenReturn(1920);
            Mockito.when(expected1.getHeight()).thenReturn(1080);

            assertEquals(result1.getHeight(), expected1.getHeight());
            assertEquals(result1.getWidth(), expected1.getWidth());

        }

        // Test case 2: largest Size > highResolution
        {
            Size choices2_0 = Mockito.mock(Size.class);
            Mockito.when(choices2_0.getWidth()).thenReturn(1920);
            Mockito.when(choices2_0.getHeight()).thenReturn(1080);
            Size choices2_1 = Mockito.mock(Size.class);
            Mockito.when(choices2_1.getWidth()).thenReturn(1280);
            Mockito.when(choices2_1.getHeight()).thenReturn(720);
            Size choices2_2 = Mockito.mock(Size.class);
            Mockito.when(choices2_2.getWidth()).thenReturn(8000);
            Mockito.when(choices2_2.getHeight()).thenReturn(4500);

            Size[] sizes2 = {
                    choices2_0,
                    choices2_1,
                    choices2_2
            };

            Object args2[] = new Object[]{
                    sizes2
            };

            Size result2 = (Size) methodChooseOptimalSize.invoke(null, args2);
            Size expected2 = Mockito.mock(Size.class);
            Mockito.when(expected2.getWidth()).thenReturn(1920);
            Mockito.when(expected2.getHeight()).thenReturn(1080);

            assertEquals(result2.getHeight(), expected2.getHeight());
            assertEquals(result2.getWidth(), expected2.getWidth());
        }

        // Test case 2: input size == 1 and largest size > highResolution
        {
            Size choices3_0 = Mockito.mock(Size.class);
            Mockito.when(choices3_0.getWidth()).thenReturn(8000);
            Mockito.when(choices3_0.getHeight()).thenReturn(4500);

            Size[] sizes3 = {
                    choices3_0
            };

            Object args3[] = new Object[]{
                    sizes3
            };

            Size result3 = (Size) methodChooseOptimalSize.invoke(null, args3);

            assertNull(result3);
        }
    }
}