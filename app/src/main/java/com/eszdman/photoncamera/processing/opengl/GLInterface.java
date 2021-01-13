package com.eszdman.photoncamera.processing.opengl;

import android.util.Log;

import androidx.annotation.RawRes;

import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.app.PhotonCamera;
import com.eszdman.photoncamera.processing.render.Parameters;

import java.io.BufferedReader;
import java.io.CharArrayReader;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.ArrayList;

public class GLInterface {
    public final GLProg glProgram;
    public Parameters parameters;
    public GLCoreBlockProcessing glProcessing;
    public GLContext glContext;
    public GLUtils glUtils;

    public GLInterface(GLCoreBlockProcessing processing) {
        glProcessing = processing;
        glProgram = glProcessing.mProgram;
        glUtils = new GLUtils(glProcessing);
    }

    public GLInterface(GLContext context) {
        glContext = context;
        glProgram = glContext.mProgram;
    }
    public static String loadShader(String code){
        return loadShader(code,null);
    }
    public static String loadShader(int fragment){
        return loadShader(fragment,null);
    }
    public static String loadShader(int fragment,ArrayList<String[]> defines){
        BufferedReader reader = new BufferedReader(new InputStreamReader(PhotonCamera.getResourcesStatic().openRawResource(fragment)));
        return readprog(reader,defines);
    }
    public static String loadShader(String code,ArrayList<String[]> defines){
        BufferedReader reader = new BufferedReader(new StringReader(code));
        return readprog(reader,defines);
    }
    static public String readprog(BufferedReader reader, ArrayList<String[]> defines) {
        StringBuilder source = new StringBuilder();
        int linecnt = 0;
        for (Object line : reader.lines().toArray()) {
            linecnt++;
            String val = String.valueOf(line);
            if(val.contains("#import")){
                val = val.replace("\n","").replace(" ","").toLowerCase();
                @RawRes
                int id = 0;
                switch (val){
                    case "#importxyytoxyz":
                        id = R.raw.import_xyy2xyz;
                        break;
                    case "#importxyztoxyy":
                        id = R.raw.import_xyz2xyy;
                        break;
                    case "#importsigmoid":
                        id = R.raw.import_sigmoid;
                        break;
                    case "#importgaussian":
                        id = R.raw.import_gaussian;
                        break;
                    case "#importcubic":
                        id = R.raw.import_cubic;
                        break;
                    case "#importinterpolation":
                        id = R.raw.import_interpolation;
                        break;
                    case "#importloadbayer":
                        id = R.raw.import_loadbayer;
                        break;
                    case "#importcoords":
                        id = R.raw.import_coords;
                        break;
                    case "#importcmyk":
                        id = R.raw.import_cmyk;
                        break;
                    case "#importmedian":
                        id = R.raw.import_median;
                        break;
                }
                if(id!= 0) {
                    BufferedReader reader2 = new BufferedReader(new InputStreamReader(PhotonCamera.getResourcesStatic().openRawResource(id)));
                    for (Object line2 : reader2.lines().toArray()) {
                        source.append("#line 1\n");
                        source.append(line2);
                        source.append("\n");
                    }
                    //linecnt++;
                    source.append("#line ").append(linecnt+1).append("\n");
                }
                continue;
            }
            if(val.contains("#define") && defines != null){
                for(String[] define : defines){
                    if(val.contains(" "+define[0]+" ")){
                        line = (String)("#define "+define[0]+" "+define[1]);
                        //Log.d("GLInterface","Overwrite:"+line);
                        break;
                    }
                }
            }
            source.append(line).append("\n");
        }
        return source.toString();
    }
}
