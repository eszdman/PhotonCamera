package com.particlesdevs.photoncamera.processing.opengl.rawpipeline;

import android.graphics.Point;
import android.util.Log;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.processing.ImageFrame;
import com.particlesdevs.photoncamera.processing.opengl.GLFormat;
import com.particlesdevs.photoncamera.processing.opengl.GLProg;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static android.opengl.GLES20.GL_TEXTURE0;
import static android.opengl.GLES20.GL_TEXTURE2;
import static android.opengl.GLES20.GL_TEXTURE4;
import static android.opengl.GLES20.GL_TEXTURE6;
import static android.opengl.GLES20.GL_TEXTURE8;

//Ported Alignment from NightCamera
public class AlignAndMergeNCam extends Node {
    private final int TileSize = 256;
    Point rawSize;
    GLProg glProg;
    private final List<GLTexture> mTextures = new ArrayList<>();
    private GLTexture mAlign, mWeights;

    public AlignAndMergeNCam(String rid, String name) {
        super(rid, name);
    }

    @Override
    public void Compile() {
    }

    private GLTexture CorrectedRaw(ByteBuffer input) {
        glProg.useAssetProgram("precorrection");
        glProg.setTexture("InputBuffer", new GLTexture(rawSize, new GLFormat(GLFormat.DataType.UNSIGNED_16), input));
        glProg.setVar("WhiteLevel", (float) PhotonCamera.getParameters().realWL);
        GLTexture output = new GLTexture(rawSize, new GLFormat(GLFormat.DataType.FLOAT_16), null);
        glProg.drawBlocks(output);
        glProg.close();
        return output;
    }

    private class TexPyramid {
        private static final int DOWNSAMPLE_SCALE = 4;
        private static final int TILE_SIZE = 8;

        private GLTexture mLargeResRef, mMidResRef, mSmallResRef;
        private GLTexture mLargeRes, mMidRes, mSmallRes;

        private GLTexture mLargeResRefSumHorz, mLargeResRefSumVert;
        private GLTexture mLargeResSumHorz, mLargeResSumVert;

        private GLTexture mLargeResRefSumHorzDiff, mLargeResRefSumVertDiff;
        private GLTexture mLargeResSumHorzDiff, mLargeResSumVertDiff;

        private GLTexture mSmallAlign, mMidAlign, mLargeAlign;
        private GLTexture mLargeWeights;

        private void downSample() {
            GLTexture refTex = mTextures.get(0);
            int width = refTex.mSize.x;
            int height = refTex.mSize.y;

            // Part 1: Downscaling reference frame.
            mLargeResRef = new GLTexture(new Point(width / 2, height / 2), new GLFormat(GLFormat.DataType.FLOAT_16), null);

            mMidResRef = new GLTexture(new Point(mLargeResRef.mSize.x / DOWNSAMPLE_SCALE + 1,
                    mLargeResRef.mSize.y / DOWNSAMPLE_SCALE + 1), new GLFormat(GLFormat.DataType.FLOAT_16), null);

            mSmallResRef = new GLTexture(new Point(mLargeResRef.mSize.x / DOWNSAMPLE_SCALE + 1,
                    mLargeResRef.mSize.y / DOWNSAMPLE_SCALE + 1), new GLFormat(GLFormat.DataType.FLOAT_16), null);

            // Running the downscalers.
            glProg.useAssetProgram("stage0_downscale_boxdown2_fs");
            {
                glProg.setVar("frame", 0);
                refTex.bind(GL_TEXTURE0);
                glProg.drawBlocks(mLargeResRef, TileSize);
            }
            glProg.useAssetProgram("stage0_downscale_gaussdown4_fs");
            {
                glProg.setVar("frame", 0);
                mLargeResRef.bind(GL_TEXTURE0);
                glProg.setVar("bounds", mLargeResRef.mSize.x, mLargeResRef.mSize.y);
                glProg.drawBlocks(mMidResRef, TileSize);
            }
            {
                mMidResRef.bind(GL_TEXTURE0);
                glProg.setVar("bounds", mMidResRef.mSize.x, mMidResRef.mSize.y);
                glProg.drawBlocks(mSmallResRef, TileSize, true);
            }

            // Part 2: Downscaling alternative frames.
            mLargeRes = new GLTexture(new Point(width / 2, height / 2), new GLFormat(GLFormat.DataType.FLOAT_16, 4), null);

            mMidRes = new GLTexture(new Point(mLargeRes.mSize.x / DOWNSAMPLE_SCALE + 1,
                    mLargeRes.mSize.y / DOWNSAMPLE_SCALE + 1), new GLFormat(GLFormat.DataType.FLOAT_16, 4), null);

            mSmallRes = new GLTexture(new Point(mMidRes.mSize.x / DOWNSAMPLE_SCALE + 1,
                    mMidRes.mSize.y / DOWNSAMPLE_SCALE + 1), new GLFormat(GLFormat.DataType.FLOAT_16, 4), null);

            // Running the downscalers.
            glProg.useAssetProgram("stage0_downscale_boxdown2_4frames_fs");
            {
                for (int i = 1; i < mTextures.size(); i++) {
                    mTextures.get(i).bind(GL_TEXTURE0 + 2 * i);
                    glProg.setVar("frame" + i, 2 * i);
                }
                glProg.drawBlocks(mLargeRes, TileSize);
            }
            glProg.useAssetProgram("stage0_downscale_gaussdown4_4frames_fs");
            glProg.setVar("frame", 0);
            {
                mLargeRes.bind(GL_TEXTURE0);
                glProg.setVar("bounds", mLargeRes.mSize.x, mLargeRes.mSize.y);
                glProg.drawBlocks(mMidRes, TileSize);
            }
            {
                mMidRes.bind(GL_TEXTURE0);
                glProg.setVar("bounds", mMidRes.mSize.x, mMidRes.mSize.y);
                glProg.drawBlocks(mSmallRes, TileSize, true);
            }
        }

        public void integrate() {

            // Single-channel ref frame.
            glProg.useAssetProgram("stage1_integrate_fs");
            glProg.setVar("refFrame", 0);

            mLargeResRefSumHorz = new GLTexture(mLargeResRef.mSize, new GLFormat(GLFormat.DataType.FLOAT_16), null);
            mLargeResRefSumVert = new GLTexture(mLargeResRef.mSize, new GLFormat(GLFormat.DataType.FLOAT_16), null);

            mLargeResRef.bind(GL_TEXTURE0);
            glProg.setVar("bounds", mLargeResRef.mSize);
            glProg.setVar("direction", 1, 0);
            glProg.drawBlocks(mLargeResRefSumHorz, TileSize, true);
            glProg.setVar("direction", 0, 1);
            glProg.drawBlocks(mLargeResRefSumVert, TileSize, true);

            // Quad-channel alt frames.
            glProg.useAssetProgram("stage1_integrate_4frames_fs");
            glProg.setVar("altFrame", 0);

            mLargeResSumHorz = new GLTexture(mLargeRes.mSize, new GLFormat(GLFormat.DataType.FLOAT_16, 4), null);
            mLargeResSumVert = new GLTexture(mLargeRes.mSize, new GLFormat(GLFormat.DataType.FLOAT_16, 4), null);

            mLargeRes.bind(GL_TEXTURE0);
            glProg.setVar("bounds", mLargeRes.mSize);
            glProg.setVar("direction", 1, 0);
            glProg.drawBlocks(mLargeResSumHorz, TileSize, true);
            glProg.setVar("direction", 0, 1);
            glProg.drawBlocks(mLargeResSumVert, TileSize, true);

            //DEBUG(this);
        }

        public void differentiate() {
            // Single-channel ref frame.
            glProg.useAssetProgram("stage1_diff_fs");
            glProg.setVar("refFrame", 0);
            glProg.setVar("bounds", mLargeRes.mSize);

            // Shuffle other GLTexture around instead of creating new ones.
            mLargeResRefSumHorzDiff = new GLTexture(mLargeResRefSumHorz.mSize, new GLFormat(GLFormat.DataType.FLOAT_16), null);
            mLargeResRefSumHorz.bind(GL_TEXTURE0);
            glProg.setVar("direction", 0, 1); // Diff vertically.
            glProg.drawBlocks(mLargeResRefSumHorzDiff, TileSize, true);

            mLargeResRefSumVertDiff = mLargeResRefSumHorz;
            mLargeResRefSumVert.bind(GL_TEXTURE0);
            glProg.setVar("direction", 1, 0); // Diff horizontally.
            glProg.drawBlocks(mLargeResRefSumVertDiff, TileSize, true);

            // Release resources.
            mLargeResRefSumVert.close();

            glProg.useAssetProgram("stage1_diff_4frames_fs");
            glProg.setVar("altFrame", 0);
            glProg.setVar("bounds", mLargeRes.mSize);

            // Shuffle other GLTexture around instead of creating new ones.
            mLargeResSumHorzDiff = new GLTexture(mLargeResRefSumHorz.mSize, new GLFormat(GLFormat.DataType.FLOAT_16, 4), null);
            mLargeResSumHorz.bind(GL_TEXTURE0);
            glProg.setVar("direction", 0, 1); // Diff vertically.
            glProg.drawBlocks(mLargeResSumHorzDiff, TileSize, true);

            mLargeResSumVertDiff = mLargeResSumHorz;
            mLargeResSumVert.bind(GL_TEXTURE0);
            glProg.setVar("direction", 1, 0); // Diff horizontally.
            glProg.drawBlocks(mLargeResSumVertDiff, TileSize, true);

            // Release resources
            mLargeResSumVert.close();
        }

        /**
         * best positions = null
         * cycle:
         * select new shift
         * foreach block:
         * compute summed diff with new shift
         * if new shift is better
         * update position of block
         * end foreach
         * end cycle
         */
        private void align() {
            glProg.useAssetProgram("stage1_alignlayer_fs");

            mSmallAlign = new GLTexture(mSmallRes.mSize.x / TILE_SIZE + 1,
                    mSmallRes.mSize.y / TILE_SIZE + 1, new GLFormat(GLFormat.DataType.UNSIGNED_16, 4), null);

            glProg.setVar("refFrame", 0);
            glProg.setVar("altFrame", 2);
            glProg.setVar("prevLayerAlign", 4);
            glProg.setVar("prevLayerScale", 0);

            mSmallResRef.bind(GL_TEXTURE0);
            mSmallRes.bind(GL_TEXTURE2);
            // No PrevAlign on GL_TEXTURE2
            glProg.drawBlocks(mSmallAlign, TileSize);

            // Close resources.
            mSmallResRef.close();
            mSmallRes.close();

            mMidAlign = new GLTexture(mMidRes.mSize.x / TILE_SIZE + 1,
                    mMidRes.mSize.y / TILE_SIZE + 1, new GLFormat(GLFormat.DataType.UNSIGNED_16, 4), null);

            // Enable previous layers from here.
            glProg.setVar("prevLayerScale", 4);

            mMidResRef.bind(GL_TEXTURE0);
            mMidRes.bind(GL_TEXTURE2);
            mSmallAlign.bind(GL_TEXTURE4);
            glProg.drawBlocks(mMidAlign, TileSize / DOWNSAMPLE_SCALE, true);
            // We reduce the block height because of stuttering.

            // Close resources.
            mMidResRef.close();
            mMidRes.close();
            mSmallAlign.close();

            glProg.useAssetProgram("stage1_alignlayer_approximate_fs");

            glProg.setVar("refFrameHorz", 0);
            glProg.setVar("refFrameVert", 2);
            glProg.setVar("altFrameHorz", 4);
            glProg.setVar("altFrameVert", 6);
            glProg.setVar("prevLayerAlign", 8);
            glProg.setVar("prevLayerScale", 4);

            mLargeAlign = new GLTexture(mLargeRes.mSize.x / TILE_SIZE + 1,
                    mLargeRes.mSize.y / TILE_SIZE + 1, new GLFormat(GLFormat.DataType.UNSIGNED_16, 4), null);

            mLargeResRefSumHorzDiff.bind(GL_TEXTURE0);
            mLargeResRefSumVertDiff.bind(GL_TEXTURE2);
            mLargeResSumHorzDiff.bind(GL_TEXTURE4);
            mLargeResSumVertDiff.bind(GL_TEXTURE6);
            mMidAlign.bind(GL_TEXTURE8);
            glProg.drawBlocks(mLargeAlign, TileSize / DOWNSAMPLE_SCALE, true);
            // We reduce the block height because of stuttering.

            // Close resources.
            mLargeResRefSumHorz.close();
            mLargeResRefSumVert.close();
            mLargeResSumHorz.close();
            mLargeResSumVert.close();
            mMidAlign.close();
        }

        private void weigh() {
            glProg.useAssetProgram("stage2_weightiles_fs");
            mLargeWeights = new GLTexture(mLargeAlign.mSize, new GLFormat(GLFormat.DataType.FLOAT_16, 4), null);
            glProg.setVar("refFrame", 0);
            glProg.setVar("altFrame", 2);
            glProg.setVar("alignment", 4);
            mLargeResRef.bind(GL_TEXTURE0);
            mLargeRes.bind(GL_TEXTURE2);
            mLargeAlign.bind(GL_TEXTURE4);
            // We reduce the block height because of stuttering.
            glProg.drawBlocks(mLargeWeights, TileSize / DOWNSAMPLE_SCALE, true);

            // Close resources.
            mLargeResRef.close();
            mLargeRes.close();
        }
    }

    private void Merge() {
        // Assume same size.
        List<GLTexture> images = mTextures;
        glProg.setVar("alignCount", 5);
        glProg.setVar("frameSize", images.get(0).mSize);
        glProg.setVar("refFrame", 0);
        images.get(0).bind(GL_TEXTURE0);
        for (int i = 1; i < images.size(); i++) {
            glProg.setVar("altFrame" + i, 2 * i);
            images.get(i).bind(GL_TEXTURE0 + 2 * i);
        }
        glProg.setVar("alignment", 2 * images.size());
        mAlign.bind(GL_TEXTURE0 + 2 * images.size());
        glProg.setVar("alignmentWeight", 2 * (images.size() + 1));
        mWeights.bind(GL_TEXTURE0 + 2 * (images.size() + 1));
        WorkingTexture = new GLTexture(mTextures.get(0).mSize, new GLFormat(GLFormat.DataType.UNSIGNED_16), null);
        WorkingTexture.BufferLoad();
        glProg.close();
    }

    @Override
    public void Run() {
        glProg = basePipeline.glint.glProgram;
        RawPipeline rawPipeline = (RawPipeline) basePipeline;
        rawSize = rawPipeline.glint.parameters.rawSize;
        ArrayList<ImageFrame> images = rawPipeline.images;
        //GLTexture BaseFrame22 = BoxDown22(BaseFrame);
        //GLTexture BaseFrame88 = GaussDown44(BaseFrame22);
        //GLTexture BaseFrame3232 = GaussDown44(BaseFrame88);
        //GLTexture Output = CorrectedRaw(images.get(0));
        long time = System.currentTimeMillis();
        //GLTexture BaseFrame = CorrectedRaw(images.get(0));
        // Remove all previous textures.
        for (GLTexture texture : mTextures) {
            texture.close();
        }
        mTextures.clear();
        for (int i = 0; i < 5; i++) {
            //GLTexture inputraw = CorrectedRaw(images.get(i%images.size()));
            GLTexture input = new GLTexture(rawSize, new GLFormat(GLFormat.DataType.UNSIGNED_16), images.get((i % images.size())).buffer);
            mTextures.add(input);
        }
        if (mTextures.size() == 5) {
            TexPyramid pyramid = new TexPyramid();
            pyramid.downSample();
            pyramid.integrate();
            pyramid.differentiate();
            pyramid.align();
            pyramid.weigh();
            mAlign = pyramid.mLargeAlign;
            mWeights = pyramid.mLargeWeights;
            Merge();
        }
        Log.d("AlignAndMerge", "AlignmentAndMerge elapsed time:" + (System.currentTimeMillis() - time) + " ms");
    }
}