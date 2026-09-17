package com.particlesdevs.photoncamera.processing.opengl.nodes;

import android.graphics.Point;
import com.particlesdevs.photoncamera.util.Log;

import com.particlesdevs.photoncamera.processing.opengl.GLBasePipeline;
import com.particlesdevs.photoncamera.processing.opengl.GLInterface;
import com.particlesdevs.photoncamera.processing.opengl.GLProg;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.GLUtils;

import java.util.Arrays;
import java.util.Properties;

public class Node {
    public GLTexture WorkingTexture;
    public String Name = "Node";
    public Node previousNode;
    public String Rid;
    private long timeStart;
    public GLBasePipeline basePipeline;
    public GLInterface glInt;
    public GLUtils glUtils;
    public GLProg glProg;
    public boolean LastNode = false;
    public Properties mProp;
    private final boolean loggedTuning = false;

    private Node() {
    }

    public Node(String rid, String name) {
        Rid = rid;
        Name = name;
    }

    private void tuningLog(String name, String value){
        if(loggedTuning) Log.d("Tuning",name+" = "+ value);
    }
    public boolean getTuning(String name, boolean Default){
        tuningLog(Name+"_"+name,String.valueOf(Default));
        return Boolean.parseBoolean(mProp.getProperty(Name+"_"+name,String.valueOf(Default)));
    }
    public float getTuning(String name,float Default){
        tuningLog(Name+"_"+name,String.valueOf(Default));
        return Float.parseFloat(mProp.getProperty(Name+"_"+name,String.valueOf(Default)));
    }
    public float[] getTuning(String name,float[] Default){
        String ins = Arrays.toString(Default).replace("[","").replace("]","");
        tuningLog(Name+"_"+name,ins);
        String inp = mProp.getProperty(Name+"_"+name, ins);
        String[] divided = inp.split(",");
        float[] output = new float[Default.length];
        for(int i = 0; i<divided.length;i++){
            output[i] = Float.parseFloat(divided[i]);
        }
        return output;
    }
    public double getTuning(String name,double Default){
        tuningLog(Name+"_"+name,String.valueOf(Default));
        return Double.parseDouble(mProp.getProperty(Name+"_"+name,String.valueOf(Default)));
    }
    public short getTuning(String name,short Default){
        tuningLog(Name+"_"+name,String.valueOf(Default));
        return Short.parseShort(mProp.getProperty(Name+"_"+name,String.valueOf(Default)));
    }
    public int getTuning(String name,int Default){
        tuningLog(Name+"_"+name,String.valueOf(Default));
        return Integer.parseInt(mProp.getProperty(Name+"_"+name,String.valueOf(Default)));
    }

    public void startT() {
        timeStart = System.currentTimeMillis();
    }

    public void endT(String name) {
        Log.d(Name, name + " elapsed:" + (System.currentTimeMillis() - timeStart) + " ms");
    }
    public void BeforeRun(){
        // Automatically inject tunable values before each run
        // This ensures settings changes are always picked up
        com.particlesdevs.photoncamera.settings.TunableInjector.inject(this);
    }
    public void Run() {}
    public void AfterRun(){}
    /**
     * Maximum stencil radius (image pixels) this node reads beyond each
     * output pixel. The tile driver expands every tile by the chain maximum
     * and mirror-fetches the skirt (Amaze {@code pad} pattern), so tiled output is
     * bit-exact. Contract:
     * <ul>
     *   <li>overestimate = safe (slightly smaller effective tiles),</li>
     *   <li>underestimate = seams (caught by the tiled-vs-full oracle),</li>
     *   <li>passthrough / pointwise nodes return 0,</li>
     *   <li>global nodes (pyramids, whole-frame statistics) return
     *       {@link Integer#MAX_VALUE}: they must execute full-frame, with
     *       frozen outputs consumed per tile,</li>
     *   <li>tunable-dependent radii must read the live tunable field.</li>
     * </ul>
     * Any node added to a production pipeline MUST override this unless it
     * is truly pointwise/passthrough (default 0 is only correct then).
     */
    public int halo() {
        return 0;
    }

    /**
     * Strip-region rendering for the tile driver / harness: when
     * {@code tileOut} is set, the node renders only image rows
     * [{@code tileY0}, {@code tileY1}) into it (row {@code y} stored at
     * {@code y - tileY0}). Bounds should be multiples of the node's
     * internal tile grid where one exists. Untouched on the legacy path.
     */
    public int tileY0 = 0;
    public int tileY1 = -1;
    public GLTexture tileOut = null;

    /** True while rendering a strip region (see above). */
    public boolean tileActive() {
        return tileOut != null && tileY1 >= 0;
    }

    /**
     * Harness oracle for nodes whose draw happens outside {@link #Run} (via
     * {@code drawProgramTexture} after it returns): runs after that draw, so
     * the full output is final. In-{@code Run} oracles would compare against
     * a stale unrendered texture. Default no-op.
     */
    public void postDrawOracle() {
    }
    public void BeforeCompile(){}
    public void Compile() {
        basePipeline.glint.glProgram.useAssetProgram(Rid);
    }

    public GLTexture GetProgTex() {
        return WorkingTexture;
    }
}
