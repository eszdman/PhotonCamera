//
// Created by eszdman on 21.11.2026.
//

#include "KernelMap.hpp"
#include "Bayer.hpp"
#include "KernelTile.hpp"
#include "halfConvert.h"
#include <cmrc/cmrc.hpp>
#include <cpu.h>
#include <datareader.h>
#include <net.h>
#include <algorithm>
#include <atomic>
#include <cmath>
#include <cstring>
#include <stdexcept>
#include <string>

CMRC_DECLARE(esdmodel);

namespace photon::esd4d {
namespace {
// ncnn can retain references to model memory. Copy from the embedded asset into
// ncnn-owned weights, which also avoids alignment requirements on CMakeRC data.
class ModelReader final : public ncnn::DataReader {
  public:
    explicit ModelReader(cmrc::file file) : current_(file.begin()), end_(file.end()) {}
    size_t read(void* dest, size_t bytes) const override {
        bytes = std::min(bytes, size_t(end_ - current_));
        std::memcpy(dest, current_, bytes);
        current_ += bytes;
        return bytes;
    }
  private:
    mutable const char* current_;
    const char* end_;
};

struct KernelNet {
    // Shared by concurrent extractors. The allocator must outlive the net.
    ncnn::PoolAllocator blobs;
    ncnn::Net net;
    KernelNet() {
        auto& opt = net.opt;
        opt.blob_allocator = &blobs;
        opt.num_threads = kernelInferenceThreads;
        opt.use_vulkan_compute = false;
        opt.use_winograd63_convolution = false;
        opt.use_bf16_storage = false;
        opt.use_fp16_packed = opt.use_fp16_storage = opt.use_fp16_arithmetic = true;
        auto assets = cmrc::esdmodel::get_filesystem();
        auto graph = assets.open("kernelnet_v3_params.ncnn.param");
        ModelReader weights(assets.open("kernelnet_v3_params.ncnn.bin"));
        if (net.load_param_mem(std::string(graph.begin(), graph.end()).c_str()) || net.load_model(weights))
            throw std::runtime_error("Could not load the embedded ncnn KernelNet model");
    }

    ncnn::Mat extract(const ncnn::Mat& gray, const ncnn::Mat& noise, bool halfIO) const {
        auto ex = net.create_extractor();
        ncnn::Mat output;
        if (ex.input("in0", gray) || ex.input("in1", noise) || ex.extract("out0", output, halfIO ? 1 : 0))
            throw std::runtime_error("KernelNet extraction failed");
        return output;
    }
};
const KernelNet& kernelNet() {
    static const KernelNet model;
    return model;
}
std::atomic_flag kernelUsed = ATOMIC_FLAG_INIT;

bool useHalfIO(const KernelNet& model, bool singlePass, int w, int h) {
    // NCNN's scalar tail rounds differently for unaligned inputs.
    return singlePass && (int64_t(w) * h) % 4 == 0 && model.net.opt.use_fp16_storage &&
           ncnn::cpu_support_arm_asimdhp();
}

int paddedTileSize(int extent) { return (extent + 31) / 32 * 32; }
void packHalfRow(const ncnn::Mat& output, int y, int x, int width, uint32_t* dst) {
    const auto* s1 = output.channel(0).row<uint16_t>(y) + x;
    const auto* s2 = output.channel(1).row<uint16_t>(y) + x;
    const auto* rho = output.channel(2).row<uint16_t>(y) + x;
    for (int i = 0; i < width; ++i) {
        dst[2 * i] = s1[i] | (uint32_t(s2[i]) << 16);
        dst[2 * i + 1] = rho[i] | 0x3c000000u;
    }
}
} // namespace

bool warmKernelMap(const Geometry& geometry) {
    const int w = kernelLumaWidth(geometry), h = geometry.rawHalfHeight + geometry.shiftY;
    if (w <= 0 || h <= 0) throw std::invalid_argument("Invalid KernelNet warmup dimensions");
    // A capture can start before the camera-open job runs. Its extractor
    // takes precedence and never waits for a dummy inference to finish.
    if (kernelUsed.test_and_set()) return false;
    const auto& model = kernelNet();
    const bool singlePass = KernelTile::fitsSinglePass(w, h);
    const int tw = singlePass ? w : paddedTileSize(KernelTile::at(0, w).extent);
    const int th = singlePass ? h : paddedTileSize(KernelTile::at(0, h).extent);
    const bool halfIO = useHalfIO(model, singlePass, w, h);
    const size_t bytes = halfIO ? sizeof(uint16_t) : sizeof(float);
    auto* allocator = halfIO ? model.net.opt.blob_allocator : nullptr;
    ncnn::Mat gray(tw, th, 1, bytes, allocator), noise(tw, th, 1, bytes, allocator);
    if (gray.empty() || noise.empty()) throw std::bad_alloc();
    if (halfIO) {
        std::fill_n(static_cast<uint16_t*>(gray.data), size_t(tw) * th, f32ToF16(.5f));
        std::fill_n(static_cast<uint16_t*>(noise.data), size_t(tw) * th, f32ToF16(.01f));
    } else {
        gray.fill(.5f);
        noise.fill(.01f);
    }
    model.extract(gray, noise, halfIO);
    return true;
}

PackedQuads inferKernelMap(ComputeContext&, const std::vector<float>& luma, int w, int h, float sigma) {
    if (w <= 0 || h <= 0 || std::uint64_t(w) * h != luma.size() || !std::isfinite(sigma) || sigma < 0)
        throw std::invalid_argument("Invalid KernelNet brightness or sigma");
    kernelUsed.test_and_set();
    const auto& model = kernelNet();
    const int ow = (w + 1) / 2, oh = (h + 1) / 2;
    PackedQuads map(size_t(ow) * oh * 2);
    ncnn::Mat gray, noise;
    const bool singlePass = KernelTile::fitsSinglePass(w, h);
    const bool halfIO = useHalfIO(model, singlePass, w, h);
    const int nx = singlePass ? 1 : KernelTile::count(w), ny = singlePass ? 1 : KernelTile::count(h);
    for (int ty = 0; ty < ny; ++ty) {
        auto v = singlePass ? KernelTile{0, h, 0, oh} : KernelTile::at(ty, h);
        for (int tx = 0; tx < nx; ++tx) {
            auto u = singlePass ? KernelTile{0, w, 0, ow} : KernelTile::at(tx, w);
            // Bound edge padding and keep ncnn's convolution tiles aligned.
            const int tw = singlePass ? w : paddedTileSize(u.extent);
            const int th = singlePass ? h : paddedTileSize(v.extent);
            if (halfIO) {
                // The external Mat borrows the read-only vector for the duration of the cast.
                const ncnn::Mat src(w, h, 1, const_cast<float*>(luma.data()));
                ncnn::cast_float32_to_float16(src, gray, model.net.opt);
                noise.create(w, h, 1, sizeof(uint16_t), model.net.opt.blob_allocator);
                if (gray.empty() || noise.empty()) throw std::bad_alloc();
                std::fill_n(static_cast<uint16_t*>(noise.data), luma.size(), f32ToF16(sigma));
            } else {
                gray.create(tw, th, 1);
                noise.create(tw, th, 1);
                if (gray.empty() || noise.empty()) throw std::bad_alloc();
                noise.fill(sigma);
                const int columns = std::min(tw, w - u.origin);
                for (int y = 0; y < th; ++y) {
                    const float* src = luma.data() + size_t(std::min(v.origin + y, h - 1)) * w + u.origin;
                    float* row = gray.row(y);
                    std::copy_n(src, columns, row);
                    std::fill(row + columns, row + tw, src[columns - 1]);
                }
            }
            auto output = model.extract(gray, noise, halfIO);
            if (output.w != (tw + 1) / 2 || output.h != (th + 1) / 2 || output.c != 3 ||
                output.elempack != 1 || (output.elemsize != sizeof(float) && !(halfIO && output.elemsize == sizeof(uint16_t))))
                throw std::runtime_error("Invalid ncnn KernelNet output");
            for (int y = v.begin; y < v.end; ++y) {
                const int localY = y - v.origin / 2;
                if (output.elemsize == sizeof(uint16_t)) {
                    packHalfRow(output, localY, u.begin - u.origin / 2, u.end - u.begin,
                                map.data() + 2 * (size_t(y) * ow + u.begin));
                    continue;
                }
                const float* s1 = output.channel(0).row(localY);
                const float* s2 = output.channel(1).row(localY);
                const float* rho = output.channel(2).row(localY);
                for (int x = u.begin; x < u.end; ++x) {
                    const int localX = x - u.origin / 2;
                    const size_t at = (size_t(y) * ow + x) * 2;
#if PHOTON_F16_NEON
                    const float32x4_t values = {s1[localX], s2[localX], rho[localX], 1.f};
                    vst1_u32(map.data() + at, vreinterpret_u32_u16(f32x4ToF16x4(values)));
#else
                    map[at] = f32ToF16(s1[localX]) | (uint32_t(f32ToF16(s2[localX])) << 16);
                    map[at + 1] = f32ToF16(rho[localX]) | 0x3c000000u;
#endif
                }
            }
        }
    }
    return map;
}
} // namespace photon::esd4d
