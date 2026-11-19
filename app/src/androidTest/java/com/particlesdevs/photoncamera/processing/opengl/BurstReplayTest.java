package com.particlesdevs.photoncamera.processing.opengl;

import android.content.SharedPreferences;
import android.graphics.Point;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Pair;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.processing.ImageFrame;
import com.particlesdevs.photoncamera.processing.cpu.HalideAlignment;
import com.particlesdevs.photoncamera.processing.opengl.scripts.ESD4D;
import com.particlesdevs.photoncamera.processing.parameters.FrameNumberSelector;
import com.particlesdevs.photoncamera.processing.parameters.IsoExpoSelector;
import com.particlesdevs.photoncamera.processing.render.NoiseModeler;
import com.particlesdevs.photoncamera.processing.render.Parameters;
import com.particlesdevs.photoncamera.util.Allocator;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Map;
import static org.junit.Assert.*;

/** Opt-in replay of saved inputs through the real ESD4D alignment AND merge. */
@RunWith(AndroidJUnit4.class)
public class BurstReplayTest {
    private static final String PREFIX = "pref_tunable_esd4d_";
    @Test public void replaySavedBurst() throws Exception {
        Bundle args = InstrumentationRegistry.getArguments();
        assertFalse("Removed experiment: use automatic noise-aware alignment", args.containsKey("gaussianScore"));
        assertFalse("Wider search failed the regression tests and was removed", args.containsKey("wideSearch"));
        assertFalse("Pixel fallback was removed; replay always uses the estimated warp", args.containsKey("pixelFallback"));
        Assume.assumeTrue("Opt in with -e burstReplay true", args.getString("burstReplay", "false").equals("true"));
        assertTrue(HalideAlignment.isAvailable());
        File input = new File(args.getString("burstPath", "/storage/emulated/0/DCIM/PhotonCamera/Raw"));
        JSONObject manifest = new JSONObject(new String(Files.readAllBytes(new File(input, "manifest.json").toPath()), "UTF-8"));
        JSONArray entries = manifest.getJSONArray("frames");
        String variant = args.getString("variant", "accurate");
        assertTrue(variant.equals("accurate") || variant.equals("legacy") || variant.equals("nearest") || variant.equals("bilinear") || variant.equals("motion") || variant.equals("lanczos2"));
        String outputName = args.getString("outputName",variant);
        assertTrue(outputName.matches("[a-zA-Z0-9_-]+"));
        File output = new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null), "burst-replay");
        assertTrue(output.isDirectory() || output.mkdirs());
        PhotonCamera.getSettings().loadCache();
        SharedPreferences prefs = PhotonCamera.getSettingsManagerStatic().getDefaultPreferences();
        Map<String, ?> saved = prefs.getAll();
        String[] keys = {"alignerselect", "gyroprealignment", "gyroonly", "haliderefineflow", "halideaccuratesubpixel",
                "halidemergesampling", "halideinterpolatemotion", "halidecoarseglobal", "cfaresidualfusion", "cfaresidualrobustness", "enablehotpixelcorrection", "enableadaptivenoise", "enablenoisestore", "profilegpusync", "aligndebugcompare"};
        int savedCount = FrameNumberSelector.frameCount;
        double savedStrength = PhotonCamera.getSettings().mergeStrength;
        ArrayList<ImageFrame> frames = new ArrayList<>();
        ESD4D pipeline = null;
        ByteBuffer encoded = null;
        try {
            SharedPreferences.Editor edit = prefs.edit();
            edit.putString(PREFIX+"alignerselect", "halide")
                    .putInt(PREFIX+"gyroprealignment", 0).putInt(PREFIX+"gyroonly", 0)
                    .putInt(PREFIX+"haliderefineflow", 0).putInt(PREFIX+"halideaccuratesubpixel", variant.equals("legacy") ? 0 : 1)
                    .putInt(PREFIX+"halidecoarseglobal", Boolean.parseBoolean(args.getString("coarseGlobal","false")) ? 1 : 0)
                    .putInt(PREFIX+"halideinterpolatemotion", variant.equals("motion") ? 1 : 0)
                    .putFloat(PREFIX+"cfaresidualfusion", Float.parseFloat(args.getString("residualFusion","0")))
                    .putInt(PREFIX+"cfaresidualrobustness", Boolean.parseBoolean(args.getString("residualRobustness","true")) ? 1 : 0)
                    .putInt(PREFIX+"enablehotpixelcorrection", Boolean.parseBoolean(args.getString("hotPixels","false")) ? 1 : 0)
                    .putString(PREFIX+"halidemergesampling", variant.equals("nearest") || variant.equals("bilinear") || variant.equals("lanczos2") ? variant : "cubic")
                    .putInt(PREFIX+"enablenoisestore", 0)
                    .putInt(PREFIX+"profilegpusync", Boolean.parseBoolean(args.getString("profileGpuSync","false")) ? 1 : 0)
                    .putInt(PREFIX+"aligndebugcompare", 0).commit();
            if (args.containsKey("noiseProfile")) prefs.edit().putInt(PREFIX+"enableadaptivenoise",0).commit();
            else if (args.containsKey("adaptiveNoise")) prefs.edit().putInt(PREFIX+"enableadaptivenoise",
                    Boolean.parseBoolean(args.getString("adaptiveNoise")) ? 1 : 0).commit();
            FrameNumberSelector.frameCount = entries.length();
            PhotonCamera.getSettings().mergeStrength = Float.parseFloat(args.getString("mergeStrength", "-0.5"));
            Parameters parameters = null;
            for (int i=0; i<entries.length(); ++i) {
                JSONObject entry = entries.getJSONObject(i);
                File path = new File(input, entry.getString("name"));
                // No path traversal in a replay manifest.
                assertEquals(input.getCanonicalFile(), path.getCanonicalFile().getParentFile());
                try (TinyDng dng = new TinyDng(path.toString())) {
                    JSONObject m = dng.metadata, exif = entry.getJSONObject("exif");
                    assertEquals(entry.getInt("width"), m.getInt("width"));
                    assertEquals(entry.getInt("height"), m.getInt("height"));
                    String[] black = exif.getString("BlackLevel").trim().split("\\s+");
                    assertTrue(black.length == 1 || black.length == 4);
                    for (int site=0;site<4;++site) assertEquals("Black level at Bayer site "+site,
                            Double.parseDouble(black[site%black.length]),m.getJSONArray("black").getDouble(site),0.001);
                    MessageDigest digest = MessageDigest.getInstance("SHA-256");
                    digest.update(dng.pixels.duplicate());
                    assertEquals("Decoded tinydng pixels: "+path.getName(), entry.getString("pixel_sha256"), hex(digest.digest()));
                    if (parameters == null) parameters = parameters(m, exif);
                    assertEquals(parameters.rawSize.x, m.getInt("width"));
                    assertEquals(parameters.rawSize.y, m.getInt("height"));
                    assertEquals(parameters.whiteLevel, m.getInt("white"));
                    ImageFrame frame = new ImageFrame(dng.pixels);
                    frame.width = parameters.rawSize.x; frame.height = parameters.rawSize.y;
                    frame.timestamp = entry.getLong("timestamp"); frame.number = i;
                    int iso = exif.getInt("ISO");
                    long exposure = Math.round(exif.getDouble("ExposureTime")*1e9);
                    frame.pair = new IsoExpoSelector.ExpoPair(exposure, 1000L, 1000000000L, iso, 25, 6400, 100);
                    frame.pair.layerMpy = (float)entry.getDouble("layer");
                    frame.pair.curlayer = IsoExpoSelector.ExpoPair.exposureLayer.Normal;
                    frames.add(frame);
                }
            }
            assertNotNull(parameters);
            if (args.containsKey("noiseProfile")) {
                assertTrue(args.getString("noiseProfile").matches("[a-zA-Z0-9_.-]+"));
                JSONObject calibration = new JSONObject(new String(Files.readAllBytes(new File(input,args.getString("noiseProfile")).toPath()),"UTF-8"));
                JSONArray noise=calibration.getJSONArray("noise_profile"); assertEquals(6,noise.length());
                Pair<Double,Double>[] model=new Pair[3];
                for(int i=0;i<3;++i) model[i]=new Pair<>(noise.getDouble(2*i),noise.getDouble(2*i+1));
                parameters.noiseModeler=new NoiseModeler(model,3200,parameters.iso,parameters.cfaPattern,null);
            }
            if (args.getString("dumpAlignment", "false").equals("true")) {
                dumpAlignment(frames, parameters, output, variant);
                if (args.getString("onlyAlignment", "false").equals("true")) return;
            }
            JSONObject report = new JSONObject();
            report.put("variant", variant).put("frame_count", frames.size()).put("reference", manifest.getString("reference"));
            JSONObject tunables = new JSONObject();
            for (Map.Entry<String,?> entry : prefs.getAll().entrySet()) {
                if (entry.getKey().startsWith(PREFIX)) tunables.put(entry.getKey(),entry.getValue());
            }
            report.put("input_manifest", manifest).put("preferences", tunables);
            report.put("merge_strength", PhotonCamera.getSettings().mergeStrength);
            report.put("black_level",new JSONArray(parameters.blackLevel));
            report.put("noise_override",args.getString("noiseProfile","none"));
            pipeline = new ESD4D(parameters.rawSize, frames);
            pipeline.parameters = parameters;
            if (Boolean.parseBoolean(args.getString("dumpAtlas", "false")) || args.containsKey("alignmentOverride")) {
                File diagnostics = new File(output,outputName+"-alignment");
                assertTrue(diagnostics.isDirectory() || diagnostics.mkdirs());
                pipeline.alignmentObserver=(atlas,tile,grid,tilesX)->{
                    try {
                        JSONObject metadata=new JSONObject().put("atlas_size",new JSONArray(new int[]{atlas.mSize.x,atlas.mSize.y}))
                                .put("frame_grid",new JSONArray(new int[]{grid.x,grid.y})).put("tile_raw",tile)
                                .put("tiles_x",tilesX).put("override",args.getString("alignmentOverride","none"));
                        dumpTextureRegion(diagnostics,"original",atlas,new int[]{0,0,atlas.mSize.x,atlas.mSize.y});
                        if (args.containsKey("alignmentOverride")) {
                            File override=new File(input,args.getString("alignmentOverride"));
                            assertEquals(input.getCanonicalFile(),override.getCanonicalFile().getParentFile());
                            byte[] data=Files.readAllBytes(override.toPath());
                            assertEquals(atlas.mSize.x*atlas.mSize.y*16,data.length);
                            ByteBuffer buffer=ByteBuffer.allocateDirect(data.length).order(java.nio.ByteOrder.nativeOrder());
                            buffer.put(data).rewind();atlas.loadData(buffer);
                            assertEquals(android.opengl.GLES31.GL_NO_ERROR,android.opengl.GLES31.glGetError());
                            dumpTextureRegion(diagnostics,"used",atlas,new int[]{0,0,atlas.mSize.x,atlas.mSize.y});
                        }
                        Files.write(new File(diagnostics,"metadata.json").toPath(),metadata.toString(2).getBytes("UTF-8"));
                    } catch(Exception e) { throw new IllegalStateException("Alignment diagnostics failed",e); }
                };
            }
            if (Boolean.parseBoolean(args.getString("dumpMerge", "false"))) {
                File diagnostics = new File(output,outputName+"-merge");
                assertTrue(diagnostics.isDirectory() || diagnostics.mkdirs());
                // Packed sensor coordinates for the tester's upright (1446,4088),
                // the right white panel, and the earlier (900,2868) detail.
                int[][] regions={{1920,704,128,224},{720,144,192,112},{1376,1024,160,160}};
                if (args.containsKey("dumpMergeRegions")) {
                    JSONArray boxes=new JSONArray(args.getString("dumpMergeRegions"));
                    regions=new int[boxes.length()][4];
                    for(int i=0;i<regions.length;++i) for(int j=0;j<4;++j) regions[i][j]=boxes.getJSONArray(i).getInt(j);
                }
                final int[][] selectedRegions=regions;
                JSONArray records=new JSONArray();
                pipeline.mergeObserver=(frame,weight,ns,no,previous,candidate,merged,kernels)->{
                    try {
                        JSONObject record=new JSONObject().put("frame",frame).put("weight",weight)
                                .put("noise_s",ns).put("noise_o",no);
                        JSONArray boxes=new JSONArray();
                        for(int i=0;i<selectedRegions.length;i++) {
                            int[] box=selectedRegions[i]; boxes.put(new JSONArray(box));
                            dumpTextureRegion(diagnostics,String.format(java.util.Locale.ROOT,"f%02d-r%d-base",frame,i),previous,box);
                            dumpTextureRegion(diagnostics,String.format(java.util.Locale.ROOT,"f%02d-r%d-candidate",frame,i),candidate,box);
                            dumpTextureRegion(diagnostics,String.format(java.util.Locale.ROOT,"f%02d-r%d-merged",frame,i),merged,box);
                        }
                        record.put("regions",boxes);records.put(record);
                        if(records.length()==1) {
                            dumpTextureRegion(diagnostics,"kernels",kernels,new int[]{0,0,kernels.mSize.x,kernels.mSize.y});
                            Files.write(new File(diagnostics,"kernel-size.json").toPath(),new JSONArray(new int[]{kernels.mSize.x,kernels.mSize.y}).toString().getBytes("UTF-8"));
                        }
                        Files.write(new File(diagnostics,"records.json").toPath(),records.toString(2).getBytes("UTF-8"));
                    } catch(Exception e) { throw new IllegalStateException("Merge diagnostics failed",e); }
                };
            }
            float alignmentNoiseScale = Float.parseFloat(args.getString("alignmentNoiseScale","1"));
            assertTrue("alignmentNoiseScale must be finite and nonnegative", Float.isFinite(alignmentNoiseScale) && alignmentNoiseScale >= 0);
            pipeline.alignmentNoiseModelScale = alignmentNoiseScale;
            report.put("alignment_noise_scale",alignmentNoiseScale);
            report.put("noise_aware_alignment", !variant.equals("legacy") && alignmentNoiseScale > 0);
            long start = SystemClock.elapsedRealtimeNanos();
            pipeline.Run();
            report.put("processing_ms", (SystemClock.elapsedRealtimeNanos()-start)/1e6);
            report.put("diagnostic_readbacks", pipeline.mergeObserver != null || pipeline.alignmentObserver != null);
            report.put("alignment_override",args.getString("alignmentOverride","none"));
            report.put("coarse_global",Boolean.parseBoolean(args.getString("coarseGlobal","false")));
            if (pipeline.hotPixelResult != null) {
                JSONObject hot = new JSONObject();
                hot.put("candidates", pipeline.hotPixelResult.candidates);
                hot.put("rejected_overfull", pipeline.hotPixelResult.rejectedOverfull);
                JSONArray sites = new JSONArray(), counts = new JSONArray();
                for (int site : pipeline.hotPixelResult.sites)
                    sites.put(new JSONArray().put(site % parameters.rawSize.x).put(site / parameters.rawSize.x));
                for (int count : pipeline.hotPixelResult.correctedPerFrame) counts.put(count);
                hot.put("raw_xy", sites).put("corrected_per_frame", counts);
                report.put("hot_pixels", hot);
            }
            encoded = Allocator.createU16FromF16(pipeline.Output, parameters.rawSize.x, parameters.rawSize.y, 65535, new float[4]);
            assertNotNull(encoded);
            File rawFile = new File(output, outputName+".u16");
            try (FileOutputStream out = new FileOutputStream(rawFile)) {
                encoded.rewind(); while (encoded.hasRemaining()) out.getChannel().write(encoded);
            }
            assertEquals((long)parameters.rawSize.x*parameters.rawSize.y*2, rawFile.length());
            report.put("width", parameters.rawSize.x).put("height", parameters.rawSize.y);
            JSONArray noise = new JSONArray();
            for (Pair<Double,Double> n : parameters.noiseModeler.baseModel) noise.put(n.first).put(n.second);
            report.put("effective_noise_profile", noise);
            Files.write(new File(output, outputName+".json").toPath(), report.toString(2).getBytes("UTF-8"));
            android.util.Log.i("BurstReplay", "RESULT "+variant+" processing_ms="+report.getDouble("processing_ms")+" output="+rawFile);
        } finally {
            if (encoded != null) Allocator.free(encoded);
            if (pipeline != null) pipeline.close();
            for (ImageFrame frame : frames) frame.close();
            FrameNumberSelector.frameCount = savedCount;
            PhotonCamera.getSettings().mergeStrength = savedStrength;
            SharedPreferences.Editor restore = prefs.edit();
            for (String suffix : keys) {
                String key = PREFIX+suffix; Object value = saved.get(key);
                if (value == null) restore.remove(key);
                else if (value instanceof Integer) restore.putInt(key,(Integer)value);
                else if (value instanceof Float) restore.putFloat(key,(Float)value);
                else if (value instanceof Boolean) restore.putBoolean(key,(Boolean)value);
                else if (value instanceof Long) restore.putLong(key,(Long)value);
                else if (value instanceof String) restore.putString(key,(String)value);
            }
            restore.commit();
        }
    }
    private static Parameters parameters(JSONObject m, JSONObject exif) throws Exception {
        Parameters p = new Parameters();
        p.rawSize = new Point(m.getInt("width"),m.getInt("height")); p.whiteLevel = m.getInt("white");
        p.blackLevel = new float[4]; p.whitePoint = new float[3];
        for (int i=0; i<4; ++i) p.blackLevel[i]=(float)m.getJSONArray("black").getDouble(i);
        for (int i=0; i<3; ++i) p.whitePoint[i]=(float)m.getJSONArray("neutral").getDouble(i);
        String cfa=m.getJSONArray("cfa").toString();
        String[] patterns={"[0,1,1,2]","[1,0,2,1]","[1,2,0,1]","[2,1,1,0]"};
        int pattern=-1; for(int i=0;i<4;++i) if(patterns[i].equals(cfa)) pattern=i;
        assertTrue("Unsupported CFA "+cfa, pattern>=0); p.cfaPattern=(byte)pattern;
        p.tile=16; p.tilesX=p.rawSize.x/800+1;
        p.alignmentSize=new Point(p.rawSize.x/16+1,p.rawSize.y/16+1);
        p.gainMap=new float[]{1,1,1,1}; p.mapSize=new Point(1,1);
        p.physicalID=2; p.iso=exif.getInt("ISO"); p.exposureTime=Math.round(exif.getDouble("ExposureTime")*1e9);
        String[] noise=exif.getString("NoiseProfile").trim().split("\\s+");
        assertEquals(6,noise.length);
        Pair<Double,Double>[] model=new Pair[3];
        for(int i=0;i<3;++i) model[i]=new Pair<>(Double.parseDouble(noise[2*i]),Double.parseDouble(noise[2*i+1]));
        p.noiseModeler=new NoiseModeler(model,3200,p.iso,p.cfaPattern,null);
        return p;
    }
    private static String hex(byte[] data) {
        StringBuilder b=new StringBuilder(); for(byte x:data) b.append(String.format(java.util.Locale.ROOT,"%02x",x&255)); return b.toString();
    }
    private static Object nativeCall(String name, Class<?>[] types, Object... values) throws Exception {
        java.lang.reflect.Method m = HalideAlignment.class.getDeclaredMethod(name,types);
        m.setAccessible(true); return m.invoke(null,values);
    }
    private static void dumpTextureRegion(File directory,String name,GLTexture texture,int[] box) throws Exception {
        assertTrue(box[0]>=0 && box[1]>=0 && box[0]+box[2]<=texture.mSize.x && box[1]+box[3]<=texture.mSize.y);
        int[] framebuffer=new int[1],viewport=new int[4];
        android.opengl.GLES31.glGetIntegerv(android.opengl.GLES31.GL_FRAMEBUFFER_BINDING,framebuffer,0);
        android.opengl.GLES31.glGetIntegerv(android.opengl.GLES31.GL_VIEWPORT,viewport,0);
        ByteBuffer pixels=ByteBuffer.allocateDirect(box[2]*box[3]*16).order(java.nio.ByteOrder.nativeOrder());
        try {
            texture.BufferLoad();
            texture.textureBuffer(new GLFormat(GLFormat.DataType.FLOAT_32,4),pixels,box[0],box[1],box[2],box[3]);
            assertEquals(android.opengl.GLES31.GL_NO_ERROR,android.opengl.GLES31.glGetError());
            try(FileOutputStream stream=new FileOutputStream(new File(directory,name+".f32"))) {
                pixels.rewind();while(pixels.hasRemaining())stream.getChannel().write(pixels);
            }
        } finally {
            android.opengl.GLES31.glBindFramebuffer(android.opengl.GLES31.GL_FRAMEBUFFER,framebuffer[0]);
            android.opengl.GLES31.glViewport(viewport[0],viewport[1],viewport[2],viewport[3]);
        }
    }

    private static void dumpAlignment(ArrayList<ImageFrame> frames, Parameters p, File output, String variant) throws Exception {
        nativeCall("nSetRefineEnabled",new Class[]{boolean.class},false);
        nativeCall("nSetAccurateSubpixelEnabled",new Class[]{boolean.class},!variant.equals("legacy"));
        nativeCall("nSetCoarseGlobalEnabled",new Class[]{boolean.class},false);
        long ctx=(Long)nativeCall("nInit",new Class[]{int.class,int.class},p.rawSize.x,p.rawSize.y);
        assertTrue(ctx!=0);
        try {
            for(int f=0;f<frames.size();++f) {
                ByteBuffer normalized=Allocator.createF16(frames.get(f).buffer,p.rawSize.x,p.rawSize.y,p.whiteLevel,p.blackLevel);
                try {
                    Class<?>[] signature={long.class,ByteBuffer.class,float.class};
                    if(f==0) assertEquals(0,((Integer)nativeCall("nBase",signature,ctx,normalized,1f)).intValue());
                    else {
                        float[] vectors=(float[])nativeCall("nAlignFrame",signature,ctx,normalized,1f/frames.get(f).pair.layerMpy);
                        ByteBuffer bytes=ByteBuffer.allocate(vectors.length*4).order(java.nio.ByteOrder.LITTLE_ENDIAN);
                        bytes.asFloatBuffer().put(vectors);
                        Files.write(new File(output,variant+String.format(java.util.Locale.ROOT,"-flow-f%02d.f32",f)).toPath(),bytes.array());
                    }
                } finally { Allocator.free(normalized); }
            }
        } finally { nativeCall("nRelease",new Class[]{long.class},ctx); }
    }
}
