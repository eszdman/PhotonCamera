package com.koshara.koshcam.processing

import java.nio.ByteBuffer

object NativeBridge {
    @JvmStatic
    external fun processFullPipeline(
        burstBuffer: ByteBuffer,
        width: Int,
        height: Int,
        mode: Int,
        targetWidth: Int,
        targetHeight: Int,
        outPath: String,
        outFd: Int
    ): Int

    @JvmStatic
    external fun pushZslFrame(
        buffer: ByteBuffer,
        size: Int,
        width: Int,
        height: Int,
        timestampNs: Long
    )

    @JvmStatic
    external fun calculateTargetResolution(
        presetMp: Int,
        aspectOrdinal: Int
    ): IntArray

    init {
        System.loadLibrary("ai_upscaler")
    }
}
