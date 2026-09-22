package com.koshara.koshcam.processing

import android.util.Size
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Handles target resolution calculations for Koshcam presets: 64MP, 100MP, 108MP, 200MP.
 * Supports aspect ratios: 4:5, 16:9, 4:3, 1:1.
 */
object ResolutionCalculator {

    enum class UpscalePreset(val megaPixels: Int) {
        UP_64(64),
        UP_100(100),
        UP_108(108),
        UP_200(200)
    }

    enum class AspectRatio(val w: Int, val h: Int) {
        ASPECT_4_5(4, 5),
        ASPECT_16_9(16, 9),
        ASPECT_4_3(4, 3),
        ASPECT_1_1(1, 1);

        fun getRatio(): Double = w.toDouble() / h.toDouble()
    }

    /**
     * Calculates the target size based on preset and aspect ratio.
     * Hard mandate for 200MP presets:
     * - Aspect 4:5  -> 12649 x 15811
     * - Aspect 16:9 -> 18856 x 10607
     * - Aspect 4:3  -> 16330 x 12247
     * - Aspect 1:1  -> 14142 x 14142
     */
    fun getTargetSize(preset: UpscalePreset, aspect: AspectRatio): Size {
        if (preset == UpscalePreset.UP_200) {
            return when (aspect) {
                AspectRatio.ASPECT_4_5 -> Size(12649, 15811)
                AspectRatio.ASPECT_16_9 -> Size(18856, 10607)
                AspectRatio.ASPECT_4_3 -> Size(16330, 12247)
                AspectRatio.ASPECT_1_1 -> Size(14142, 14142)
            }
        }

        val nativeRes = NativeBridge.calculateTargetResolution(preset.megaPixels, aspect.ordinal)
        if (nativeRes.size == 2 && nativeRes[0] > 0 && nativeRes[1] > 0) {
            return Size(nativeRes[0], nativeRes[1])
        }

        val targetArea = preset.megaPixels * 1_000_000.0
        val ratio = aspect.getRatio()
        val height = sqrt(targetArea / ratio).roundToInt()
        val width = (height * ratio).roundToInt()

        return Size(width, height)
    }

    fun getTilingConfig(targetSize: Size): TilingInfo {
        val tileSize = 256
        val overlap = 16
        val tilesX = (targetSize.width + tileSize - overlap - 1) / (tileSize - overlap)
        val tilesY = (targetSize.height + tileSize - overlap - 1) / (tileSize - overlap)
        return TilingInfo(tileSize, overlap, tilesX, tilesY)
    }

    data class TilingInfo(
        val tileSize: Int,
        val overlap: Int,
        val tilesX: Int,
        val tilesY: Int
    )
}
