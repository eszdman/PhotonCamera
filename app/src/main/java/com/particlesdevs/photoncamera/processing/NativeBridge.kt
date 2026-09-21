package com.particlesdevs.photoncamera.processing

import java.nio.ByteBuffer

object NativeBridge {
    /**
     * Executes the full high-performance pipeline for Samsung A22 5G.
     * 1. Bayer Fusion (15 frames)
     * 2. AI Upscaling (Vulkan)
     * 3. TurboJPEG encoding
     *
     * @param burstBuffer DirectByteBuffer containing concatenated 15 RAW10 frames.
     * @param width Source image width.
     * @param height Source image height.
     * @param mode AI mode (0: FAST, 1: DETAIL, 2: ULTRA).
     * @param targetWidth Final upscaled width.
     * @param targetHeight Final upscaled height.
     * @param outPath Output file path (if FD is 0).
     * @param outFd Output FileDescriptor (preferred for SAF).
     * @return 0 on success, negative error code otherwise.
     */
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

    init {
        System.loadLibrary("ai_upscaler")
    }
}
