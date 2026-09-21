package com.particlesdevs.photoncamera.processing

import java.nio.ByteBuffer

class AiUpscalerPipeline {

    external fun nativeInit(modelPath: String, profile: Int): Int

    /**
     * Processes the image using RealSR with tiling.
     * @param inBuffers Array of input RAW10 buffers (15 frames).
     * @param outBuffer Output high-res RGB buffer.
     * @param width Input width.
     * @param height Input height.
     * @param targetWidth Upscaled width.
     * @param targetHeight Upscaled height.
     * @param tileSize Tile size for Mali GPU (256).
     * @param overlap Tile overlap (16).
     */
    external fun nativeProcess(
        inBuffers: Array<ByteBuffer>,
        outBuffer: ByteBuffer,
        width: Int, 
        height: Int,
        targetWidth: Int, 
        targetHeight: Int,
        tileSize: Int, 
        overlap: Int
    )

    companion object {
        init {
            System.loadLibrary("ai_upscaler")
        }
    }
}
