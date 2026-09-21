package com.particlesdevs.photoncamera.processing

import java.nio.ByteBuffer

class JpegEncoder {

    /**
     * Encodes a DirectByteBuffer to a JPEG file using TurboJPEG.
     * @param buffer Input RGB DirectByteBuffer.
     * @param width Image width.
     * @param height Image height.
     * @param quality JPEG quality (0-100).
     * @param filePath Destination file path.
     * @return 0 on success, -1 on failure.
     */
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
