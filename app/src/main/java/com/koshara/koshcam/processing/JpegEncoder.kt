package com.koshara.koshcam.processing

import java.nio.ByteBuffer

class JpegEncoder {

    external fun nativeEncode(
        buffer: ByteBuffer,
        width: Int,
        height: Int,
        quality: Int,
        filePath: String
    ): Int

    companion object {
        init {
            System.loadLibrary("ai_upscaler")
        }
    }
}
