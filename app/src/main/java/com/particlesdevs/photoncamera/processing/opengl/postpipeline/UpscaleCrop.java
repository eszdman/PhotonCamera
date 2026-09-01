package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import android.graphics.Point;

import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;

/**
 * Expands a cropped capture to the requested full-frame output size using the
 * pipeline's existing GPU interpolation path.
 *
 * <p>This node deliberately performs no ML inference, CPU texture readback,
 * luma/chroma conversion, or temporary full-frame float allocation.</p>
 */
public final class UpscaleCrop extends Node {
    public UpscaleCrop() {
        super("", "UpscaleCrop");
    }

    @Override
    public void Compile() {
        // glUtils.interpolate() owns the required GL interpolation setup.
    }

    @Override
    public void Run() {
        GLTexture input = previousNode.WorkingTexture;

        if (input == null) {
            WorkingTexture = null;
            return;
        }

        if (basePipeline.mParameters.fullRawSize == null ||
                !basePipeline.mParameters.isCropped) {
            WorkingTexture = input;
            return;
        }

        Point fullSize = basePipeline.mParameters.fullRawSize;

        if (fullSize.x <= 0 ||
                fullSize.y <= 0 ||
                input.mSize.x <= 0 ||
                input.mSize.y <= 0) {
            WorkingTexture = input;
            return;
        }

        /*
         * Keep output dimensions divisible by four, matching the crop/output
         * sizing convention already used by PostPipeline.
         */
        Point target = new Point(
                fullSize.x & ~3,
                fullSize.y & ~3);

        if (target.x < 4) {
            target.x = 4;
        }

        if (target.y < 4) {
            target.y = 4;
        }

        if (target.equals(input.mSize)) {
            WorkingTexture = input;
            return;
        }

        WorkingTexture = glUtils.interpolate(input, target);

        /*
         * Downstream nodes and the final GL output need to use the new texture
         * dimensions rather than the original crop dimensions.
         */
        basePipeline.workSize = new Point(target);
        ((PostPipeline) basePipeline).cropSize = new Point(target);
    }
}
