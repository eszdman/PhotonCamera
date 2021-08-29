package com.particlesdevs.photoncamera.processing.render;

import android.util.Log;
import android.util.Pair;

import com.particlesdevs.photoncamera.processing.parameters.FrameNumberSelector;

public class NoiseModeler {
    private static String TAG = "NoiseModeler";
    public Pair<Double,Double>[] baseModel;
    public Pair<Double,Double>[] computeModel;
    public int AnalogueISO;
    public int SensivityISO;
    public NoiseModeler(Pair<Double,Double>[] inModel,Integer analogISO,Integer ISO, int bayer) {
        AnalogueISO = analogISO;
        SensivityISO = ISO;
        baseModel = new Pair[3];
        computeModel = new Pair[3];
        //inModel = null;
        if (inModel == null || inModel.length == 0) {
            Pair<Double, Double> Imx363sGenerator = new Pair<>(0.0000025720647, 0.000028855721);
            Pair<Double, Double> Imx363oGenerator = new Pair<>(0.000000000039798506, 0.000000046578279);
            Pair<Double,Double> computedModel = new Pair<>(computeNoiseModelS(ISO,Imx363sGenerator),computeNoiseModelO(ISO,Imx363oGenerator));
            //Test
            /*
            Pair<Double, Double> TestsGenerator = new Pair<>(1.0798706869238175e-06, -8.618818353621416e-06);
            Pair<Double, Double> TestoGenerator = new Pair<>(5.790989178667454e-12, 3.7009550769043865e-07);
            Pair<Double,Double> computedModel = new Pair<>(computeNoiseModelS(ISO,TestsGenerator),computeNoiseModelO(ISO,TestoGenerator));
             */
            baseModel[0] = new Pair<>(computedModel.first, computedModel.second);
            baseModel[1] = new Pair<>(computedModel.first, computedModel.second);
            baseModel[2] = new Pair<>(computedModel.first, computedModel.second);
        } else {
            if (inModel.length == 1) {
                baseModel[0] = new Pair<>(inModel[0].first, inModel[0].second);
                baseModel[1] = new Pair<>(inModel[0].first, inModel[0].second);
                baseModel[2] = new Pair<>(inModel[0].first, inModel[0].second);
            }
            if (inModel.length == 3) {
                baseModel[0] = new Pair<>(inModel[0].first, inModel[0].second);
                baseModel[1] = new Pair<>(inModel[1].first, inModel[1].second);
                baseModel[2] = new Pair<>(inModel[2].first, inModel[2].second);
            }
            if (inModel.length == 4) {
                baseModel[0] = new Pair<>(inModel[0].first, inModel[0].second);
                baseModel[1] = new Pair<>((inModel[1].first + inModel[2].first) / 2.0, (inModel[1].second + inModel[2].second) / 2.0);
                baseModel[2] = new Pair<>(inModel[3].first, inModel[3].second);
            }
        }

        Log.d(TAG, "NoiseModel0->" + baseModel[0]);
        Log.d(TAG, "NoiseModel1->" + baseModel[1]);
        Log.d(TAG, "NoiseModel2->" + baseModel[2]);
        computeStackingNoiseModel();
        Log.d(TAG, "ComputedNoiseModel0->" + computeModel[0]);
        Log.d(TAG, "ComputedNoiseModel1->" + computeModel[1]);
        Log.d(TAG, "ComputedNoiseModel2->" + computeModel[2]);
    }
    public void computeStackingNoiseModel(){
        computeModel[0] = new Pair<>(baseModel[0].first/getStackingNoiseRemoval(),baseModel[0].second/(Math.pow(getStackingNoiseRemoval(),1.0)));
        computeModel[1] = new Pair<>(baseModel[1].first/getStackingNoiseRemoval(),baseModel[1].second/(Math.pow(getStackingNoiseRemoval(),1.0)));
        computeModel[2] = new Pair<>(baseModel[2].first/getStackingNoiseRemoval(),baseModel[2].second/(Math.pow(getStackingNoiseRemoval(),1.0)));
    }

    private static double getStackingNoiseRemoval()
    {
        //int Frames = FrameNumberSelector.frameCount;

        //return Math.sqrt(Frames-2);
        return Math.pow(FrameNumberSelector.frameCount,0.9);
    }

    private double computeNoiseModelS(double Sensitivity,Pair<Double,Double> sGenerator) {
        return sGenerator.first * Sensitivity + sGenerator.second;
    }

    private double computeNoiseModelO(double Sensitivity,Pair<Double,Double> oGenerator) {
        double dGain = Math.max(Sensitivity/AnalogueISO,1.0);
        return (oGenerator.first * Sensitivity*Sensitivity) + (oGenerator.second*dGain*dGain);
    }

}
