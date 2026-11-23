//
// Created by eszdman on 22.11.2026.
//

#pragma once

namespace photon::post {
enum class Format { r32f, rgba16f, rgba32f, rgba8, words, rg16f };
template <Format F> struct Plane;
using Rgba = Plane<Format::rgba16f>;
} // namespace photon::post
