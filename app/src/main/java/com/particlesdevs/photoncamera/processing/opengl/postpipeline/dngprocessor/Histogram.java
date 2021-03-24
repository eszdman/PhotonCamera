package com.particlesdevs.photoncamera.processing.opengl.postpipeline.dngprocessor;

import android.util.Log;

import com.aparapi.Kernel;
import com.aparapi.Range;

public class Histogram {
    private static final int HIST_BINS = 256;
    private static final double EPSILON = 0.01;
    private static final float LINEARIZE_PERCEPTION = 2.4f;

    public final float[] sigma = new float[3];
    public float[] hist;
    public final float[] histr;
    public final float[] histg;
    public final float[] histb;
    public float gamma;
    public final float logAvgLuminance;
    public Histogram(float[] f, int whPixels) {
        int[] histv = new int[HIST_BINS];
        int[] histx = new int[HIST_BINS];
        int[] histy = new int[HIST_BINS];
        int[] histz = new int[HIST_BINS];

        final double[] logTotalLuminance = {0d};
        // Loop over all values
        new Kernel(){
            @Override
            public void run() {
                int i = getGlobalId(0)*4;
                for (int j = 0; j < 3; j++) {
                    sigma[j] += f[i + j];
                }

                int bin = (int) (f[i + 3] * HIST_BINS);
                if (bin < 0) bin = 0;
                if (bin >= HIST_BINS) bin = HIST_BINS - 1;
                histv[bin]++;

                bin = (int) (f[i] * HIST_BINS);
                if (bin < 0) bin = 0;
                if (bin >= HIST_BINS) bin = HIST_BINS - 1;
                histx[bin]++;
                bin = (int) (f[i + 1] * HIST_BINS);
                if (bin < 0) bin = 0;
                if (bin >= HIST_BINS) bin = HIST_BINS - 1;
                histy[bin]++;
                bin = (int) (f[i + 2] * HIST_BINS);
                if (bin < 0) bin = 0;
                if (bin >= HIST_BINS) bin = HIST_BINS - 1;
                histz[bin]++;

                logTotalLuminance[0] += Math.log(f[i + 3] + EPSILON);
            }
        }.execute(Range.create(f.length/4));

        new Kernel() {
            @Override
            public void run() {
                int w = getGlobalId(0);
                if (w - 1 >= 0 && w + 1 < histv.length)
                 histv[w] = (histv[w]*2 + histv[w - 1] + histv[w + 1]) / 4;
            }
        }.execute(Range.create(histv.length));

        logAvgLuminance = (float) Math.exp(logTotalLuminance[0] * 4 / f.length);
        for (int j = 0; j < 3; j++) {
            sigma[j] /= whPixels;
        }

        hist = buildCumulativeHist(histv);
        histr = buildCumulativeHist(histx);
        histg = buildCumulativeHist(histy);
        histb = buildCumulativeHist(histz);

        // Find gamma: Inverse of the average exponent.
        gamma = findGamma(hist);

        // Compensate for the gamma being applied first.
        /*for (int i = 1; i <= HIST_BINS; i++) {
            double id = (double) i / HIST_BINS;
            hist[i] *= id / Math.pow(id, gamma);
        }*/

        // Limit contrast and banding.
        /*float[] tmp = new float[cumulativeHist.length];
        for (int i = cumulativeHist.length - 1; i > 0; i--) {
            System.arraycopy(cumulativeHist, 0, tmp, 0, i);
            for (int j = i; j < cumulativeHist.length - 1; j++) {
                tmp[j] = (cumulativeHist[j - 1] + cumulativeHist[j + 1]) * 0.5f;
            }
            tmp[tmp.length - 1] = cumulativeHist[cumulativeHist.length - 1];

            float[] swp = tmp;
            tmp = cumulativeHist;
            cumulativeHist = swp;
        }*/

        // Crush shadows.
        //crushShadows(cumulativeHist);


        //Generate equalizing curve
        createCurve(hist);
        createCurve(histr);
        createCurve(histg);
        createCurve(histb);

    }
    private void createCurve(float [] hist){
        hist[0] = 0.f;
        float prev = hist[0];
        float normalization = 0.f;
        for(int i = 0; i<hist.length;i++){
            float prevh = hist[i];
            float move = ((float)(i))/hist.length;
            float accel = 0.2f+Math.max(move,0.2f)*1.8f/0.2f;
            accel*=1.0-move;

            float softClipK = Math.min(move-0.70f,0.0f)/0.3f;
            float softClip = (accel/hist.length)*(1.0f-softClipK) + 0.1f*softClipK/hist.length;

            float diff = Math.min(Math.max(hist[i]-prev,0.0005f),softClip);
            hist[i] = prev+diff;
            normalization+=diff;
            prev = hist[i];
        }
        Log.d("Histogram","normalization:"+normalization);
        //if(normalization < 1.f) normalization = 1.f;
        for(int i =0; i<hist.length;i++){
            hist[i]/=normalization;
        }
    }
    private static float[] findBL(int[] histr,int[] histg,int[] histb) {
        float[] bl = new float[3];
        int rmax = 25;
        for(int i =0; i<25; i++){
            if(histr[i] >= 1) {
                bl[0] = i/((float)histr.length);
                rmax = i;
                break;
            }
        }
        for(int i =0; i<25; i++){
            if(histg[i] >= 1) {
                bl[1] = i/((float)histg.length);
                break;
            }
        }
        for(int i =0; i<25; i++){
            if(histb[i] >= 1) {
                bl[2] = i/((float)histb.length);
                break;
            }
        }
        return bl;
    }
    private static float[] buildCumulativeHist(int[] hist) {
        float[] cumulativeHist = new float[HIST_BINS + 1];
        for (int i = 1; i < cumulativeHist.length; i++) {
            cumulativeHist[i] = cumulativeHist[i - 1] + hist[i - 1];
        }
        float max = cumulativeHist[HIST_BINS];
        for (int i = 0; i < cumulativeHist.length; i++) {
            cumulativeHist[i] /= max;
        }
        return cumulativeHist;
    }

    private static float findGamma(float[] cumulativeHist) {
        float sumExponent = 0.f;
        int exponentCounted = 0;
        for (int i = 0; i <= HIST_BINS; i++) {
            float val = cumulativeHist[i];
            if (val > 0.001f) {
                // Which power of the input is the output.
                double exponent = Math.log(cumulativeHist[i]) / Math.log((double) i / HIST_BINS);
                if (exponent > 0f && exponent < 10f) {
                    sumExponent += exponent;
                    exponentCounted++;
                }
            }
        }
        return LINEARIZE_PERCEPTION * sumExponent / exponentCounted;
    }

    private static void crushShadows(float[] cumulativeHist) {
        for (int i = 0; i < cumulativeHist.length; i++) {
            float og = (float) i / cumulativeHist.length;
            float a = Math.min(1f, og / 0.02f);
            if (a == 1f) {
                break;
            }
            cumulativeHist[i] *= Math.pow(a, 3.f);
        }
    }

    // Shift highlights down
    private static void limitHighlightContrast(int[] clippedHist, int valueCount) {
        for (int i = clippedHist.length - 1; i >= clippedHist.length / 4; i--) {
            int limit = 4 * valueCount / i;

            if (clippedHist[i] > limit) {
                int removed = clippedHist[i] - limit;
                clippedHist[i] = limit;

                for (int j = i - 1; j >= 0; j--) {
                    int space = limit - clippedHist[j];
                    if (space > 0) {
                        int allocate = Math.min(removed, space);
                        clippedHist[j] += allocate;
                        removed -= allocate;
                        if (removed == 0) {
                            break;
                        }
                    }
                }
            }
        }
    }
}
