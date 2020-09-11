package com.eszdman.photoncamera.api;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.SharedPreferences;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.TonemapCurve;
import android.os.Environment;
import android.util.Log;
import android.util.Range;
import android.widget.Toast;

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
    public float[] tonemap = {
            0f,0.0036874f,0.00740041f,0.0111387f,0.0149021f,0.0186902f,0.0225028f,0.0263396f,0.0302003f,0.0340846f,0.0379923f
            ,0.041923f,0.0458765f,0.0498525f,0.0538507f,0.0578709f,0.0619127f,0.0659759f,0.0700602f,0.0741653f,0.078291f
            ,0.0824369f,0.0866027f,0.0907882f,0.0949932f,0.0992172f,0.10346f,0.107722f,0.112001f,0.116299f,0.120614f
            ,0.124947f,0.129297f,0.133664f,0.138048f,0.142447f,0.146863f,0.151295f,0.155742f,0.160204f,0.164681f
            ,0.169172f,0.173678f,0.178198f,0.182732f,0.187279f,0.19184f,0.196413f,0.200999f,0.205598f,0.210208f
            ,0.21483f,0.219464f,0.224109f,0.228765f,0.233432f,0.238109f,0.242796f,0.247493f,0.252199f,0.256915f
            ,0.26164f,0.266373f,0.271115f,0.275865f,0.280623f,0.285388f,0.290161f,0.29494f,0.299727f,0.30452f
            ,0.309319f,0.314123f,0.318934f,0.32375f,0.32857f,0.333396f,0.338226f,0.34306f,0.347898f,0.35274f
            ,0.357585f,0.362433f,0.367284f,0.372137f,0.376993f,0.381851f,0.38671f,0.39157f,0.396432f,0.401294f
            ,0.406157f,0.41102f,0.415884f,0.420747f,0.425609f,0.43047f,0.43533f,0.440189f,0.445046f,0.449901f
            ,0.454754f,0.459604f,0.464451f,0.469295f,0.474136f,0.478973f,0.483806f,0.488635f,0.493459f,0.498278f
            ,0.503093f,0.507901f,0.512705f,0.517502f,0.522293f,0.527077f,0.531855f,0.536625f,0.541389f,0.546144f
            ,0.550892f,0.555631f,0.560362f,0.565084f,0.569797f,0.574501f,0.579195f,0.58388f,0.588554f,0.593217f
            ,0.59787f,0.602512f,0.607142f,0.611761f,0.616368f,0.620963f,0.625546f,0.630115f,0.634672f,0.639215f
            ,0.643745f,0.648261f,0.652763f,0.657251f,0.661723f,0.666181f,0.670624f,0.675051f,0.679462f,0.683857f
            ,0.688236f,0.692598f,0.696943f,0.701271f,0.705581f,0.709874f,0.714148f,0.718404f,0.722642f,0.72686f
            ,0.73106f,0.73524f,0.7394f,0.74354f,0.74766f,0.751759f,0.755837f,0.759894f,0.76393f,0.767944f
            ,0.771936f,0.775905f,0.779852f,0.783776f,0.787677f,0.791555f,0.795409f,0.799238f,0.803044f,0.806825f
            ,0.810581f,0.814312f,0.818018f,0.821698f,0.825352f,0.82898f,0.832581f,0.836156f,0.839703f,0.843223f
            ,0.846715f,0.85018f,0.853616f,0.857023f,0.860402f,0.863752f,0.867073f,0.870364f,0.873624f,0.876855f
            ,0.880055f,0.883225f,0.886364f,0.889471f,0.892546f,0.89559f,0.898602f,0.901581f,0.904527f,0.90744f
            ,0.91032f,0.913167f,0.91598f,0.918758f,0.921502f,0.924212f,0.926886f,0.929525f,0.932129f,0.934697f
            ,0.937228f,0.939724f,0.942182f,0.944604f,0.946988f,0.949335f,0.951644f,0.953915f,0.956148f,0.958342f
            ,0.960497f,0.962613f,0.96469f,0.966726f,0.968723f,0.970679f,0.972595f,0.97447f,0.976303f,0.978095f
            ,0.979846f,0.981554f,0.98322f,0.984843f,0.986424f,0.987961f,0.989455f,0.990906f,0.992312f,0.993674f
            ,0.994991f,0.996264f,0.997492f,0.998674f,0.99981f
    };
    private int count = 0;
    public CameraMode selectedMode = CameraMode.PHOTO;
    public enum CameraMode {
        UNLIMITED(2),
        PHOTO(0),
        NIGHT(1),;
        //VIDEO(3);
        public final int mNum;

        CameraMode(int number) {
            mNum = number;
        }

        public static String[] names() {
            String[] names = new String[values().length];
            for (int i = 0; i < values().length; i++) {
                names[i] = values()[i].name();
            }
            return names;
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
        tonemap = get(tonemap,"Tonemap");
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
        put(tonemap,"Tonemap");
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
        Range range = CameraController.mCameraCharacteristics.get(CONTROL_AE_COMPENSATION_RANGE);
        if(selectedMode == CameraMode.NIGHT && range != null) captureBuilder.set(CONTROL_AE_EXPOSURE_COMPENSATION,(int)range.getUpper());
        /*Point size = new Point(Interface.getCameraFragment().mImageReaderPreview.getWidth(),Interface.getCameraFragment().mImageReaderPreview.getHeight());
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
        //captureBuilder.set(CONTROL_AF_MODE, Interface.getSettings().afMode);*/
        Object focus = captureBuilder.get(CONTROL_AF_MODE);
        Log.d(TAG,"InDeviceFocus:"+(int)(focus));
        if(focus != null) afMode = (int) focus;
        Interface.getTouchFocus().onConfigured = false;
        initialAF = captureBuilder.get(CONTROL_AF_REGIONS);
        initialAE = captureBuilder.get(CONTROL_AE_REGIONS);
        //Interface.getTouchFocus().setFocus(size.x/2,size.y/2);
        Interface.getTouchFocus().onConfigured = true;
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
    private void put(float[] floats, String name) {
        String floatStr = "[";
        for(int i =0; i<floats.length;i++){
            floatStr+=String.valueOf(floats[i]);
            if(i!= floats.length-1) floatStr+=",";
        }
        floatStr+="]";
        sharedPreferencesEditor.putString("Tonemap",floatStr);
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
    float[] get(float[] current,String name){
        String floatStr = "[";
        for(int i =0; i<current.length;i++){
            floatStr+=String.valueOf(current[i]);
            if(i!= current.length-1) floatStr+=",";
        }
        floatStr+="]";
        String outStr = sharedPreferences.getString("Tonemap",floatStr);
        Log.v(TAG,"FloatArr:"+outStr);
        outStr = outStr.replace("]","");
        outStr = outStr.replace("[","");
        outStr = outStr.replace(" ","");
        String outarr[] = outStr.split(",");
        for(int i =0;i<current.length;i++){
            current[i] = Float.parseFloat(outarr[i]);
        }
        return current;
    }
    CameraMode get(CameraMode cur,String name) {
        int result;
        result = sharedPreferences.getInt("ID"+String.format("%03d",Integer.parseInt(mCameraID))+"_m_"+name, cur.mNum);
        Log.d(TAG,"Loaded "+name+":"+result);
        count++;
        switch (result){
            case(0): return CameraMode.PHOTO;
            case(1): return CameraMode.NIGHT;
            case(2): return CameraMode.UNLIMITED;
        }
        return CameraMode.PHOTO;
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
        final Activity activity = Interface.getMainActivity();
        if (activity != null) {
            activity.runOnUiThread(() -> Toast.makeText(activity, "Exported", Toast.LENGTH_SHORT).show());
        }
    }
    public String[] getArr(Properties props,String propname,String def){
        String values = props.getProperty(propname,def);
        values = values.replace("]","");
        values = values.replace("[","");
        values = values.replace(" ","");
        String outarr[] = values.split(",");
        return outarr;
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
        CameraManager2.cameraManager2.mCameraIDs.clear();
        for(String id : getArr(props,"Cameras",CameraManager2.cameraManager2.getCameraIdList().toString())) {
            Log.d(TAG,"CameraID:"+id);
            CameraManager2.cameraManager2.mCameraIDs.add(id);
        }
        Log.v(TAG,"Tonemap:"+props.getProperty("Tonemap"));
        sharedPreferencesEditor.putString("Tonemap",props.getProperty("Tonemap"));
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
        sharedPreferencesEditor.apply();
        load();
        Interface.getSettingsActivity().set();
        final Activity activity = Interface.getMainActivity();
        if (activity != null) {
            activity.runOnUiThread(() -> Toast.makeText(activity, "Imported", Toast.LENGTH_SHORT).show());
        }
    }
}
