package com.particlesdevs.photoncamera.processing

import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class TargetResolution(val megapixels: Double) {
    MP_64(64.0),
    MP_100(100.0),
    MP_108(108.0),
    MP_200(200.0)
}

enum class AspectRatio(val wRatio: Double, val hRatio: Double) {
    RATIO_4_5(4.0, 5.0),
    RATIO_16_9(16.0, 9.0),
    RATIO_4_3(4.0, 3.0),
    RATIO_1_1(1.0, 1.0)
}

data class OutputSize(val width: Int, val height: Int)

object ResolutionCalculator {
    fun calculate(aspectRatio: AspectRatio, target: TargetResolution): OutputSize {
        // Хардкод точных значений для 200MP согласно архитектурным требованиям
        if (target == TargetResolution.MP_200) {
            return when (aspectRatio) {
                AspectRatio.RATIO_4_5 -> OutputSize(12649, 15811)
                AspectRatio.RATIO_16_9 -> OutputSize(18856, 10607)
                AspectRatio.RATIO_4_3 -> OutputSize(16330, 12247)
                AspectRatio.RATIO_1_1 -> OutputSize(14142, 14142)
            }
        }

        val totalPixels = target.megapixels * 1_000_000.0
        val ratio = aspectRatio.wRatio / aspectRatio.hRatio

        val height = sqrt(totalPixels / ratio)
        val width = height * ratio

        return OutputSize(width.roundToInt(), height.roundToInt())
    }
}