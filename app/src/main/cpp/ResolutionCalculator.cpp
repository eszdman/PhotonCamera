#include "ResolutionCalculator.h"
#include <cmath>
#include <android/log.h>

#define LOG_TAG "KoshcamResEngine"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

namespace koshcam {

TargetSize DynamicResolutionEngine::calculateResolution(TargetPreset preset, AspectRatio aspect) {
    if (preset == TargetPreset::MP_200) {
        switch (aspect) {
            case AspectRatio::ASPECT_4_5:  return {12649, 15811};
            case AspectRatio::ASPECT_16_9: return {18856, 10607};
            case AspectRatio::ASPECT_4_3:  return {16330, 12247};
            case AspectRatio::ASPECT_1_1:  return {14142, 14142};
        }
    }

    double ratioW = 4.0;
    double ratioH = 3.0;

    switch (aspect) {
        case AspectRatio::ASPECT_4_5:  ratioW = 4.0;  ratioH = 5.0;  break;
        case AspectRatio::ASPECT_16_9: ratioW = 16.0; ratioH = 9.0;  break;
        case AspectRatio::ASPECT_4_3:  ratioW = 4.0;  ratioH = 3.0;  break;
        case AspectRatio::ASPECT_1_1:  ratioW = 1.0;  ratioH = 1.0;  break;
    }

    double targetArea = static_cast<double>(static_cast<int>(preset)) * 1000000.0;
    double aspectVal = ratioW / ratioH;

    int height = static_cast<int>(std::round(std::sqrt(targetArea / aspectVal)));
    int width  = static_cast<int>(std::round(height * aspectVal));

    LOGD("Calculated target resolution for %dMP (aspect %d): %d x %d",
         static_cast<int>(preset), static_cast<int>(aspect), width, height);

    return {width, height};
}

} // namespace koshcam
