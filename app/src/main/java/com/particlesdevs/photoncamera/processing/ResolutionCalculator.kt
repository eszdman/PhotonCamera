package com.particlesdevs.photoncamera.processing

import android.util.Size
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Handles the calculation of output resolutions for the AI Upscaler.
 * Presets: 64MP, 100MP, 108MP, 200MP.
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
     * Hard mandate for 200MP presets on Samsung A22 5G.
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
        
        val targetArea = preset.megaPixels * 1_000_000.0
        val ratio = aspect.getRatio()
        val height = sqrt(targetArea / ratio).roundToInt()
        val width = (height * ratio).roundToInt()

        return Size(width, height)
    }

    /**
     * Calculates the tile size and overlap for RealSR processing.
     * Samsung A22 5G (Mali-G57 MC2) optimization:
     * Using smaller 256x256 tiles with 16px overlap to balance between
     * OOM avoidance and tiling artifacts.
     */
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
