#include "LutColorEngine.h"
#include <android/log.h>
#include <fstream>
#include <sstream>
#include <cmath>
#include <algorithm>

#define LOG_TAG "KoshcamLut"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace koshcam {

LutColorEngine::LutColorEngine() : size(LUT_SIZE) {
    lutTable.resize(size * size * size * 3, 0.0f);
    initPreset(LutPreset::LEICA);
}

LutColorEngine::~LutColorEngine() {}

void LutColorEngine::initPreset(LutPreset preset) {
    switch (preset) {
        case LutPreset::LEICA:
            generateLeicaLut();
            break;
        case LutPreset::ZEISS:
            generateZeissLut();
            break;
        case LutPreset::VIVO:
            generateVivoLut();
            break;
    }
}

void LutColorEngine::generateLeicaLut() {
    LOGD("Generating Leica 3D LUT (filmic contrast, deep shadows)");
    for (int r = 0; r < size; ++r) {
        for (int g = 0; g < size; ++g) {
            for (int b = 0; b < size; ++b) {
                float fr = (float)r / (size - 1);
                float fg = (float)g / (size - 1);
                float fb = (float)b / (size - 1);

                // Leica S-curve + warm deep shadows
                float outR = std::pow(fr, 1.15f) * 1.05f;
                float outG = std::pow(fg, 1.10f) * 1.00f;
                float outB = std::pow(fb, 1.20f) * 0.95f;

                int idx = ((r * size + g) * size + b) * 3;
                lutTable[idx + 0] = std::clamp(outR, 0.0f, 1.0f);
                lutTable[idx + 1] = std::clamp(outG, 0.0f, 1.0f);
                lutTable[idx + 2] = std::clamp(outB, 0.0f, 1.0f);
            }
        }
    }
}

void LutColorEngine::generateZeissLut() {
    LOGD("Generating Zeiss 3D LUT (cool skin tones, crisp blues)");
    for (int r = 0; r < size; ++r) {
        for (int g = 0; g < size; ++g) {
            for (int b = 0; b < size; ++b) {
                float fr = (float)r / (size - 1);
                float fg = (float)g / (size - 1);
                float fb = (float)b / (size - 1);

                // Cool shadow lift & crisp highlight separation
                float outR = std::pow(fr, 1.05f) * 0.96f;
                float outG = std::pow(fg, 1.02f) * 1.02f;
                float outB = std::pow(fb, 0.95f) * 1.08f;

                int idx = ((r * size + g) * size + b) * 3;
                lutTable[idx + 0] = std::clamp(outR, 0.0f, 1.0f);
                lutTable[idx + 1] = std::clamp(outG, 0.0f, 1.0f);
                lutTable[idx + 2] = std::clamp(outB, 0.0f, 1.0f);
            }
        }
    }
}

void LutColorEngine::generateVivoLut() {
    LOGD("Generating Vivo 3D LUT (vibrant HDR, rich saturation)");
    for (int r = 0; r < size; ++r) {
        for (int g = 0; g < size; ++g) {
            for (int b = 0; b < size; ++b) {
                float fr = (float)r / (size - 1);
                float fg = (float)g / (size - 1);
                float fb = (float)b / (size - 1);

                // High saturation & vibrant midtone expansion
                float lum = 0.299f * fr + 0.587f * fg + 0.114f * fb;
                float outR = lum + 1.25f * (fr - lum);
                float outG = lum + 1.25f * (fg - lum);
                float outB = lum + 1.25f * (fb - lum);

                int idx = ((r * size + g) * size + b) * 3;
                lutTable[idx + 0] = std::clamp(outR, 0.0f, 1.0f);
                lutTable[idx + 1] = std::clamp(outG, 0.0f, 1.0f);
                lutTable[idx + 2] = std::clamp(outB, 0.0f, 1.0f);
            }
        }
    }
}

bool LutColorEngine::loadCubeFile(const std::string& cubeFilePath) {
    std::ifstream file(cubeFilePath);
    if (!file.is_open()) {
        LOGE("Failed to open .cube file: %s", cubeFilePath.c_str());
        return false;
    }

    std::string line;
    int loadedSize = 0;
    std::vector<float> tempLut;

    while (std::getline(file, line)) {
        if (line.empty() || line[0] == '#') continue;

        if (line.find("LUT_3D_SIZE") != std::string::npos) {
            std::stringstream ss(line);
            std::string token;
            ss >> token >> loadedSize;
            tempLut.reserve(loadedSize * loadedSize * loadedSize * 3);
        } else if (loadedSize > 0) {
            float r, g, b;
            std::stringstream ss(line);
            if (ss >> r >> g >> b) {
                tempLut.push_back(r);
                tempLut.push_back(g);
                tempLut.push_back(b);
            }
        }
    }

    if (loadedSize > 0 && tempLut.size() == static_cast<size_t>(loadedSize * loadedSize * loadedSize * 3)) {
        size = loadedSize;
        lutTable = std::move(tempLut);
        LOGD("Successfully loaded %dx%dx%d .cube LUT", size, size, size);
        return true;
    }

    LOGE("Corrupted or invalid .cube file format");
    return false;
}

void LutColorEngine::trilinearSample(float r, float g, float b, float& outR, float& outG, float& outB) const {
    float rf = std::clamp(r, 0.0f, 1.0f) * (size - 1);
    float gf = std::clamp(g, 0.0f, 1.0f) * (size - 1);
    float bf = std::clamp(b, 0.0f, 1.0f) * (size - 1);

    int r0 = (int)rf, r1 = std::min(r0 + 1, size - 1);
    int g0 = (int)gf, g1 = std::min(g0 + 1, size - 1);
    int b0 = (int)bf, b1 = std::min(b0 + 1, size - 1);

    float dr = rf - r0;
    float dg = gf - g0;
    float db = bf - b0;

    auto getLut = [this](int ir, int ig, int ib, int c) {
        return lutTable[((ir * size + ig) * size + ib) * 3 + c];
    };

    for (int c = 0; c < 3; ++c) {
        float c000 = getLut(r0, g0, b0, c);
        float c001 = getLut(r0, g0, b1, c);
        float c010 = getLut(r0, g1, b0, c);
        float c011 = getLut(r0, g1, b1, c);
        float c100 = getLut(r1, g0, b0, c);
        float c101 = getLut(r1, g0, b1, c);
        float c110 = getLut(r1, g1, b0, c);
        float c111 = getLut(r1, g1, b1, c);

        float c00 = c000 * (1 - dr) + c100 * dr;
        float c01 = c001 * (1 - dr) + c101 * dr;
        float c10 = c010 * (1 - dr) + c110 * dr;
        float c11 = c011 * (1 - dr) + c111 * dr;

        float c0 = c00 * (1 - dg) + c10 * dg;
        float c1 = c01 * (1 - dg) + c11 * dg;

        float val = c0 * (1 - db) + c1 * db;
        if (c == 0) outR = val;
        else if (c == 1) outG = val;
        else outB = val;
    }
}

void LutColorEngine::processRgb(float* rgbBuffer, int width, int height) {
    int totalPixels = width * height;
    #pragma omp parallel for
    for (int i = 0; i < totalPixels; ++i) {
        float r = rgbBuffer[i * 3 + 0] / 255.0f;
        float g = rgbBuffer[i * 3 + 1] / 255.0f;
        float b = rgbBuffer[i * 3 + 2] / 255.0f;

        float outR, outG, outB;
        trilinearSample(r, g, b, outR, outG, outB);

        rgbBuffer[i * 3 + 0] = outR * 255.0f;
        rgbBuffer[i * 3 + 1] = outG * 255.0f;
        rgbBuffer[i * 3 + 2] = outB * 255.0f;
    }
}

} // namespace koshcam
