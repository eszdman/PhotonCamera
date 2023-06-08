package com.particlesdevs.photoncamera.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class UtilitiesTest {

    @Test
    public void testConvertToFloatArray() {
        int[] input = {1, 2, 3, 4, 5};
        float[] expected = {1.0f, 2.0f, 3.0f, 4.0f, 5.0f};
        float[] result = Utilities.convertToFloatArray(input);
        assertArrayEquals(expected, result);
    }

    @Test
    public void testLinearRegressionK() {
        // Input data set
        float[] input = {1.2f, 2.5f, 3.7f, 4.1f, 5.9f};

        // expect value
        float expectedK = 2.0f;

        // call method
        float result = Utilities.linearRegressionK(input);

        // valid result
        assertEquals(expectedK, result, 0.001f); // 0.001f는 허용 오차입니다.
    }

    @Test
    public void testLinearRegressionC() {
        // 입력 데이터
        float[] input = {1.2f, 2.5f, 3.7f, 4.1f, 5.9f};

        // 기대하는 y 절편 값
        float expectedC = 0.5f;

        // 메서드 호출
        float result = Utilities.linearRegressionC(input);

        // 결과 검증
        assertEquals(expectedC, result, 0.001f); // 0.001f는 허용 오차입니다.
    }





}