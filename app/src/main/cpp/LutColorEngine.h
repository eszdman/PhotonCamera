#ifndef KOSHCAM_LUT_COLOR_ENGINE_H
#define KOSHCAM_LUT_COLOR_ENGINE_H

#include <vector>
#include <cstdint>
#include <string>

namespace koshcam {

enum class LutPreset {
    LEICA = 0, // Filmic contrast, deep shadow tones
    ZEISS = 1, // Cool cinematic skin tones, crisp cyan/blue
    VIVO  = 2  // Vibrant HDR, rich saturation
};

class LutColorEngine {
public:
    static constexpr int LUT_SIZE = 33; // Standard 33x33x33 3D LUT

    LutColorEngine();
    ~LutColorEngine();

    /**
     * Initializes default procedural 3D LUT tables for presets or loads .cube file.
     */
    void initPreset(LutPreset preset);
    bool loadCubeFile(const std::string& cubeFilePath);

    /**
     * Applies 3D LUT color transformation to float RGB image buffer in-place.
     */
    void processRgb(float* rgbBuffer, int width, int height);

private:
    std::vector<float> lutTable; // Size = 33 * 33 * 33 * 3
    int size;

    void generateLeicaLut();
    void generateZeissLut();
    void generateVivoLut();

    void trilinearSample(float r, float g, float b, float& outR, float& outG, float& outB) const;
};

} // namespace koshcam

#endif // KOSHCAM_LUT_COLOR_ENGINE_H
