package com.eszdman.photoncamera.api;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.TonemapCurve;
import android.os.Environment;
import android.util.Log;
import android.util.Range;

import com.eszdman.photoncamera.ui.MainActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import static android.content.Context.MODE_PRIVATE;
import static android.hardware.camera2.CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE;
import static android.hardware.camera2.CameraMetadata.CONTROL_AE_MODE_ON;
import static android.hardware.camera2.CameraMetadata.CONTROL_AE_STATE_LOCKED;
import static android.hardware.camera2.CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE;
import static android.hardware.camera2.CameraMetadata.HOT_PIXEL_MODE_HIGH_QUALITY;
import static android.hardware.camera2.CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON;
import static android.hardware.camera2.CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY;
import static android.hardware.camera2.CameraMetadata.NOISE_REDUCTION_MODE_OFF;
import static android.hardware.camera2.CameraMetadata.TONEMAP_MODE_GAMMA_VALUE;
import static android.hardware.camera2.CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION;
import static android.hardware.camera2.CaptureRequest.CONTROL_AE_MODE;
import static android.hardware.camera2.CaptureRequest.CONTROL_AE_REGIONS;
import static android.hardware.camera2.CaptureRequest.CONTROL_AF_MODE;
import static android.hardware.camera2.CaptureRequest.CONTROL_AF_REGIONS;
import static android.hardware.camera2.CaptureRequest.HOT_PIXEL_MODE;
import static android.hardware.camera2.CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE;
import static android.hardware.camera2.CaptureRequest.NOISE_REDUCTION_MODE;
import static android.hardware.camera2.CaptureRequest.TONEMAP_CURVE;
import static android.hardware.camera2.CaptureRequest.TONEMAP_MODE;

@SuppressWarnings("ALL")
public class Settings {
    private final String TAG = "Settings";
    public int noiseReduction = NOISE_REDUCTION_MODE_OFF;
    public int afMode = CONTROL_AF_MODE_CONTINUOUS_PICTURE;
    public MeteringRectangle[] initialAF = null;
    public MeteringRectangle[] initialAE = null;
    public final int aeModeOn = CONTROL_AE_MODE_ON;
    public int aeModeLock = CONTROL_AE_STATE_LOCKED;
    public int aeCurrentPrev = CONTROL_AE_MODE_ON;
    public int frameCount = 25;
    public int lumenCount = 3;
    public int chromaCount = 12;
    public boolean enhancedProcess = false;
    public boolean grid = false;
    public boolean watermark = false;
    public boolean afdata = false;
    public boolean roundedge = true;
    public boolean align = true;
    public boolean hdrx = true;
    public boolean hdrxNR = true;
    public double saturation = 1.0;
    public double sharpness = 0.7;
    public double contrastMpy = 1.0;
    public int contrastConst = 0;
    public double compressor = 1.6;
    public double gain = 1.6;
    public boolean rawSaver = false;
    public String lastPicture = null;
    public boolean ManualMode = false;
    public boolean QuadBayer = false;
    public int cfaPattern = -1;
    public boolean remosaic = false;
    public boolean eisPhoto = true;
    public boolean fpsPreview = false;
    public int alignAlgorithm = 0;
    public String mCameraID = "0";
    private int count = 0;
    public CameraMode selectedMode = CameraMode.DEFAULT;
    public enum CameraMode {
        DEFAULT(0),
        NIGHT(1),
        UNLIMITED(2);
        public final int mNum;
        CameraMode(int number) {
            mNum = number;
        }
    }
    private SharedPreferences.Editor sharedPreferencesEditor;
    private SharedPreferences sharedPreferences;
    Settings(){
        try {
            load();
        }catch (Exception e){
            SharedPreferences.Editor ed = sharedPreferences.edit();
            ed.clear();
            ed.commit();
            load();
        }
    }
    public void load() {
        sharedPreferences = MainActivity.act.getPreferences(MODE_PRIVATE);
        noiseReduction = get(noiseReduction,"NoiseReduction");
        frameCount = get(frameCount,"FrameCount");
        align = get(align,"Alignment");
        lumenCount = get(lumenCount,"LumaCount");
        chromaCount = get(chromaCount,"ChromaCount");
        enhancedProcess = get(enhancedProcess,"EnhancedProc");
        grid = get(grid,"Grid");
        watermark = get(watermark,"Watermark");
        afdata = get(afdata,"AFData");
        roundedge = get(roundedge,"RoundEdges");
        sharpness = get(sharpness,"Sharpness");
        contrastMpy = get(contrastMpy,"ContrastMpy");
        contrastConst = get(contrastConst,"ContrastConst");
        saturation = get(saturation,"Saturation");
        compressor = get(compressor,"Compressor");
        gain = get(gain,"Gain");
        hdrx = get(hdrx,"Hdrx");
        cfaPattern = get(cfaPattern,"CFA");
        rawSaver = get(rawSaver,"SaveRaw");
        remosaic = get(remosaic,"Remosaic");
        eisPhoto = get(eisPhoto,"EisPhoto");
        QuadBayer = get(QuadBayer,"QuadBayer");
        fpsPreview = get(fpsPreview,"FpsPreview");
        hdrxNR = get(hdrxNR,"HdrxNR");
        alignAlgorithm = get(alignAlgorithm,"AlignmentAlgo");
        selectedMode = get(selectedMode, "SelectedMode");
        count = -1;
        mCameraID = sharedPreferences.getString("Camera", mCameraID);
        count = 0;
    }

    public void save() {
        sharedPreferences = MainActivity.act.getPreferences(MODE_PRIVATE);
        sharedPreferencesEditor = sharedPreferences.edit();
        put(noiseReduction,"NoiseReduction");
        put(frameCount,"FrameCount");
        put(align,"Alignment");
        put(lumenCount,"LumaCount");
        put(chromaCount,"ChromaCount");
        put(enhancedProcess,"EnhancedProc");
        put(grid,"Grid");
        put(watermark,"Watermark");
        put(afdata,"AFData");
        put(roundedge,"RoundEdges");
        put(sharpness,"Sharpness");
        put(contrastMpy,"ContrastMpy");
        put(contrastConst,"ContrastConst");
        put(saturation,"Saturation");
        put(compressor,"Compressor");
        put(gain,"Gain");
        put(hdrx,"Hdrx");
        put(cfaPattern,"CFA");
        put(rawSaver,"SaveRaw");
        put(remosaic,"Remosaic");
        put(eisPhoto,"EisPhoto");
        put(QuadBayer,"QuadBayer");
        put(fpsPreview,"FpsPreview");
        put(hdrxNR,"HdrxNR");
        put(alignAlgorithm,"AlignmentAlgo");
        put(selectedMode, "SelectedMode");
        count = -1;
        sharedPreferencesEditor.putString("Camera", mCameraID);
        sharedPreferencesEditor.apply();
        count = 0;
    }

    public void saveID(){
        count = -1;
        sharedPreferences = MainActivity.act.getPreferences(MODE_PRIVATE);
        sharedPreferencesEditor = sharedPreferences.edit();
        sharedPreferencesEditor.putString("Camera", mCameraID);
        sharedPreferencesEditor.apply();
        count = 0;
    }
    public void applyRes(CaptureRequest.Builder captureBuilder) {
        captureBuilder.set(HOT_PIXEL_MODE, HOT_PIXEL_MODE_HIGH_QUALITY);
        captureBuilder.set(CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
        //captureBuilder.set(CONTROL_AF_MODE, CONTROL_AF_MODE_OFF);
        //captureBuilder.set(STATISTICS_LENS_SHADING_MAP_MODE, STATISTICS_LENS_SHADING_MAP_MODE_ON);
        //captureBuilder.set(CONTROL_SCENE_MODE,CONTROL_SCENE_MODE_HDR);
        //captureBuilder.set(EDGE_MODE, EDGE_MODE_HIGH_QUALITY);
    }
    @SuppressLint("InlinedApi")
    public void applyPrev(CaptureRequest.Builder captureBuilder) {
        Camera2ApiAutoFix.Apply();
        captureBuilder.set(NOISE_REDUCTION_MODE, NOISE_REDUCTION_MODE_HIGH_QUALITY);
        captureBuilder.set(CONTROL_AE_MODE, aeModeOn);
        //captureBuilder.set(COLOR_CORRECTION_MODE,COLOR_CORRECTION_MODE_HIGH_QUALITY);
        captureBuilder.set(LENS_OPTICAL_STABILIZATION_MODE,LENS_OPTICAL_STABILIZATION_MODE_ON);//Fix ois bugs for preview and burst
        //captureBuilder.set(CONTROL_AE_EXPOSURE_COMPENSATION,-1);
        Range range = CameraFragment.mCameraCharacteristics.get(CONTROL_AE_COMPENSATION_RANGE);
        if(selectedMode == CameraMode.NIGHT && range != null) captureBuilder.set(CONTROL_AE_EXPOSURE_COMPENSATION,(int)range.getUpper());
        /*Point size = new Point(Interface.i.camera.mImageReaderPreview.getWidth(),Interface.i.camera.mImageReaderPreview.getHeight());
        double sizex = size.x;
        double sizey = size.y;*/
        //captureBuilder.set(CONTROL_AE_TARGET_FPS_RANGE,new Range<>(24,60));
        /*MeteringRectangle[] rectm8 = new MeteringRectangle[2];
        rectm8[0] = new MeteringRectangle(new Point((int)(sizex/2.0),(int)(sizey/2.0)),new Size((int)(sizex*2.0/4.0),(int)(sizey*2.0/4.0)),10);
        rectm8[1] = new MeteringRectangle(new Point((int)(sizex/2.0),(int)(sizey/2.0)),new Size((int)(sizex/7),(int)(sizey/7)),30);
        MeteringRectangle[] rectaf = new MeteringRectangle[1];
        rectaf[0] =  new MeteringRectangle(new Point((int)(sizex/2.0),(int)(sizey/2.0)),new Size((int)(sizex/4),(int)(sizey/4)),10);
        //captureBuilder.set(CONTROL_AF_REGIONS,rectaf);
        captureBuilder.set(CONTROL_AE_REGIONS,rectm8);
        //captureBuilder.set(CONTROL_AF_MODE, Interface.i.settings.afMode);*/
        Object focus = captureBuilder.get(CONTROL_AF_MODE);
        Log.d(TAG,"InDeviceFocus:"+(int)(focus));
        if(focus != null) afMode = (int) focus;
        Interface.i.touchFocus.onConfigured = false;
        initialAF = captureBuilder.get(CONTROL_AF_REGIONS);
        initialAE = captureBuilder.get(CONTROL_AE_REGIONS);
        //Interface.i.touchFocus.setFocus(size.x/2,size.y/2);
        Interface.i.touchFocus.onConfigured = true;
        captureBuilder.set(TONEMAP_MODE,TONEMAP_MODE_GAMMA_VALUE);
        float[] rgb = new float[64];
        for(int i =0; i<64; i+=2){
            float x = ((float)i)/64.f;
            rgb[i] = x;
            float output = 2.8114f*x+-3.5701f*x*x+1.6807f*x*x*x;
            output = Math.max(output,0.f);
            output = Math.min(output,1.f);
            //Log.d(TAG,"Curve:"+output);
            rgb[i+1] = output;
        }
        TonemapCurve tonemapCurve = new TonemapCurve(rgb,rgb,rgb);
        captureBuilder.set(TONEMAP_CURVE,tonemapCurve);
    }
    void put(int in,String name) {
        Log.d(TAG,"Saved "+name+":"+in);
        sharedPreferencesEditor.putInt("ID"+String.format("%03d",Integer.parseInt(mCameraID))+"_i_"+name, in);
        count++;
    }

    void put(double in,String name) {
        Log.d(TAG,"Saved "+name+":"+in);
        sharedPreferencesEditor.putFloat("ID"+String.format("%03d",Integer.parseInt(mCameraID))+"_d_"+name, (float) in);
        count++;
    }

    void put(String in,String name) {
        Log.d(TAG,"Saved "+name+":"+in);
        sharedPreferencesEditor.putString("ID"+String.format("%03d",Integer.parseInt(mCameraID))+"_s_"+name, in);
        count++;
    }

    void put(boolean in,String name) {
        Log.d(TAG,"Saved "+name+":"+in);
        sharedPreferencesEditor.putBoolean("ID"+String.format("%03d",Integer.parseInt(mCameraID))+"_b_"+name, in);
        count++;
    }
    void put(CameraMode in,String name) {
        Log.d(TAG,"Saved "+name+":"+in);
        sharedPreferencesEditor.putInt("ID"+String.format("%03d",Integer.parseInt(mCameraID))+"_m_"+name, in.mNum);
        count++;
    }

    boolean get(boolean in,String name) {
        boolean result = sharedPreferences.getBoolean("ID"+String.format("%03d",Integer.parseInt(mCameraID))+"_b_"+name, in);
        Log.d(TAG,"Loaded "+name+":"+result);
        count++;
        return result;
    }

    int get(int cur,String name) {
        int result;
        result = sharedPreferences.getInt("ID"+String.format("%03d",Integer.parseInt(mCameraID))+"_i_"+name, cur);
        Log.d(TAG,"Loaded "+name+":"+result);
        count++;
        return result;
    }

    double get(double cur,String name) {
        double result;
        result = sharedPreferences.getFloat("ID"+String.format("%03d",Integer.parseInt(mCameraID))+"_d_"+name, (float) (cur));
        Log.d(TAG,"Loaded "+name+":"+result);
        count++;
        return result;
    }

    String get(String cur,String name) {
        String result;
        result = (sharedPreferences.getString("ID"+String.format("%03d",Integer.parseInt(mCameraID))+"_s_"+name, (cur)));
        Log.d(TAG,"Loaded "+name+":"+result);
        count++;
        return result;
    }
    CameraMode get(CameraMode cur,String name) {
        int result;
        result = sharedPreferences.getInt("ID"+String.format("%03d",Integer.parseInt(mCameraID))+"_m_"+name, cur.mNum);
        Log.d(TAG,"Loaded "+name+":"+result);
        count++;
        switch (result){
            case(0): return CameraMode.DEFAULT;
            case(1): return CameraMode.NIGHT;
            case(2): return CameraMode.UNLIMITED;
        }
        return CameraMode.DEFAULT;
    }
    public void ExportSettings(){
       save();
       Map<String, ?> allKeys =  sharedPreferences.getAll();
       File configFile = new File(Environment.getExternalStorageDirectory()+"//DCIM//PhotonCamera//Settings.ini");
       Properties props = new Properties();
       try {
           if (!configFile.exists()) configFile.createNewFile();
           props.load(new FileInputStream(configFile));
       }
       catch(IOException e){
           e.printStackTrace();
           return;
       }
        for (Map.Entry<String, ?> entry : allKeys.entrySet()) {
           props.setProperty(entry.getKey(),entry.getValue().toString());
           Log.v(TAG,"setProperty:"+entry.getKey()+" = "+entry.getValue().toString());
       }

        try {
            props.store(new FileOutputStream(configFile),"PhotonCamera settings file");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public void ImportSettings(){
        Map<String, ?> allKeys =  sharedPreferences.getAll();
        File configFile = new File(Environment.getExternalStorageDirectory()+"//DCIM//PhotonCamera//Settings.ini");
        Properties props = new Properties();
        try {
            if (!configFile.exists()) configFile.createNewFile();
            props.load(new FileInputStream(configFile));
        }
        catch(IOException e){
            e.printStackTrace();
            return;
        }
        mCameraID = props.getProperty("Camera");
        String ids = props.getProperty("Cameras",CameraManager2.cameraManager2.getCameraIdList().toString());
        ids = ids.replace("]","");
        ids = ids.replace("[","");
        ids = ids.replace(" ","");
        String idarr[] = ids.split(",");
        CameraManager2.cameraManager2.mCameraIDs.clear();
        for(String id : idarr) {
            Log.d(TAG,"CameraID:"+id);
            CameraManager2.cameraManager2.mCameraIDs.add(id);
        }
        for (Map.Entry<Object, Object> entry : props.entrySet()){
            String property = entry.getValue().toString();
            String name = entry.getKey().toString();
            if(name.charAt(5) == '_' && name.length() > 6) {
                Log.d(TAG,"Importing:"+name.charAt(6)+" "+name);
                switch (name.charAt(6)) {
                    case 'b':
                        sharedPreferencesEditor.putBoolean(name, Boolean.parseBoolean(property));
                        break;
                    case 'i':
                        sharedPreferencesEditor.putInt(name, Integer.parseInt(property));
                        break;
                    case 'd':
                        sharedPreferencesEditor.putFloat(name, Float.parseFloat(property));
                        break;
                    case 's':
                        sharedPreferencesEditor.putString(name, property);
                        break;
                    case 'm':
                        sharedPreferencesEditor.putInt(name, Integer.parseInt(property));
                        break;
                }
            }
        }
        load();
        Interface.i.settingsActivity.set();
    }
}
