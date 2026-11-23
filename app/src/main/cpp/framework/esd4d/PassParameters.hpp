//
// Created by eszdman on 19.11.2026.
//

#pragma once

namespace photon::esd4d {
struct alignas(16) WarpParameters {
    int rawHalf[2], shift[2], grid[2], tile;
    float exposure, strength, noiseS, noiseO, padding{};
    float matrix[3][4]{};
};

struct Layer {
    int width, height, inputs, outputs, step, radius, weights, biases;
};

struct Prepare {
    int size[2], sourceSize[2], gainSize[2], stage, precision;
    float black[4], exposure, sharpness;
};

struct Match {
    int size[2], rawHalf[2], previousSize[2], first, precision;
    float noiseS, noiseO, exposure, integralNorm;
};

struct Detector {
    int size[2], shift[2];
    float exposure, noiseS, noiseO, threshold;
    unsigned capacity;
};

struct Measurement {
    int extent[2], offset[2];
    float weight, scale;
    int first, stride;
};
} // namespace photon::esd4d
