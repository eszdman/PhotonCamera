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
        Assume.assumeTrue("Opt in with -e burstReplay true", args.getString("burstReplay", "false").equals("true"));
        assertTrue(HalideAlignment.isAvailable());
        File input = new File(args.getString("burstPath", "/storage/emulated/0/DCIM/PhotonCamera/Raw"));
        JSONObject manifest = new JSONObject(new String(Files.readAllBytes(new File(input, "manifest.json").toPath()), "UTF-8"));
        JSONArray entries = manifest.getJSONArray("frames");
        String variant = args.getString("variant", "accurate");
        assertTrue(variant.equals("accurate") || variant.equals("legacy") || variant.equals("nearest") || variant.equals("bilinear") || variant.equals("motion"));
        String outputName = args.getString("outputName",variant);
        assertTrue(outputName.matches("[a-zA-Z0-9_-]+"));
        File output = new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null), "burst-replay");
        assertTrue(output.isDirectory() || output.mkdirs());
        PhotonCamera.getSettings().loadCache();
        SharedPreferences prefs = PhotonCamera.getSettingsManagerStatic().getDefaultPreferences();
        Map<String, ?> saved = prefs.getAll();
        String[] keys = {"alignerselect", "gyroprealignment", "gyroonly", "haliderefineflow", "halideaccuratesubpixel",
                "halidemergesampling", "halideinterpolatemotion", "halidepixelfallback", "enableadaptivenoise", "enablenoisestore", "profilegpusync", "aligndebugcompare"};
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
                    .putInt(PREFIX+"halideinterpolatemotion", variant.equals("motion") ? 1 : 0)
                    .putInt(PREFIX+"halidepixelfallback", Boolean.parseBoolean(args.getString("pixelFallback","true")) ? 1 : 0)
                    .putString(PREFIX+"halidemergesampling", variant.equals("nearest") || variant.equals("bilinear") ? variant : "cubic")
                    .putInt(PREFIX+"enablenoisestore", 0).putInt(PREFIX+"profilegpusync", 0).putInt(PREFIX+"aligndebugcompare", 0).commit();
            if (args.containsKey("noiseProfile")) prefs.edit().putInt(PREFIX+"enableadaptivenoise",0).commit();
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
            long start = SystemClock.elapsedRealtimeNanos();
            pipeline.Run();
            report.put("processing_ms", (SystemClock.elapsedRealtimeNanos()-start)/1e6);
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
    private static void dumpAlignment(ArrayList<ImageFrame> frames, Parameters p, File output, String variant) throws Exception {
        nativeCall("nSetRefineEnabled",new Class[]{boolean.class},false);
        nativeCall("nSetAccurateSubpixelEnabled",new Class[]{boolean.class},!variant.equals("legacy"));
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
