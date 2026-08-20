    package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

    import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
    import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;
    import com.particlesdevs.photoncamera.processing.opengl.scripts.GLHistogram;
    import com.particlesdevs.photoncamera.settings.annotations.Tunable;
    import com.particlesdevs.photoncamera.util.Log;
    import com.particlesdevs.photoncamera.util.Math2;

    public class AutoExposure extends Node {
    @Tunable(title = "Enable", category = "Auto Exposure", defaultValue = 1, min = 0, max = 1, step = 1, description = "Enable post processing auto exposure adjustment")
    boolean enable;

    @Tunable(title = "Histogram size", category = "Auto Exposure", defaultValue = 256, min = 256, max = 16384, step = 16, description = "Histogram bin count")
    int histSize;
    
    @Tunable(title = "Target Brightness", category = "Auto Exposure", max = 255.0f, defaultValue = 128.0f)
    float target;

    @Tunable(title = "Noise Max", category = "Auto Exposure", max = 1.0f, defaultValue = 0.05f)
    float noiseMax;
    
    @Tunable(title = "Gain Max", category = "Auto Exposure", max = 20.0f, defaultValue = 9.0f)
    float gainMax;

    @Tunable(title = "Enable WhitePoint Search", category = "Auto Exposure", defaultValue = 1, min = 0, max = 1, step = 1, description = "Enable white point search for Reinhard tone mapping")
    boolean enableWP;

    @Tunable(title = "WhitePoint apply level", category = "Auto Exposure", min = 0.0f, max = 1.0f, step = 0.1f, defaultValue = 0.8f, description = "Lower level disables white point, higher level applies full")
    float whiteApply;

    @Tunable(title = "Fill coefficient", category = "Auto Exposure", min = 0.0f, max = 1.0f, step = 0.01f, defaultValue = 0.99f, description = "Lower fill ratio can skip right histogram value peaks for HDR scenarios")
    float fillCoefficient;

    @Tunable(title = "Apply gamma mix", category = "Auto Exposure", min = 0.0f, max = 1.0f, step = 0.01f, defaultValue = 0.1f, description = "Blend between AE color space sRGB-linear")
    float applyGammaMix;


    public AutoExposure() {
        super("", "AutoExposure");
    }

    @Override
    public void AfterRun() {
    }

    @Override
    public void Compile() {}
    @Override
    public void Run() {
        if(!enable) {
            WorkingTexture = previousNode.WorkingTexture;
            return;
        }
        // Values are automatically injected in BeforeRun()!
        GLHistogram histogram = new GLHistogram(glProg, histSize);
        histogram.Rc = true;
        histogram.Gc = true;
        histogram.Bc = true;
        int[][] result;
        try {
            result = histogram.Compute(previousNode.WorkingTexture);
        } finally {
            histogram.close();
        }
        int histNormR = 0;
        int histNormG = 0;
        int histNormB = 0;
        for (int i = 0; i < histSize; i++) {
            histNormR += result[0][i];
            histNormG += result[1][i];
            histNormB += result[2][i];
        }
        float sum = 0.0f;
        int cnt = 0;
        for (int i = 0; i < histSize-1; i++) {
            if(cnt > (histNormR + histNormG + histNormB) * fillCoefficient) {
                Log.d(Name, "Histogram already full, coefficient:" + fillCoefficient);
                break;
            }
            sum += result[0][i] * i + result[1][i] * i + result[2][i] * i;
            cnt += result[0][i] + result[1][i] + result[2][i];
        }
        glProg.useAssetProgram("autoexposure/apply");
        glProg.setTexture("InputBuffer", previousNode.WorkingTexture);

        if (cnt <= 0 || !Float.isFinite(sum) || sum <= 0.0f) {
            Log.d(Name, "Skipping auto exposure: histogram has no positive samples");
            WorkingTexture = previousNode.WorkingTexture;
            glProg.closed = true;
            return;
        }
        float avg = sum / cnt;
        float mpy = (histSize / 256.0f) * target / avg;
        if (!Float.isFinite(mpy) || mpy <= 0.0f) {
            Log.d(Name, "Skipping auto exposure: invalid multiplier " + mpy);
            WorkingTexture = previousNode.WorkingTexture;
            glProg.closed = true;
            return;
        }

        sum = 0;
        int cnt2 = 0;
        float sumR = 0.0f;
        float sumG = 0.0f;
        float sumB = 0.0f;
        int cntR = 0;
        int cntG = 0;
        int cntB = 0;
        for (int i = histSize-1; i > Math.max(histSize * 2.0 / 3.0, histSize/(mpy+0.001)); i--) {
            sum += Math.max(result[0][i] * i, Math.max(result[1][i] * i, result[2][i] * i));
            sumR += result[0][i] * i;
            sumG += result[1][i] * i;
            sumB += result[2][i] * i;
            cntR += result[0][i];
            cntG += result[1][i];
            cntB += result[2][i];
            if(cntR > histNormR * 0.005f) {
                cnt2 = cntR;
                sum = sumR;
                break;
            }
            if(cntG > histNormG * 0.005f) {
                cnt2 = cntG;
                sum = sumG;
                break;
            }
            if(cntB > histNormB * 0.005f) {
                cnt2 = cntB;
                sum = sumB;
                break;
            }
        }
        if(cnt2 == 0){
            sum = histSize-1;
            cnt2 = 1;
        }
        float whiteMax = ((sum / cnt2) / histSize);

        float gainNoiseMax = (float) (noiseMax / Math.sqrt(basePipeline.noiseS * 0.5 + basePipeline.noiseO));
        gainNoiseMax = Math.max(gainNoiseMax, 1.0f);
        if (mpy > gainNoiseMax) {
            Log.d("AutoExposure", "Clamping gain by noise from " + mpy + " to " + gainNoiseMax);
            mpy = gainNoiseMax;
        }
        if(mpy > gainMax) {
            Log.d("AutoExposure", "Clamping gain by max from " + mpy + " to " + gainMax);
            mpy = gainMax;
        }
        float normL = 0.0f;
        float normR = 0.0f;
        for (int i = 0; i < histSize; i++) {
            float val = ((float)(i) / (histSize-1.0f)) * mpy;
            normL += Math.min(val, 1.0f);
            normR += (val * (1.0f + (val / (mpy * mpy))))/(1.0f + val);
        }
        Log.d("AutoExposure", "Reinhard normalizer:" + normR + " normL:" + normL + " base Mpy:" + mpy);
        if (!Float.isFinite(normR) || normR <= 0.0f || !Float.isFinite(normL)) {
            Log.d(Name, "Skipping auto exposure: invalid Reinhard normalizer");
            WorkingTexture = previousNode.WorkingTexture;
            glProg.closed = true;
            return;
        }
        mpy *= normL / normR;
        if (!Float.isFinite(mpy) || mpy <= 0.0f) {
            Log.d(Name, "Skipping auto exposure: invalid normalized multiplier " + mpy);
            WorkingTexture = previousNode.WorkingTexture;
            glProg.closed = true;
            return;
        }

        whiteMax *= mpy;
        Log.d("AutoExposure", "Reinhard white max (top 0.5%): " + whiteMax);
        Log.d("AutoExposure", "Average brightness: " + avg + ", multiplier: " + mpy);
        glProg.setVar("mpy",mpy);
        if(enableWP)
            glProg.setVar("whiteMax", Math2.mix(mpy, whiteMax, whiteApply));
        else {
            glProg.setVar("whiteMax", mpy);
        }
        glProg.setVar("applyGammaMix", applyGammaMix);
        WorkingTexture = basePipeline.getMain();
        glProg.drawBlocks(WorkingTexture);
        glProg.closed = true;
    }
}
