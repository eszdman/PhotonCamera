//
// Created by eszdman on 22.11.2026.
//

#pragma once
#include "PostPass.hpp"
#include <future>

namespace photon::post {
using Scalar = Plane<Format::r32f>;
using Sdr = Plane<Format::rgba8>;

struct PostState {
    kp::Manager& gpu;
    kp::Sequence& sequence;
    Size image;
    const LinearSettings& settings;
    Scalar cfa;
    Rgba rgb;
    Rgba destination;
    Rgba alternate{};
    FramePasses passes{gpu, sequence};

    std::future<RgbaImage> singleKernelMap;
    Size captureSize = image;
    Scalar sceneLuma{}, exposureCurve{};
    Rgba gainMap{};
    float sharpenScale = 1.f;
    float adaptiveWhitePoint = 1, displayGain = 1, rawClipLevel = 1;

    void upload(const Resources& buffers) const;
    void download(const Resources& buffers);

    Rgba nextRgb() {
        if (rgb.tensor != destination.tensor) return destination;
        if (!alternate.tensor) alternate = rgba(image);
        return alternate;
    }

    Scalar scalar(Size size) const {
        return {gpu.tensorT<float>(std::size_t(size.x) * size.y, kp::Memory::MemoryTypes::eStorage), size};
    }
    Plane<Format::rg16f> rg(Size size) const {
        return {gpu.tensorT<uint32_t>(std::size_t(size.x) * size.y, kp::Memory::MemoryTypes::eStorage), size};
    }
    Rgba rgba(Size size) const {
        return {gpu.tensorT<uint32_t>(std::size_t(size.x) * size.y * 2, kp::Memory::MemoryTypes::eStorage), size};
    }
};
} // namespace photon::post
