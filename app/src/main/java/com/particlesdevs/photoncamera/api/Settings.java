package com.particlesdevs.photoncamera.api;

import com.particlesdevs.photoncamera.settings.PreferenceKeys;
import com.particlesdevs.photoncamera.util.Allocator;

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
    public boolean aspect169;
    public boolean binning;
    public boolean DebugData;
    public boolean roundEdge;
    public boolean align;
    public boolean hdrx;
    public boolean hdrxNR;
    public boolean ultraHdr;
    public boolean ultraHdr4x;
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
    public int rawSaver;
    public boolean QuadBayer;
    public int cfaPattern;
    public int theme;
    public boolean remosaic;//TODO
    public boolean eisPhoto;
    public int fpsMode;
    public int alignAlgorithm;

    public int colorMethod;
    public int focusPeak;
    public int previewFormat;
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
        aspect169 = PreferenceKeys.getBool(PreferenceKeys.Key.KEY_WIDE169);
        binning = PreferenceKeys.isBinningOn();
        Allocator.binning = binning;
        DebugData = PreferenceKeys.isFullDebugOn();
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
        rawSaver = PreferenceKeys.isSaveRaw();
        remosaic = PreferenceKeys.isRemosaicOn();
        eisPhoto = PreferenceKeys.isEisPhotoOn();
        QuadBayer = PreferenceKeys.isQuadBayerOn();
        fpsMode = PreferenceKeys.getFpsMode();
        hdrxNR = PreferenceKeys.isHdrxNrOn();
        ultraHdr = PreferenceKeys.isUltraHdrOn();
        ultraHdr4x = PreferenceKeys.isUltraHdr4xDownscaleOn();
        alignAlgorithm = PreferenceKeys.getAlignMethodValue();
        colorMethod = PreferenceKeys.getColorMethodValue();
        focusPeak = PreferenceKeys.getFocusPeakValue();
        previewFormat = PreferenceKeys.getPreviewFormatValue();
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
