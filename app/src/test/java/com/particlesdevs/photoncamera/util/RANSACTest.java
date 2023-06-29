package com.particlesdevs.photoncamera.util;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class RANSACTest {

    @Test
    public void perform() {
        List<Double> list = new ArrayList<>();
        list.add(1.1);
        list.add(1.2);
        list.add(1.3);
        List<Double> result = RANSAC.perform(list, 2, 1, 1, 1);
        for(Double r : result){
            System.out.print(r);
        }
    }
}