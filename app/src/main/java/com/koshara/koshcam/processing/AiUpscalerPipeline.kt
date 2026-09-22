package com.koshara.koshcam.processing

import java.nio.ByteBuffer

class AiUpscalerPipeline {

    external fun nativeInit(modelPath: String, mode: Int): Int

    external fun nativeProcess(
        inBuffers: Array<ByteBuffer>,
        outBuffer: ByteBuffer,
        width: Int, 
        height: Int,
        targetWidth: Int, 
        targetHeight: Int,
        tileSize: Int, 
        overlap: Int
    ): Int

    companion object {
        init {
            System.loadLibrary("ai_upscaler")
        }
    }
}
