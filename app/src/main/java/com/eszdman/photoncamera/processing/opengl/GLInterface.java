package com.eszdman.photoncamera.processing.opengl;

import androidx.annotation.RawRes;

import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.app.PhotonCamera;
import com.eszdman.photoncamera.processing.render.Parameters;

import java.io.BufferedReader;
import java.io.InputStreamReader;

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

    static public String loadShader(int fragment) {
        StringBuilder source = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(PhotonCamera.getCameraActivity().getResources().openRawResource(fragment)));
        StringBuilder imports = new StringBuilder();
        for (Object line : reader.lines().toArray()) {
            String val = String.valueOf(line);
            if(val.contains("#import")){
                val = val.replace("\n","").replace(" ","").toLowerCase();
                @RawRes
                int id;
                switch (val){
                    case "#import xyytoxyz":
                        id = R.raw.import_xyy2xyz;
                        break;
                    case "#import xyztoxyy":
                        id = R.raw.import_xyz2xyy;
                        break;
                    case "#import sigmoid":
                        id = R.raw.import_sigmoid;
                        break;
                    default:
                        throw new IllegalStateException("Unexpected value: " + val);
                }
                BufferedReader reader2 = new BufferedReader(new InputStreamReader(PhotonCamera.getCameraActivity().getResources().openRawResource(id)));
                for (Object line2 : reader2.lines().toArray()) {
                    imports.append(line2);
                    imports.append("\n");
                }
            }
            source.append(line).append("\n");
        }
        source.append(imports);
        return source.toString();
    }
}
