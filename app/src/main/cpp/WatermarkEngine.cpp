#include "WatermarkEngine.h"
#include <cstring>
#include <algorithm>
#include <android/log.h>

#define LOG_TAG "KoshcamWatermark"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

namespace koshcam {

WatermarkEngine::WatermarkEngine() {}
WatermarkEngine::~WatermarkEngine() {}

std::vector<uint8_t> WatermarkEngine::attachBanner(
    const uint8_t* inRgb,
    int width,
    int height,
    int& outHeight,
    const ExifInfo& exif
) {
    int bannerHeight = static_cast<int>(height * 0.08f); // 8% banner
    if (bannerHeight < 60) bannerHeight = 60;

    outHeight = height + bannerHeight;
    size_t imageBytes = static_cast<size_t>(width) * height * 3;
    size_t bannerBytes = static_cast<size_t>(width) * bannerHeight * 3;
    size_t totalBytes = imageBytes + bannerBytes;

    std::vector<uint8_t> output(totalBytes, 0); // Initialize with black banner (0,0,0)

    // 1. Copy main image
    std::memcpy(output.data(), inRgb, imageBytes);

    LOGD("Attached black watermark banner: %dx%d (image) -> %dx%d (total), EXIF: %s | %s | %s | %s | %s",
         width, height, width, outHeight,
         exif.brand.c_str(), exif.focalLength.c_str(), exif.shutterSpeed.c_str(), exif.iso.c_str(), exif.author.c_str());

    return output;
}

} // namespace koshcam
