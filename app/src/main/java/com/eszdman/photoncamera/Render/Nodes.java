package com.eszdman.photoncamera.Render;

import android.renderscript.RenderScript;
import android.renderscript.ScriptC;
import android.util.Log;
import com.eszdman.photoncamera.ScriptC_initial;

public class Nodes {
    private static String TAG = "Nodes";
    private long timer;
    RenderScript rs;
    ScriptC_initial initial;

    Nodes(RenderScript rs){
        this.rs = rs;
        initial = new ScriptC_initial(rs);
    }

    public void startT(){
        timer = System.currentTimeMillis();
    }

    public void endT(String msg){
        Log.d(TAG,msg+" elapsed:"+(System.currentTimeMillis()-timer)+" ms");
    }

    public void endT(ScriptC in){
        Log.d(TAG,in.getName()+" elapsed:"+(System.currentTimeMillis()-timer)+" ms");
    }
}
