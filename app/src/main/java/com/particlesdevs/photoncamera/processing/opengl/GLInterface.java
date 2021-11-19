package com.particlesdevs.photoncamera.processing.opengl;

import android.util.Log;

import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.processing.render.Parameters;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static com.particlesdevs.photoncamera.processing.opengl.GLProg.glVersion;

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
        return readProgram(reader,defines);
    }
    public static String loadShader(String code,ArrayList<String[]> defines){
        BufferedReader reader = new BufferedReader(new StringReader(code));
        return readProgram(reader,defines);
    }
    private static int findLeft(String in, char search){
        char left;
        for(int i =0; i<in.length();i++){
            left = in.charAt(i);
            if(left == search) return i;
        }
        return 0;
    }
    private static int findRight(String in, char search){
        char right;
        for(int i = in.length()-1; i>=0;i--){
            right = in.charAt(i);
            if(right == search) return i;
        }
        return in.length()-1;
    }
    private static int getParameter(String[] in, String parameter){
        for(String in2 : in){
            String[] paramVal = in2.replace(" ","").split("=");
            if(paramVal[0].equals(parameter)) return Integer.parseInt(paramVal[1]);
        }
        return 0;
    }
    public static Map<String, GLComputeLayout> getLayouts(String program){
        BufferedReader reader = new BufferedReader(new StringReader(program));
        Map<String, GLComputeLayout> layoutsMap = new HashMap<>();
        for (Object line : reader.lines().toArray()) {
            String val = String.valueOf(line);
            if(val.contains("layout")){
                String[] divided = val.split(" ");
                String last = "";
                if(divided.length > 0){
                    last = divided[divided.length-1].replace(";","").replace("\n","");
                }
                String[] parameters = val.substring(findLeft(val,'(')+1,findRight(val,')')).split(",");
                if(last.equals("in")){
                    layoutsMap.put(last,new GLComputeLayout(
                            getParameter(parameters,"local_size_x"),
                            getParameter(parameters,"local_size_y"),
                            getParameter(parameters,"local_size_z")));
                } else {
                    layoutsMap.put(last,new GLComputeLayout(getParameter(parameters,"binding")));
                }
            }
        }
        return layoutsMap;
    }
    public static String readProgram(BufferedReader reader, ArrayList<String[]> defines) {
        StringBuilder source = new StringBuilder();
        source.append(glVersion);
        int linecnt = 0;
        for (Object line : reader.lines().toArray()) {
            linecnt++;
            String val = String.valueOf(line);
            if(val.contains("#import")){
                String imported = "";
                if(!val.contains("//")) {
                    imported = PhotonCamera.getAssetLoader().getString(
                            val
                                    .replace("#", "")
                                    .replace(" ", "_")
                                    .replace("\n", "")
                                    + ".glsl");
                }
                if(!imported.equals("")){
                    source.append("#line 1\n");
                    source.append(imported);
                    source.append("\n");
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
