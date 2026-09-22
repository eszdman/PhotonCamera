#ifndef KOSHCAM_RESOLUTION_CALCULATOR_H
#define KOSHCAM_RESOLUTION_CALCULATOR_H

namespace koshcam {

enum class TargetPreset {
    MP_64  = 64,
    MP_100 = 100,
    MP_108 = 108,
    MP_200 = 200
};

enum class AspectRatio {
    ASPECT_4_5,
    ASPECT_16_9,
    ASPECT_4_3,
    ASPECT_1_1
};

struct TargetSize {
    int width;
    int height;
};

class DynamicResolutionEngine {
public:
    /**
     * Mathematically calculates target Width x Height from base 48MP sensor given target megapixel preset and aspect ratio.
     * Hard-mandated calculations for 200MP:
     * - Aspect 4:5  -> 12649 x 15811
     * - Aspect 16:9 -> 18856 x 10607
     * - Aspect 4:3  -> 16330 x 12247
     * - Aspect 1:1  -> 14142 x 14142
     */
    static TargetSize calculateResolution(TargetPreset preset, AspectRatio aspect);
};

} // namespace koshcam

#endif // KOSHCAM_RESOLUTION_CALCULATOR_H
