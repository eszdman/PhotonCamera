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
import java.nio.FloatBuffer;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;

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
            0.00390625f,
            0.0078125f, 0.0117188f, 0.015625f, 0.0195312f, 0.0234375f, 0.0273438f, 0.03125f, 0.0351562f, 0.0390625f, 0.0429688f, 0.046875f, 0.0507812f, 0.0546875f, 0.0585938f, 0.0625f, 0.0664062f, 0.0703125f,
            0.0742188f, 0.078125f, 0.0820312f, 0.0859375f, 0.0898438f, 0.09375f, 0.0976562f, 0.101562f, 0.105469f, 0.109375f, 0.113281f, 0.117188f, 0.121094f, 0.125f, 0.128906f,
            0.132812f, 0.136719f, 0.140625f, 0.144531f, 0.148438f, 0.152344f, 0.15625f, 0.160156f, 0.164062f, 0.167969f, 0.171875f, 0.175781f, 0.179688f, 0.183594f, 0.1875f, 0.191406f, 0.195312f, 0.199219f,
            0.203125f, 0.207031f, 0.210938f, 0.214844f, 0.21875f, 0.222656f, 0.226562f, 0.230469f, 0.234375f, 0.238281f, 0.242188f, 0.246094f, 0.25f, 0.253906f, 0.257812f, 0.261719f, 0.265625f,
            0.269531f, 0.273438f, 0.277344f, 0.28125f, 0.285156f, 0.289062f, 0.292969f, 0.296875f, 0.300781f, 0.304688f, 0.308594f, 0.3125f, 0.316406f, 0.320312f, 0.324219f, 0.328125f, 0.332031f,
            0.335938f, 0.339844f, 0.34375f, 0.347656f, 0.351562f, 0.355469f, 0.359375f, 0.363281f, 0.367188f, 0.371094f, 0.375f, 0.378906f, 0.382812f, 0.386719f, 0.390625f, 0.394531f,
            0.398438f, 0.402344f, 0.40625f, 0.410156f, 0.414062f, 0.417969f, 0.421875f, 0.425781f, 0.429688f, 0.433594f, 0.4375f, 0.441406f, 0.445312f, 0.449219f, 0.453125f, 0.457031f, 0.460938f,
            0.464844f, 0.46875f, 0.472656f, 0.476562f, 0.480469f, 0.484375f, 0.488281f, 0.492188f, 0.496094f, 0.5f, 0.503906f, 0.507812f, 0.511719f, 0.515625f, 0.519531f, 0.523438f, 0.527344f,
            0.53125f, 0.535156f, 0.539062f, 0.542969f, 0.546875f, 0.550781f, 0.554688f, 0.558594f, 0.5625f, 0.566406f, 0.570312f, 0.574219f, 0.578125f, 0.582031f, 0.585938f, 0.589844f,
            0.59375f, 0.597656f, 0.601562f, 0.605469f, 0.609375f, 0.613281f, 0.617188f, 0.621094f, 0.625f, 0.628906f, 0.632812f, 0.636719f, 0.640625f, 0.644531f, 0.648438f, 0.652344f, 0.65625f,
            0.660156f, 0.664062f, 0.667969f, 0.671875f, 0.675781f, 0.679688f, 0.683594f, 0.6875f, 0.691406f, 0.695312f, 0.699219f, 0.703125f, 0.707031f, 0.710938f, 0.714844f, 0.71875f, 0.722656f,
            0.726562f, 0.730469f, 0.734375f, 0.738281f, 0.742188f, 0.746094f, 0.75f, 0.753906f, 0.757812f, 0.761719f, 0.765625f, 0.769531f, 0.773438f, 0.777344f, 0.78125f, 0.785156f,
            0.789062f, 0.792969f, 0.796875f, 0.800781f, 0.804688f, 0.808594f, 0.8125f, 0.816406f, 0.820312f, 0.824219f, 0.828125f, 0.832031f, 0.835938f, 0.839844f, 0.84375f, 0.847656f, 0.851562f,
            0.855469f, 0.859375f, 0.863281f, 0.867188f, 0.871094f, 0.875f, 0.878906f, 0.882812f, 0.886719f, 0.890625f, 0.894531f, 0.898438f, 0.902344f, 0.90625f, 0.910156f, 0.914062f, 0.917969f,
            0.921875f, 0.925781f, 0.929688f, 0.933594f, 0.9375f, 0.941406f, 0.945312f, 0.949219f, 0.953125f, 0.957031f, 0.960938f, 0.964844f, 0.96875f, 0.972656f, 0.976562f, 0.980469f,
            0.984375f, 0.988281f, 0.992188f, 0.996094f, 1.0f
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
        Range range = CameraFragment.mCameraCharacteristics.get(CONTROL_AE_COMPENSATION_RANGE);
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
        floatStr = sharedPreferences.getString("Tonemap",floatStr);
        floatStr = floatStr.replace("]","");
        floatStr = floatStr.replace("[","");
        floatStr = floatStr.replace(" ","");
        String outarr[] = floatStr.split(",");
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
        Set<String> floatText = new HashSet<>();
        for(int i =0; i<tonemap.length;i++){
            floatText.add(String.valueOf(tonemap[i]));
        }
        String floatStr = "[";
        for(int i =0; i<tonemap.length;i++){
            floatStr+=String.valueOf(tonemap[i]);
            if(i!= tonemap.length-1) floatStr+=",";
        }
        floatStr+="]";
        int cnt = 0;
        for (String tonemapstr : getArr(props,"Tonemap",sharedPreferences.getString("Tonemap",floatStr).toString())){
            tonemap[cnt] = Float.parseFloat(tonemapstr);
            cnt++;
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
        Interface.getSettingsActivity().set();
        final Activity activity = Interface.getMainActivity();
        if (activity != null) {
            activity.runOnUiThread(() -> Toast.makeText(activity, "Imported", Toast.LENGTH_SHORT).show());
        }
    }
}
