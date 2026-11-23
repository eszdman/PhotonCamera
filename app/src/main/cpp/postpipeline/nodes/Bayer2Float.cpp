//
// Created by eszdman on 21.11.2026.
//

#include "PostNodes.hpp"
#include "mask_bayer.hpp"
#include "mask_quad.hpp"
#include "mask_rgb.hpp"
#include "accum_rgb.hpp"
#include "tofloat_rgb0.hpp"
#include "tofloat_rgb1.hpp"
#include "dilate.hpp"
#include "accum_bayer.hpp"
#include "accum_quad.hpp"
#include "tofloat_bayer0.hpp"
#include "tofloat_bayer1.hpp"
#include "tofloat_quad0.hpp"
#include "tofloat_quad1.hpp"
#include <optional>

namespace photon::post {
using namespace shaders;
namespace {
using Color = std::array<float, 3>;
using Words = Plane<Format::words>;

template <class Mask, class Accum, Format F>
std::optional<Color> opposedHighlights(PostState& s, const BayerSettings& raw, Plane<F> input) {
    Size grid{s.image.x / 3, s.image.y / 3};
    if (!raw.hlInpaintOpposed || raw.cfaPattern == 4 || grid.x < 8 || grid.y < 8) return {};
    float clip = .987f * raw.hlClip;
    auto maskPlane = [&] {
        return Words{s.gpu.tensorT<uint32_t>(std::size_t(grid.x) * grid.y, kp::Memory::MemoryTypes::eStorage),
                     grid};
    };
    auto mask = maskPlane();
    auto count = s.gpu.tensorT(std::vector<uint32_t>{0});
    s.upload({count});
    typename Mask::Parameters m;
    m.u_size = s.image;
    m.u_msize = grid;
    m.CfaPattern = raw.cfaPattern;
    auto wp = s.settings.whitePoint;
    if constexpr (F == Format::rgba16f) wp.fill(1.f);
    for (int c = 0; c < 3; ++c) {
        if (wp[c] <= 0) wp[c] = 1;
        m.u_clipthr[c] = clip * wp[c];
    }
    s.passes.compute(grid, m, Mask::u_raw(input), Mask::MaskOut(mask), Mask::ClipCount({count, {1, 1}}));
    s.download({count});
    if (count->data()[0] == 0) return {};

    auto dilated = maskPlane();
    dilate::Parameters d;
    d.u_msize = grid;
    s.passes.compute(grid, d, dilate::MaskIn(mask), dilate::MaskDil(dilated));
    auto rows = s.gpu.tensorT<int32_t>(std::size_t(s.image.y) * 6);
    typename Accum::Parameters p;
    p.u_size = s.image;
    p.u_msize = grid;
    p.u_whitepoint = wp;
    p.u_loclip.fill(.2f * clip);
    p.u_clip = clip;
    p.CfaPattern = raw.cfaPattern;
    s.passes.compute({64, s.image.y}, p, Accum::u_raw(input), Accum::Mask(dilated),
                     Accum::RowSums({rows, {6, s.image.y}}));
    s.download({rows});
    Color chroma{};
    const int32_t* data = rows->data();
    for (int c = 0; c < 3; ++c) {
        int64_t sum = 0, samples = 0;
        for (int y = 0; y < s.image.y; ++y) {
            sum += data[y * 6 + c];
            samples += data[y * 6 + c + 3];
        }
        if (samples > 100) chroma[c] = float(double(sum) / 65536.0 / double(samples));
    }
    return chroma;
}

template <class Pass, bool reconstruct>
void calibrate(PostState& s, const BayerSettings& raw, Rgba gainMap, const Color& chroma, Size offset) {
    typename Pass::Parameters p;
    p.RawInvSize = {1.f / s.image.x, 1.f / s.image.y};
    p.CfaPattern = raw.cfaPattern;
    p.whitePoint = s.settings.whitePoint;
    p.OFFSET = offset;
    if constexpr (reconstruct) {
        p.RawSize = s.image;
        p.HLCLIP = .987f * raw.hlClip;
        p.Chrominance = chroma;
    }
    if constexpr (reconstruct) {
        for (float wp : s.settings.whitePoint)
            if (wp > 0 && wp < 1) s.rawClipLevel = std::max(s.rawClipLevel, 1.f / wp);
    }
    auto calibrated = s.scalar(s.image);
    s.passes.dispatch(p, Pass::InputBuffer(s.cfa), Pass::GainMap(gainMap), Pass::Output(calibrated));
    s.cfa = calibrated;
}
} // namespace

void Bayer2Float::run(PostState& s) {
    auto gains = s.gpu.tensorT(settings_.gainMap);
    s.upload({gains});
    Rgba map{gains, settings_.mapSize};
    s.gainMap = map;
    if (s.settings.demosaicing == Demosaicing::mergedRgb) {
        auto chroma = opposedHighlights<mask_rgb, accum_rgb>(s, settings_, s.rgb);
        auto out = s.nextRgb();
        if (chroma) {
            tofloat_rgb1::Parameters p;
            p.CfaPattern = settings_.cfaPattern;
            p.RawInvSize = {1.f / s.image.x, 1.f / s.image.y};
            p.OFFSET = offset_;
            p.HLCLIP = .987f * settings_.hlClip;
            p.Chrominance = *chroma;
            s.passes.dispatch(p, tofloat_rgb1::InputBuffer(s.rgb), tofloat_rgb1::GainMap(map),
                              tofloat_rgb1::Output(out));
        } else {
            tofloat_rgb0::Parameters p;
            p.CfaPattern = settings_.cfaPattern;
            p.RawInvSize = {1.f / s.image.x, 1.f / s.image.y};
            p.OFFSET = offset_;
            s.passes.dispatch(p, tofloat_rgb0::InputBuffer(s.rgb), tofloat_rgb0::GainMap(map),
                              tofloat_rgb0::Output(out));
        }
        s.rgb = out;
    } else if (s.settings.demosaicing == Demosaicing::quad) {
        auto chroma = opposedHighlights<mask_quad, accum_quad>(s, settings_, s.cfa);
        if (chroma)
            calibrate<tofloat_quad1, true>(s, settings_, map, *chroma, offset_);
        else
            calibrate<tofloat_quad0, false>(s, settings_, map, {}, offset_);
    } else {
        auto chroma = opposedHighlights<mask_bayer, accum_bayer>(s, settings_, s.cfa);
        if (chroma)
            calibrate<tofloat_bayer1, true>(s, settings_, map, *chroma, offset_);
        else
            calibrate<tofloat_bayer0, false>(s, settings_, map, {}, offset_);
    }
}
} // namespace photon::post
