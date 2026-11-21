//
// Created by eszdman on 16.08.2026.
//
// Single JNI wrapper around ncnn for the two ML models
// used by PhotonCamera:
//
//   * FlowNet-v2 dense optical flow (flownet_flat.ncnn.param/.bin)
//   * KernelNet anisotropic parameter model (kernelnet_v3_params.ncnn.param/.bin)
//
// Both networks share one ncnn runtime linked statically into this library
// (see ncnn/<ABI>/lib/libncnn.a). The FlowNet model requires three custom
// layers (FlownetUpsample / FlownetCorrLookup / FlownetCorrFused) that are
// compiled into this translation unit and registered at runtime via
// Net::register_custom_layer() — no ncnn source modifications needed.
//
// Java side:
//   com.particlesdevs.photoncamera.processing.ml.FlowNetNcnnProcessor
//   com.particlesdevs.photoncamera.processing.ml.KernelNetNcnnProcessor
//
// The library is built as libncnnMl.so and loaded via System.loadLibrary("ncnnMl").
//

#include <jni.h>
#include <android/log.h>
#include <android/asset_manager_jni.h>
#include <android/asset_manager.h>

#include <algorithm>
#include <cstdlib>
#include <cstring>
#include <memory>
#include <string>
#include <sys/time.h>
#include <vector>

#include "net.h"
#include "mat.h"
#include "halfConvert.h"
#include "esd4d/KernelTile.hpp"

// FlowNet custom layer registration (picks Vulkan or CPU creators based on NCNN_VULKAN)
#include "flownet_register.h"

#ifdef _OPENMP
#include <omp.h>
#endif

#define LOG_TAG "NcnnML"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Pin the OpenMP runtime to a fixed number of threads. No CPU-count / core
// topology probing here — the caller decides the thread count.
static void pinOpenMPThreads(int num_threads) {
#ifdef _OPENMP
    omp_set_dynamic(0);
    omp_set_num_threads(num_threads);
#else
    (void)num_threads;
#endif
}

static int64_t nowMs() {
    struct timeval tv;
    gettimeofday(&tv, nullptr);
    return (int64_t)tv.tv_sec * 1000 + tv.tv_usec / 1000;
}

static int64_t nowUs() {
    struct timeval tv;
    gettimeofday(&tv, nullptr);
    return (int64_t)tv.tv_sec * 1000000 + tv.tv_usec;
}

// Derive .bin asset path from .param path: "models/foo.ncnn.param" → "models/foo.ncnn.bin"
static std::string paramToBinPath(const std::string& paramPath) {
    std::string binPath = paramPath;
    size_t pos = binPath.rfind(".param");
    if (pos != std::string::npos)
        binPath.replace(pos, 6, ".bin");
    return binPath;
}

// ---------------------------------------------------------------------------
// Shared init (no-op for ncnn; kept for Java compatibility).
// ---------------------------------------------------------------------------
extern "C" JNIEXPORT jboolean JNICALL
Java_com_particlesdevs_photoncamera_processing_ml_NcnnMl_nativeEnsureInit(
    JNIEnv*, jclass) {
    return JNI_TRUE;
}

// ===========================================================================
// FlowNet-v2 (fixed 768×432 input)
// ===========================================================================

struct FlowNetCtx {
    ncnn::Net net;
};

extern "C" JNIEXPORT jlong JNICALL
Java_com_particlesdevs_photoncamera_processing_ml_FlowNetNcnnProcessor_nativeCreate(
    JNIEnv* env, jclass, jobject assetManager, jstring paramPath) {
    int64_t t0 = nowMs();

    AAssetManager* mgr = AAssetManager_fromJava(env, assetManager);
    if (mgr == nullptr) {
        LOGE("AAssetManager_fromJava failed");
        return 0;
    }
    const char* path = env->GetStringUTFChars(paramPath, nullptr);
    if (path == nullptr) {
        LOGE("paramPath null");
        return 0;
    }
    std::string paramStr = path;
    env->ReleaseStringUTFChars(paramPath, path);

    auto* ctx = new (std::nothrow) FlowNetCtx();
    if (ctx == nullptr) {
        LOGE("OOM allocating FlowNetCtx");
        return 0;
    }

    // Vulkan + fp16 for GPU speed. Subgroup ops disabled (crash on Adreno/Mali).
    // fp16 arithmetic engages tensor cores for convs; the custom corr shader
    // keeps its 128-element dot in fp32 regardless (declared support_fp16_storage
    // but the shader uses float accumulators).
    ctx->net.opt.use_vulkan_compute = true;
    ctx->net.opt.use_fp16_packed = true;
    ctx->net.opt.use_fp16_storage = true;
    ctx->net.opt.use_fp16_arithmetic = true;
    ctx->net.opt.use_bf16_storage = false;
    ctx->net.opt.use_subgroup_ops = false;
    ctx->net.opt.num_threads = 4;
    ctx->net.opt.lightmode = false;
    pinOpenMPThreads(ctx->net.opt.num_threads);

    // Allow forcing CPU via env (debug only).
    if (getenv("FLOWNET_CPU") && getenv("FLOWNET_CPU")[0] == '1') {
        ctx->net.opt.use_vulkan_compute = false;
        LOGI("flownet: Vulkan disabled by FLOWNET_CPU=1, using CPU");
    }

    flownet_register_custom_layers(ctx->net);

    std::string binPath = paramToBinPath(paramStr);

    if (ctx->net.load_param(mgr, paramStr.c_str()) != 0) {
        LOGE("flownet load_param(%s) failed", paramStr.c_str());
        delete ctx;
        return 0;
    }
    if (ctx->net.load_model(mgr, binPath.c_str()) != 0) {
        LOGE("flownet load_model(%s) failed", binPath.c_str());
        delete ctx;
        return 0;
    }

    LOGI("flownet init took %lldms (vulkan=%d)", (long long)(nowMs() - t0),
         ctx->net.opt.use_vulkan_compute);
    return (jlong)ctx;
}

// baseRgba/alterRgba: interleaved floats [w*h*4], B,G,R in [0,255] (A unused).
// flowOut: [h*w*2] channel-last floats (flow.x, flow.y) in input-pixel units.
extern "C" JNIEXPORT jboolean JNICALL
Java_com_particlesdevs_photoncamera_processing_ml_FlowNetNcnnProcessor_nativeRun(
    JNIEnv* env, jclass, jlong handle, jobject baseRgba, jobject alterRgba,
    jint width, jint height, jobject flowOut) {
    auto* ctx = reinterpret_cast<FlowNetCtx*>(handle);
    if (ctx == nullptr) return JNI_FALSE;
    pinOpenMPThreads(ctx->net.opt.num_threads);

    const float* basePtr = static_cast<const float*>(env->GetDirectBufferAddress(baseRgba));
    const float* alterPtr = static_cast<const float*>(env->GetDirectBufferAddress(alterRgba));
    float* outPtr = static_cast<float*>(env->GetDirectBufferAddress(flowOut));
    if (basePtr == nullptr || alterPtr == nullptr || outPtr == nullptr) {
        LOGE("GetDirectBufferAddress failed");
        return JNI_FALSE;
    }

    const int plane = width * height;

    // Deinterleave RGBA→BGR and create ncnn Mats (CHW float32, values 0..255).
    // The model divides by 255 internally (BinaryOp div in the param graph).
    ncnn::Mat in0(width, height, 3);
    ncnn::Mat in1(width, height, 3);
    {
        float* c0 = (float*)in0.channel(0); // B
        float* c1 = (float*)in0.channel(1); // G
        float* c2 = (float*)in0.channel(2); // R
        float* d0 = (float*)in1.channel(0);
        float* d1 = (float*)in1.channel(1);
        float* d2 = (float*)in1.channel(2);
        for (int i = 0; i < plane; i++) {
            c0[i] = basePtr[4 * i + 0];
            c1[i] = basePtr[4 * i + 1];
            c2[i] = basePtr[4 * i + 2];
            d0[i] = alterPtr[4 * i + 0];
            d1[i] = alterPtr[4 * i + 1];
            d2[i] = alterPtr[4 * i + 2];
        }
    }

    LOGI("flownet input in0 dims=%d w=%d h=%d c=%d elemsize=%zu",
         in0.dims, in0.w, in0.h, in0.c, (size_t)in0.elemsize);

    int64_t tStart = nowMs();

    ncnn::Extractor ex = ctx->net.create_extractor();
    int ret_in0 = ex.input("in0", in0);
    int ret_in1 = ex.input("in1", in1);
    LOGI("flownet input ret: in0=%d in1=%d", ret_in0, ret_in1);

    ncnn::Mat out;
    int ret = ex.extract("out0", out);
    if (ret != 0) {
        LOGE("flownet extract failed ret=%d", ret);
        return JNI_FALSE;
    }

    LOGI("flownet forward %dx%d took %lld ms", width, height,
         (long long)(nowMs() - tStart));

    // Output [2,H,W] channel-major → channel-last [x, y].
    if (out.c < 2 || out.w != width || out.h != height) {
        LOGE("unexpected flownet output dims=%d w=%d h=%d c=%d",
             out.dims, out.w, out.h, out.c);
        return JNI_FALSE;
    }
    const float* flowX = (const float*)out.channel(0);
    const float* flowY = (const float*)out.channel(1);
    for (int i = 0; i < plane; i++) {
        outPtr[2 * i + 0] = flowX[i];
        outPtr[2 * i + 1] = flowY[i];
    }

    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_particlesdevs_photoncamera_processing_ml_FlowNetNcnnProcessor_nativeDestroy(
    JNIEnv*, jclass, jlong handle) {
    auto* ctx = reinterpret_cast<FlowNetCtx*>(handle);
    if (ctx != nullptr) delete ctx;
}

// ===========================================================================
// KernelNet (dynamic input size; output at half res)
//
// KernelNet runs on the CPU. Overlapping tiles bound activation memory and
// preserve the stride-2 output grid across tile boundaries.
//
// Env knobs (debug/experimentation):
//   KN_THREADS=N  override the KernelNet CPU thread count for benchmarking
//   KN_TILE=N    tile core size in px, rounded up to /16 (default 1024)
//   KN_BORDER=N  tile overlap in px, rounded up to even (default 32)
//   KN_NOTILE=1  fall back to the old single full-res pass
// ===========================================================================

struct KernelNetCtx {
    ncnn::Net net;
    int tileCore = 1024;   // valid interior advance between tiles (even, /16)
                            // ~1024 gives the small 4x4-ish tile grid at 12MP
                            // (4x3 at 4000x3000): fill/copy stay cache-local
    int tileBorder = 32;   // overlap margin per side (even, covers the v3 receptive field)
    bool forceTiling = false;
    bool stageTiming = false;
    // Fixed-size tile Mats, allocated on first run and reused for every tile
    // of every capture.
    ncnn::Mat grayTile;
    ncnn::Mat sigmaTile;
};

static jboolean kernelnetRunFull(KernelNetCtx* ctx, const float* grayPtr,
                                 int width, int height, float sigma, uint16_t* outPtr);
static jboolean kernelnetRunTiled(KernelNetCtx* ctx, const float* grayPtr,
                                  int width, int height, float sigma, uint16_t* outPtr);

extern "C" JNIEXPORT jlong JNICALL
Java_com_particlesdevs_photoncamera_processing_ml_KernelNetNcnnProcessor_nativeCreate(
    JNIEnv* env, jclass, jobject assetManager, jstring paramPath) {
    AAssetManager* mgr = AAssetManager_fromJava(env, assetManager);
    if (mgr == nullptr) {
        LOGE("AAssetManager_fromJava failed");
        return 0;
    }
    const char* path = env->GetStringUTFChars(paramPath, nullptr);
    if (path == nullptr) return 0;
    std::string paramStr = path;
    env->ReleaseStringUTFChars(paramPath, path);

    auto* ctx = new (std::nothrow) KernelNetCtx();
    if (ctx == nullptr) return 0;

    ctx->net.opt.use_vulkan_compute = false;
    ctx->net.opt.use_fp16_packed = true;
    ctx->net.opt.use_fp16_storage = true;
    ctx->net.opt.use_fp16_arithmetic = true;
    ctx->net.opt.use_bf16_storage = false;
    ctx->net.opt.use_subgroup_ops = false;
    ctx->net.opt.num_threads = photon::esd4d::kernelInferenceThreads;
    if (const char* value = getenv("KN_THREADS")) {
        const int threads = atoi(value);
        if (threads >= 1 && threads <= 8) ctx->net.opt.num_threads = threads;
    }
    // A 4-output Winograd block stays aligned across the 16-pixel tile steps.
    ctx->net.opt.use_winograd63_convolution = false;
    ctx->net.opt.lightmode = true;
    pinOpenMPThreads(ctx->net.opt.num_threads);

    if (getenv("KN_FP32") && getenv("KN_FP32")[0] == '1') {
        ctx->net.opt.use_fp16_packed = false;
        ctx->net.opt.use_fp16_storage = false;
        ctx->net.opt.use_fp16_arithmetic = false;
        LOGI("kernelnet: fp32 storage path selected by KN_FP32=1");
    }

    std::string binPath = paramToBinPath(paramStr);

    if (ctx->net.load_param(mgr, paramStr.c_str()) != 0) {
        LOGE("kernelnet load_param(%s) failed", paramStr.c_str());
        delete ctx;
        return 0;
    }
    if (ctx->net.load_model(mgr, binPath.c_str()) != 0) {
        LOGE("kernelnet load_model(%s) failed", binPath.c_str());
        delete ctx;
        return 0;
    }

    if (const char* t = getenv("KN_TILE")) {
        int v = atoi(t);
        if (v >= 64) {
            ctx->tileCore = (v + 15) / 16 * 16;
            ctx->forceTiling = true;
        }
    }
    if (const char* b = getenv("KN_BORDER")) {
        int v = atoi(b);
        if (v >= 8) ctx->tileBorder = (v + 1) / 2 * 2;
    }
    if (const char* st = getenv("KN_STAGETIMING")) {
        ctx->stageTiming = strcmp(st, "0") != 0;
    }

    LOGI("kernelnet backend: cpu (%d threads, tile core=%d border=%d)",
         ctx->net.opt.num_threads, ctx->tileCore, ctx->tileBorder);
    return (jlong)ctx;
}

// gray: [w*h] luma floats in [0,1]. sigma is a scalar noise estimate, tiled to
// a full-res plane (the graph takes two 1-channel inputs). Output at half res,
// RGBA-interleaved: (s1, s2, rho, 1) HALF floats per texel, row-major — the
// exact GL_RGBA16F layout, so the buffer goes straight into glTexSubImage2D
// with GL_HALF_FLOAT (no driver FLOAT->HALF conversion on the Java side).
extern "C" JNIEXPORT jboolean JNICALL
Java_com_particlesdevs_photoncamera_processing_ml_KernelNetNcnnProcessor_nativeRun(
    JNIEnv* env, jclass, jlong handle, jobject grayBuffer, jint width, jint height,
    jfloat sigma, jobject outBuffer) {
    auto* ctx = reinterpret_cast<KernelNetCtx*>(handle);
    if (ctx == nullptr) return JNI_FALSE;
    pinOpenMPThreads(ctx->net.opt.num_threads);

    const float* grayPtr = static_cast<const float*>(env->GetDirectBufferAddress(grayBuffer));
    uint16_t* outPtr = static_cast<uint16_t*>(env->GetDirectBufferAddress(outBuffer));
    if (grayPtr == nullptr || outPtr == nullptr) {
        LOGE("GetDirectBufferAddress failed");
        return JNI_FALSE;
    }

    if ((!ctx->forceTiling && photon::esd4d::KernelTile::fitsSinglePass(width, height)) ||
        (getenv("KN_NOTILE") && getenv("KN_NOTILE")[0] == '1'))
        return kernelnetRunFull(ctx, grayPtr, width, height, sigma, outPtr);
    return kernelnetRunTiled(ctx, grayPtr, width, height, sigma, outPtr);
}

// One full-frame inference for moderate inputs, with the same half-resolution output.
static jboolean kernelnetRunFull(KernelNetCtx* ctx, const float* grayPtr,
                                 int width, int height, float sigma, uint16_t* outPtr) {
    const int plane = width * height;

    ncnn::Mat gray(width, height, 1);
    ncnn::Mat sigmaMat(width, height, 1);
    if (gray.empty() || sigmaMat.empty()) return JNI_FALSE;
    memcpy(gray.data, grayPtr, (size_t)plane * sizeof(float));
    float* sp = (float*)sigmaMat.data;
    std::fill(sp, sp + plane, sigma);

    int64_t tStart = nowMs();

    ncnn::Extractor ex = ctx->net.create_extractor();
    if (ex.input("in0", gray) || ex.input("in1", sigmaMat)) return JNI_FALSE;

    ncnn::Mat out;
    if (ex.extract("out0", out) != 0) {
        LOGE("kernelnet extract failed");
        return JNI_FALSE;
    }

    LOGI("kernelnet forward %dx%d took %lld ms", width, height,
         (long long)(nowMs() - tStart));

    // Output [3, outH, outW] channel-major -> RGBA-interleaved (s1, s2, rho, 1).
    if (out.c != 3 || out.w != (width + 1) / 2 || out.h != (height + 1) / 2) {
        LOGE("unexpected kernelnet output dims=%d w=%d h=%d c=%d",
             out.dims, out.w, out.h, out.c);
        return JNI_FALSE;
    }
    const int outPlane = out.w * out.h;
    const float* ch0 = (const float*)out.channel(0);
    const float* ch1 = (const float*)out.channel(1);
    const float* ch2 = (const float*)out.channel(2);
#if PHOTON_F16_NEON
    for (int i = 0; i < outPlane; i++) {
        float32x4_t v = {ch0[i], ch1[i], ch2[i], 1.0f};
        vst1_u16(outPtr + (size_t)i * 4, f32x4ToF16x4(v));
    }
#else
    for (int i = 0; i < outPlane; i++) {
        outPtr[(size_t)i * 4 + 0] = f32ToF16(ch0[i]);
        outPtr[(size_t)i * 4 + 1] = f32ToF16(ch1[i]);
        outPtr[(size_t)i * 4 + 2] = f32ToF16(ch2[i]);
        outPtr[(size_t)i * 4 + 3] = 0x3C00u; // 1.0h
    }
#endif

    return JNI_TRUE;
}

// Fill one TILE x TILE luma tile from the full-res plane with clamp-to-edge
// padding. Tile origins are always >= 0, so only right/bottom can overflow.
static void fillTileClamped(float* dst, const float* src, int W, int H,
                            int x0, int y0, int T) {
    for (int y = 0; y < T; y++) {
        int sy = y0 + y;
        if (sy > H - 1) sy = H - 1;
        const float* srow = src + (size_t)sy * W;
        float* drow = dst + (size_t)y * T;
        const int mid = (x0 + T <= W) ? T : (W - x0);
        memcpy(drow, srow + x0, (size_t)mid * sizeof(float));
        if (mid < T) {
            const float rv = srow[W - 1];
            for (int x = mid; x < T; x++) drow[x] = rv;
        }
    }
}

static jboolean kernelnetRunTiled(KernelNetCtx* ctx, const float* grayPtr,
                                  int width, int height, float sigma, uint16_t* outPtr) {
    const int B = ctx->tileBorder;      // overlap per side (even)
    const int STEP = ctx->tileCore;     // interior advance (even, /16)
    const int TILE = STEP + 2 * B;      // fixed net input size (even)
    const int tOut = TILE / 2;          // fixed tile output size
    const int outW = (width - 1) / 2 + 1;
    const int outH = (height - 1) / 2 + 1;

    if (ctx->grayTile.empty()) {
        ctx->grayTile.create(TILE, TILE, 1);
        ctx->sigmaTile.create(TILE, TILE, 1);
    }
    float* sigmaData = (float*)ctx->sigmaTile.data;
    std::fill(sigmaData, sigmaData + (size_t)TILE * TILE, sigma);

    // Number of tiles such that the last tile's core reaches the image edge.
    auto tileCount = [TILE, STEP](int dim) -> int {
        return dim <= TILE ? 1 : (dim - TILE + STEP - 1) / STEP + 1;
    };
    const int nx = tileCount(width);
    const int ny = tileCount(height);

    int64_t tStart = nowMs();
    int64_t tFill = 0, tInput = 0, tExtract = 0, tCopy = 0;
    int64_t worstExtract = 0;

    for (int iy = 0; iy < ny; iy++) {
        const int ty0 = iy * STEP;
        const int vy0 = (iy == 0) ? 0 : ty0 + B;
        const int vy1 = (iy == ny - 1) ? height : ty0 + TILE - B;
        for (int ix = 0; ix < nx; ix++) {
            const int tx0 = ix * STEP;
            const int vx0 = (ix == 0) ? 0 : tx0 + B;
            const int vx1 = (ix == nx - 1) ? width : tx0 + TILE - B;

            int64_t s0 = nowUs();
            fillTileClamped((float*)ctx->grayTile.data, grayPtr,
                            width, height, tx0, ty0, TILE);
            int64_t s1 = nowUs();

            ncnn::Extractor ex = ctx->net.create_extractor();
            if (ex.input("in0", ctx->grayTile) != 0 ||
                ex.input("in1", ctx->sigmaTile) != 0) {
                LOGE("kernelnet tile input failed");
                return JNI_FALSE;
            }
            int64_t s2 = nowUs();
            ncnn::Mat out;
            if (ex.extract("out0", out) != 0) {
                LOGE("kernelnet tile extract failed");
                return JNI_FALSE;
            }
            int64_t s3 = nowUs();
            if (out.c != 3 || out.w != tOut || out.h != tOut) {
                LOGE("unexpected kernelnet tile output dims=%d w=%d h=%d c=%d",
                     out.dims, out.w, out.h, out.c);
                return JNI_FALSE;
            }
            int64_t s4 = nowUs();

            // Half-res core of this tile -> global output. Origins are even,
            // so output g of a tile maps exactly to full-res output index
            // g + origin/2 (stride-2 conv phase preserved). Written
            // RGBA-interleaved (s1, s2, rho, 1) halves per texel so the buffer
            // uploads to the kernelsMap texture with GL_HALF_FLOAT, no repass.
            const int gx0 = vx0 / 2, gx1 = (vx1 + 1) / 2;
            const int gy0 = vy0 / 2, gy1 = (vy1 + 1) / 2;
            const int lx0 = gx0 - tx0 / 2, ly0 = gy0 - ty0 / 2;
            const int rowLen = gx1 - gx0;
            const float* ch0 = (const float*)out.channel(0);
            const float* ch1 = (const float*)out.channel(1);
            const float* ch2 = (const float*)out.channel(2);
            for (int y = 0; y < gy1 - gy0; y++) {
                const float* r0 = ch0 + (size_t)(ly0 + y) * tOut + lx0;
                const float* r1 = ch1 + (size_t)(ly0 + y) * tOut + lx0;
                const float* r2 = ch2 + (size_t)(ly0 + y) * tOut + lx0;
                uint16_t* dst = outPtr + ((size_t)(gy0 + y) * outW + gx0) * 4;
#if PHOTON_F16_NEON
                for (int x = 0; x < rowLen; x++) {
                    float32x4_t v = {r0[x], r1[x], r2[x], 1.0f};
                    vst1_u16(dst + x * 4, f32x4ToF16x4(v));
                }
#else
                for (int x = 0; x < rowLen; x++) {
                    dst[x * 4 + 0] = f32ToF16(r0[x]);
                    dst[x * 4 + 1] = f32ToF16(r1[x]);
                    dst[x * 4 + 2] = f32ToF16(r2[x]);
                    dst[x * 4 + 3] = 0x3C00u; // 1.0h
                }
#endif
            }
            int64_t s5 = nowUs();

            tFill += s1 - s0;
            tInput += s2 - s1;
            tExtract += s3 - s2;
            tCopy += s5 - s4;
            if (s3 - s2 > worstExtract) worstExtract = s3 - s2;
        }
    }

    LOGI("kernelnet tiled %dx%d -> %dx%d (%dx%d tiles of %dpx, core=%d border=%d)"
         " took %lld ms", width, height, outW, outH, nx, ny, TILE, STEP, B,
         (long long)(nowMs() - tStart));
    if (ctx->stageTiming) {
        const int n = nx * ny;
        LOGI("kernelnet stages (%d tiles): fill=%lldus input=%lldus extract=%lldus"
             " (worst %lldus) copy=%lldus", n,
             (long long)tFill / n, (long long)tInput / n,
             (long long)tExtract / n, (long long)worstExtract,
             (long long)tCopy / n);
    }
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_particlesdevs_photoncamera_processing_ml_KernelNetNcnnProcessor_nativeDestroy(
    JNIEnv*, jclass, jlong handle) {
    auto* ctx = reinterpret_cast<KernelNetCtx*>(handle);
    if (ctx != nullptr) delete ctx;
}
