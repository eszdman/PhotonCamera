#ifndef KOSHCAM_WATERMARK_ENGINE_H
#define KOSHCAM_WATERMARK_ENGINE_H

#include <vector>
#include <cstdint>
#include <string>

namespace koshcam {

struct ExifInfo {
    std::string focalLength = "4.74mm f/1.8";
    std::string shutterSpeed = "1/50s";
    std::string iso = "ISO 100";
    std::string author = "Shot by koshara";
    std::string brand = "Koshcam";
};

class WatermarkEngine {
public:
    WatermarkEngine();
    ~WatermarkEngine();

    /**
     * Appends a stylish black banner at the bottom of the RGB frame with EXIF data and "Shot by koshara".
     * Returns new combined vector with bottom banner attached.
     */
    static std::vector<uint8_t> attachBanner(
        const uint8_t* inRgb,
        int width,
        int height,
        int& outHeight,
        const ExifInfo& exif
    );
};

} // namespace koshcam

#endif // KOSHCAM_WATERMARK_ENGINE_H
