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
        BufferedReader reader = new BufferedReader(new InputStreamReader(PhotonCamera.getCameraActivity().getResources().openRawResource(fragment)));
        return readprog(reader,defines);
    }
    public static String loadShader(String code,ArrayList<String[]> defines){
        BufferedReader reader = new BufferedReader(new StringReader(code));
        return readprog(reader,defines);
    }
    static public String readprog(BufferedReader reader, ArrayList<String[]> defines) {
        StringBuilder source = new StringBuilder();
        StringBuilder imports = new StringBuilder();
        for (Object line : reader.lines().toArray()) {
            String val = String.valueOf(line);
            if(val.contains("#import")){
                val = val.replace("\n","").replace(" ","").toLowerCase();
                @RawRes
                int id = 0;
                String headers = "";
                switch (val){
                    case "#importxyytoxyz":
                        id = R.raw.import_xyy2xyz;
                        headers+="vec3 xyYtoXYZ(vec3);";
                        break;
                    case "#importxyztoxyy":
                        id = R.raw.import_xyz2xyy;
                        headers+="vec3 XYZtoxyY(vec3);";
                        break;
                    case "#importsigmoid":
                        id = R.raw.import_sigmoid;
                        headers+="float sigmoid(float, float);";
                        break;
                    case "#importgaussian":
                        id = R.raw.import_gaussian;
                        headers+="float unscaledGaussian(float, float);";
                        headers+="vec3 unscaledGaussian(vec3, float);";
                        headers+="vec3 unscaledGaussian(vec3, vec3);";
                        break;
                    case "#importcubic":
                        id = R.raw.import_cubic;
                        headers+="vec4 cubic(float);";
                        break;
                    case "#importinterpolation":
                        id = R.raw.import_interpolation;
                        headers+="vec4 cubic(float);";
                        headers+="vec4 textureLinear(sampler2D, vec2);";
                        headers+="vec4 textureLinear1D(sampler2D, vec2);";
                        headers+="vec4 textureBicubic(sampler2D, vec2);";
                        headers+="vec4 textureBicubicHardware(sampler2D, vec2);";
                        headers+="vec4 textureCubic(sampler2D, vec2);";
                        headers+="vec4 textureCubicHardware(sampler2D, vec2);";
                        break;
                    case "#importloadbayer":
                        id = R.raw.import_loadbayer;
                        headers+="float[9] loadbayer9(sampler2D tex, ivec2 coords, int bayer);";
                        break;
                    case "#importcoords":
                        id = R.raw.import_coords;
                        headers+="ivec2 mirrorCoords(ivec2 xy, ivec4 bounds);";
                        break;
                    case "#importcmyk":
                        id = R.raw.import_cmyk;
                        headers+="vec3 cmyk2rgb (vec4 cmyk);";
                        headers+="vec4 rgb2cmyk (vec3 rgb);";
                }
                headers+="\n";
                if(id!= 0) {
                    BufferedReader reader2 = new BufferedReader(new InputStreamReader(PhotonCamera.getCameraActivity().getResources().openRawResource(id)));
                    for (Object line2 : reader2.lines().toArray()) {
                        imports.append(line2);
                        imports.append("\n");
                    }
                }
                source.append(headers);
                continue;
            }
            if(val.contains("#define") && defines != null){
                for(String[] define : defines){
                    if(val.contains(" "+define[0]+" ")){
                        line = (String)("#define "+define[0]+" "+define[1]);
                        Log.d("GLInterface","Overwrite:"+line);
                        break;
                    }
                }
            }
            source.append(line).append("\n");
        }
        source.append(imports);
        return source.toString();
    }
}
