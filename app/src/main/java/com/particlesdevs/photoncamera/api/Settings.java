package com.particlesdevs.photoncamera.api;

import com.particlesdevs.photoncamera.settings.PreferenceKeys;

import static android.hardware.camera2.CaptureRequest.NOISE_REDUCTION_MODE_OFF;

public class Settings {
    private final String TAG = "Settings";
    //Preferences

    public int frameCount;
    public int lumenCount;
    public int chromaCount;
    public boolean enhancedProcess;
    public boolean watermark;
    public boolean energySaving;
    public boolean DebugData;
    public boolean roundEdge;
    public boolean align;
    public boolean hdrx;
    public boolean hdrxNR;
    public double exposureCompensation;
    public double saturation;
    public double sharpness;
    public double contrastMpy = 1.0;
    public int contrastConst = 0;//TODO
    public double noiseRstr;
    public double mergeStrength;
    public double compressor;
    public double gain;
    public double shadows;
    public boolean rawSaver;
    public boolean QuadBayer;
    public int cfaPattern;
    public int theme;
    public boolean remosaic;//TODO
    public boolean eisPhoto;
    public boolean fpsPreview;
    public int alignAlgorithm;
    public String mCameraID;
    public float[] toneMap;
    public float[] gamma;

    //Camera direct related
    public int noiseReduction = NOISE_REDUCTION_MODE_OFF;
    public CameraMode selectedMode;

    public void loadCache() {
        noiseReduction = PreferenceKeys.isSystemNrOn();
        frameCount = PreferenceKeys.getFrameCountValue();
        align = PreferenceKeys.isDisableAligningOn();
        lumenCount = PreferenceKeys.getLumaNrValue();
        chromaCount = PreferenceKeys.getChromaNrValue();
        enhancedProcess = PreferenceKeys.isEnhancedProcessionOn();
        watermark = PreferenceKeys.isShowWatermarkOn();
        energySaving = PreferenceKeys.getBool(PreferenceKeys.Key.KEY_ENERGY_SAVING);
        DebugData = PreferenceKeys.isAfDataOn();
        roundEdge = PreferenceKeys.isRoundEdgeOn();
        sharpness = PreferenceKeys.getSharpnessValue();
        contrastMpy = PreferenceKeys.getContrastValue();//TODO recheck
//        contrastConst = get(contrastConst, "ContrastConst");///////TODO
//        saturation = get(saturation, "Saturation");
        saturation = PreferenceKeys.getSaturationValue();
        exposureCompensation = PreferenceKeys.getFloat(PreferenceKeys.Key.KEY_EXPOCOMPENSATE_SEEKBAR);
        compressor = PreferenceKeys.getCompressorValue();
        noiseRstr = PreferenceKeys.getFloat(PreferenceKeys.Key.KEY_NOISESTR_SEEKBAR);
        mergeStrength = PreferenceKeys.getFloat(PreferenceKeys.Key.KEY_MERGE_SEEKBAR);
        gain = PreferenceKeys.getGainValue();
        shadows = PreferenceKeys.getFloat(PreferenceKeys.Key.KEY_SHADOWS_SEEKBAR);
        hdrx = PreferenceKeys.isHdrxNrOn();
        cfaPattern = PreferenceKeys.getCFAValue();
        rawSaver = PreferenceKeys.isSaveRawOn();
        remosaic = PreferenceKeys.isRemosaicOn();
        eisPhoto = PreferenceKeys.isEisPhotoOn();
        QuadBayer = PreferenceKeys.isQuadBayerOn();
        fpsPreview = PreferenceKeys.isFpsPreviewOn();
        hdrxNR = PreferenceKeys.isHdrxNrOn();
        alignAlgorithm = PreferenceKeys.getAlignMethodValue();
        selectedMode = CameraMode.valueOf(PreferenceKeys.getCameraModeOrdinal());
        toneMap = parseToneMapArray();
        gamma = parseGammaArray();
        mCameraID = PreferenceKeys.getCameraID();
        theme = PreferenceKeys.getThemeValue();
    }

    public void saveID() {
        PreferenceKeys.setCameraID(mCameraID);
    }

    float[] parseToneMapArray() {
        String savedArrayAsString = PreferenceKeys.getToneMap();
        if (savedArrayAsString == null)
            return new float[0];
        String[] array = savedArrayAsString.replace("[", "").replace("]", "").split(",");
        float[] finalArray = new float[array.length];
        for (int i = 0; i < array.length; i++) {
            finalArray[i] = Float.parseFloat(array[i].trim());
        }
        return finalArray;
    }

    float[] parseGammaArray() {
        String savedArrayAsString = PreferenceKeys.getPref(PreferenceKeys.Key.GAMMA);
        if (savedArrayAsString == null)
            return new float[0];
        String[] array = savedArrayAsString.replace("[", "").replace("]", "").split(",");
        float[] finalArray = new float[array.length];
        for (int i = 0; i < array.length; i++) {
            finalArray[i] = Float.parseFloat(array[i].trim());
        }
        return finalArray;
    }

}
