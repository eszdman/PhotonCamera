package com.particlesdevs.photoncamera.processing.render;

import android.util.Log;
import android.util.Pair;

import com.particlesdevs.photoncamera.pro.SpecificSettingSensor;
import com.particlesdevs.photoncamera.processing.parameters.FrameNumberSelector;

public class NoiseModeler {
    private static String TAG = "NoiseModeler";
    public Pair<Double,Double>[] baseModel;
    public Pair<Double,Double>[] computeModel;
    public int AnalogueISO;
    public int SensivityISO;
    public NoiseModeler(Pair<Double,Double>[] inModel, Integer analogISO, Integer ISO, int bayer, SpecificSettingSensor specificSettingSensor) {
        AnalogueISO = analogISO;
        SensivityISO = ISO;
        baseModel = new Pair[3];
        computeModel = new Pair[3];
        //inModel = null;
        if (inModel == null || inModel.length == 0 || inModel[0].first == 0.0 || (specificSettingSensor != null && specificSettingSensor.ModelerExists)) {
            Pair<Double, Double> CustomGeneratorS;
            Pair<Double, Double> CustomGeneratorO;
            if(specificSettingSensor != null) {
                double[] avrdouble = new double[4];
                for (double[] ind : specificSettingSensor.NoiseModelerArr) {
                    avrdouble[0] += ind[0];
                    avrdouble[1] += ind[1];
                    avrdouble[2] += ind[2];
                    avrdouble[3] += ind[3];
                }
                avrdouble[0] /= 4.0;
                avrdouble[1] /= 4.0;
                avrdouble[2] /= 4.0;
                avrdouble[3] /= 4.0;
                CustomGeneratorS = new Pair<>(avrdouble[0], avrdouble[1]);
                CustomGeneratorO = new Pair<>(avrdouble[2], avrdouble[3]);
            } else {
                CustomGeneratorS = new Pair<>(0.0000025720647, 0.000028855721);
                CustomGeneratorO = new Pair<>(0.000000000039798506, 0.000000046578279);
            }
            Pair<Double,Double> computedModel = new Pair<>(computeNoiseModelS(ISO,CustomGeneratorS),computeNoiseModelO(ISO,CustomGeneratorO));
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
        computeStackingNoiseModel(FrameNumberSelector.frameCount);
    }
    public void computeStackingNoiseModel(int FrameCnt){
        double noiseRemove = Math.pow(FrameCnt,0.9);
        computeModel[0] = new Pair<>(baseModel[0].first/noiseRemove,baseModel[0].second/(Math.pow(noiseRemove,1.0)));
        computeModel[1] = new Pair<>(baseModel[1].first/noiseRemove,baseModel[1].second/(Math.pow(noiseRemove,1.0)));
        computeModel[2] = new Pair<>(baseModel[2].first/noiseRemove,baseModel[2].second/(Math.pow(noiseRemove,1.0)));
    }
    private double computeNoiseModelS(double Sensitivity,Pair<Double,Double> sGenerator) {
        double returning = sGenerator.first * Sensitivity + sGenerator.second;
        if(returning < 0.0) {
            Log.d("NoiseModeler","Negative noise model sGenerator at Sensivity:"+ Sensitivity+
                    ",First:"+sGenerator.first+
                    ",Second:"+sGenerator.second);
            //returning=-returning;
        }
        return returning;
    }

    private double computeNoiseModelO(double Sensitivity,Pair<Double,Double> oGenerator) {
        double dGain = Math.max(Sensitivity/AnalogueISO,1.0);
        double returning = (oGenerator.first * Sensitivity*Sensitivity) + (oGenerator.second*dGain*dGain);
        if(returning < 0.0) {
            Log.d("NoiseModeler","Negative noise model oGenerator at Sensivity:"+Sensitivity+
                    ",Dgain:"+dGain+
                    ",First:"+oGenerator.first+
                    ",Second:"+oGenerator.second);
            //returning=-returning;
        }
        return returning;
    }

}
