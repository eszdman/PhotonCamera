package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import android.graphics.Bitmap;
import android.graphics.Point;

import com.particlesdevs.photoncamera.processing.opengl.GLFormat;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;
import com.particlesdevs.photoncamera.processing.ultrahdr.UltraHdrGainMapStats;
import com.particlesdevs.photoncamera.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_NEAREST;
import static android.opengl.GLES30.GL_HALF_FLOAT;
import static android.opengl.GLES30.GL_NO_ERROR;
import static android.opengl.GLES30.GL_RED;
import static android.opengl.GLES30.GL_UNSIGNED_BYTE;
import static android.opengl.GLES30.glGetError;
import static android.opengl.GLES30.glReadPixels;

/**
 * Generates the Ultra HDR gain map from the pipeline's intermediate textures
 * at full resolution. Reads the pre-tonemap linear HDR luminance written by
 * Initial in the alpha channel (passed through the AutoExposure stage
 * unchanged) and the displayed SDR base. The gain map shader computes the
 * positive log2 ratio between the linear HDR luminance and the displayed SDR
 * base. Shadows and neutral regions remain zero-gain.
 *
 * <p>Memory-bounded by design: the pass runs in horizontal blocks with small
 * render targets and readback buffers. A first block pass accumulates the
 * gain statistics (max/min content boost), and a second block pass quantises the
 * gain map to 8 bits directly in the shader and writes it into the final
 * bitmap. The image is rotated and mirrored in-shader so the bitmap already
 * matches the final base image orientation.</p>
 */
public class GainMapGenerator extends Node {

    private static final int BLOCK_HEIGHT = 256;

    public GainMapGenerator() {
        super("", "GainMapGenerator");
    }

    @Override
    public void Compile() {
    }

    @Override
    public void AfterRun() {
    }

    @Override
    public void Run() {
        PostPipeline pipeline = (PostPipeline) basePipeline;
        WorkingTexture = previousNode.WorkingTexture;
        if (!pipeline.hdrLinearValid) {
            Log.d(Name, "UltraHDR skipped: no linear HDR data (Initial node disabled)");
            glProg.closed = true;
            return;
        }
        String stage = "size check";
        try {
            Point inSize = previousNode.WorkingTexture.mSize;
            Point cropSize = pipeline.cropSize;
            int cropW = cropSize.x;
            int cropH = cropSize.y;
            int rot;
            switch (pipeline.mParameters.cameraRotation) {
                case 90:
                    rot = 3;
                    break;
                case 180:
                    rot = 2;
                    break;
                case 270:
                    rot = 1;
                    break;
                default:
                    rot = 0;
                    break;
            }
            Point outSize = (rot == 1 || rot == 3)
                    ? new Point(cropH, cropW)
                    : new Point(cropW, cropH);
            if (outSize.x < 1 || outSize.y < 1) {
                Log.d(Name, "UltraHDR skipped: output too small (" + outSize.x + "x" + outSize.y + ")");
                glProg.closed = true;
                return;
            }
            int mirror = pipeline.mParameters.mirror ? 1 : 0;

            Bitmap outBitmap = Bitmap.createBitmap(outSize.x, outSize.y, Bitmap.Config.ARGB_8888);
            stage = "program bind";
            glProg.useAssetProgram("ultrahdr/gainmap");
            glProg.setTexture("InputBuffer", previousNode.WorkingTexture);
            glProg.setVar("GAIN_CLAMP_MIN", UltraHdrGainMapStats.MIN_LOG2_GAIN);
            glProg.setVar("GAIN_CLAMP_MAX", UltraHdrGainMapStats.MAX_LOG2_GAIN);
            glProg.setVar("GAIN_OFFSET", UltraHdrGainMapStats.GAIN_OFFSET);
            glProg.setVar("GAIN_DEADBAND", (float) UltraHdrGainMapStats.LOG2_MIN_RATIO);
            glProg.setVar("ROTATE", rot);
            glProg.setVar("MIRROR", mirror);
            glProg.setVar("RAW_SIZE", inSize.x, inSize.y);
            glProg.setVar("CROP_H", cropH);

            // Pass 1: gain statistics across all blocks.
            stage = "stats";
            int[] histogram = new int[UltraHdrGainMapStats.HISTOGRAM_BINS];
            int pixelCount = 0;
            ByteBuffer statsBuffer = ByteBuffer.allocateDirect(outSize.x * BLOCK_HEIGHT * 2)
                    .order(ByteOrder.LITTLE_ENDIAN);
            for (int y = 0; y < outSize.y; y += BLOCK_HEIGHT) {
                int h = Math.min(BLOCK_HEIGHT, outSize.y - y);
                GLTexture statsTex = new GLTexture(new Point(outSize.x, h),
                        new GLFormat(GLFormat.DataType.FLOAT_16, 1),
                        null, GL_NEAREST, GL_CLAMP_TO_EDGE);
                try {
                    glProg.setVar("GAIN_SCALE", 0.0f);
                    glProg.setVar("BLOCK_OFFSET", y);
                    glProg.drawBlocks(statsTex);
                    int blockPixels = outSize.x * h;
                    statsBuffer.clear();
                    glReadPixels(0, 0, outSize.x, h, GL_RED, GL_HALF_FLOAT, statsBuffer);
                    for (int i = 0; i < blockPixels; i++) {
                        float fl = halfToFloat(statsBuffer.getShort());
                        histogram[UltraHdrGainMapStats.binForGain(fl)]++;
                    }
                    pixelCount += blockPixels;
                } finally {
                    statsTex.close();
                }
            }
            UltraHdrGainMapStats.Stats stats =
                    UltraHdrGainMapStats.computeFromHistogram(histogram, pixelCount);
            Log.d(Name, "UltraHDR stats: total=" + stats.total
                    + " above1.05x=" + stats.aboveThresholdCount
                    + " below0.95x=" + stats.belowThresholdCount
                    + " p50=" + stats.p50 + " p999=" + stats.p999
                    + " highlightTail=" + stats.highlightTail + " shadowTail=" + stats.shadowTail
                    + " maxL=" + stats.maxL + " minL=" + stats.minL
                    + " maxBoost=" + stats.maxContentBoost + " minBoost=" + stats.minContentBoost);
            if (stats.maxContentBoost <= 0.0f) {
                outBitmap.recycle();
                glProg.closed = true;
                return;
            }
            pipeline.gainMapMaxBoost = stats.maxContentBoost;
            pipeline.gainMapMinBoost = stats.minContentBoost;

            // Pass 2: quantise in-shader and fill the bitmap block by block.
            stage = "quantise";
            float log2Max = (float) (Math.log(stats.maxContentBoost) / Math.log(2.0));
            float log2Min = (float) (Math.log(stats.minContentBoost) / Math.log(2.0));
            float log2Span = log2Max - log2Min;
            if (!Float.isFinite(log2Span) || log2Span <= 0.0f) {
                throw new IllegalStateException("Invalid gain range: " + log2Min + ".." + log2Max);
            }
            float gainScale = 255.0f / log2Span;
            glProg.setVar("GAIN_MIN", log2Min);
            glProg.setVar("GAIN_SCALE", gainScale);
            int[] band = new int[outSize.x * BLOCK_HEIGHT];
            ByteBuffer quantBuffer = ByteBuffer.allocateDirect(outSize.x * BLOCK_HEIGHT)
                    .order(ByteOrder.LITTLE_ENDIAN);
            for (int y = 0; y < outSize.y; y += BLOCK_HEIGHT) {
                int h = Math.min(BLOCK_HEIGHT, outSize.y - y);
                GLTexture quantTex = new GLTexture(new Point(outSize.x, h),
                        new GLFormat(GLFormat.DataType.SIMPLE_8, 1),
                        null, GL_NEAREST, GL_CLAMP_TO_EDGE);
                try {
                    glProg.setVar("BLOCK_OFFSET", y);
                    glProg.drawBlocks(quantTex);
                    int blockPixels = outSize.x * h;
                    quantBuffer.clear();
                    glReadPixels(0, 0, outSize.x, h, GL_RED, GL_UNSIGNED_BYTE, quantBuffer);
                    for (int i = 0; i < blockPixels; i++) {
                        int v = quantBuffer.get() & 0xFF;
                        band[i] = 0xFF000000 | v << 16 | v << 8 | v;
                    }
                    outBitmap.setPixels(band, 0, outSize.x, 0, y, outSize.x, h);
                } finally {
                    quantTex.close();
                }
            }
            int glError = glGetError();
            if (glError != GL_NO_ERROR) {
                Log.e(Name, "UltraHDR GL error after blocks: 0x" + Integer.toHexString(glError));
            }
            pipeline.gainMapBitmap = outBitmap;
            pipeline.gainMapSize = new Point(outSize);
            stage = "done";
            Log.d(Name, "UltraHDR gain map computed: " + outSize.x + "x" + outSize.y);
        } catch (Exception e) {
            Log.e(Name, "UltraHDR gain map generation failed at " + stage + ": "
                    + Log.getStackTraceString(e));
            pipeline.gainMapBitmap = null;
            pipeline.gainMapSize = null;
            pipeline.gainMapMaxBoost = 0.0f;
            pipeline.gainMapMinBoost = 1.0f;
        } finally {
            glProg.closed = true;
        }
    }

    private static float halfToFloat(short h) {
        int s = (h >> 15) & 0x1;
        int e = (h >> 10) & 0x1F;
        int m = h & 0x3FF;
        int f;
        if (e == 0) {
            if (m == 0) {
                f = s << 31;
            } else {
                int e2 = -14;
                while ((m & 0x400) == 0) {
                    m <<= 1;
                    e2--;
                }
                m &= 0x3FF;
                f = s << 31 | (e2 + 127) << 23 | m << 13;
            }
        } else if (e == 31) {
            f = s << 31 | 0x7F800000 | m << 13;
        } else {
            f = s << 31 | (e - 15 + 127) << 23 | m << 13;
        }
        return Float.intBitsToFloat(f);
    }
}
