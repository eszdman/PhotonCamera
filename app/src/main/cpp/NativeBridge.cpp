#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <algorithm>
#include "BayerFusion.h"
#include "AiUpscalerPipeline.h"
#include "JpegEncoder.h"
#include "ZslRingBuffer.h"
#include "LutColorEngine.h"
#include "ResolutionCalculator.h"
#include "WatermarkEngine.h"

#define LOG_TAG "KoshcamNativeBridge"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

static koshcam::ZslRingBuffer gZslRingBuffer;

extern "C" JNIEXPORT jint JNICALL
Java_com_koshara_koshcam_processing_NativeBridge_processFullPipeline(
        JNIEnv* env, jclass clazz,
        jobject burstBuffer, // DirectByteBuffer with 15 RAW10 frames
        jint width, jint height,
        jint mode,
        jint targetWidth, jint targetHeight,
        jstring outPath,
        jint outFd
) {
    const uint8_t* burstPtr = static_cast<uint8_t*>(env->GetDirectBufferAddress(burstBuffer));
    if (!burstPtr) return -1;

    LOGI("Processing pipeline: input %dx%d -> target %dx%d (mode=%d)", width, height, targetWidth, targetHeight, mode);

    // 1. Bayer Fusion
    BayerFusion::Config fusionCfg;
    fusionCfg.width = width;
    fusionCfg.height = height;
    BayerFusion fusion(fusionCfg);

    std::vector<float> fusedBayer(static_cast<size_t>(width) * height);
    fusion.process(burstPtr, fusedBayer.data());
    LOGD("Bayer fusion completed");

    // 2. AI Upscaling (ncnn + Vulkan Mali-G57 MC2)
    AiUpscalerPipeline upscaler;
    std::string modelPath = "/data/data/com.koshara.koshcam/files/models";
    upscaler.init(modelPath.c_str(), mode);

    std::vector<float> upscaledRgb(static_cast<size_t>(targetWidth) * targetHeight * 3);
    upscaler.process(fusedBayer.data(), width, height, upscaledRgb.data(), targetWidth, targetHeight);
    LOGD("AI upscaling completed to %dx%d", targetWidth, targetHeight);

    // 3. Convert F32 RGB to U8 RGB
    std::vector<uint8_t> u8Rgb(upscaledRgb.size());
    for (size_t i = 0; i < upscaledRgb.size(); ++i) {
        u8Rgb[i] = static_cast<uint8_t>(std::clamp(upscaledRgb[i], 0.0f, 255.0f));
    }

    // 4. Dynamic Watermark Engine ("koshara" bottom black banner)
    koshcam::ExifInfo exif;
    exif.focalLength = "4.74mm f/1.8";
    exif.shutterSpeed = "1/50s";
    exif.iso = "ISO 100";
    exif.brand = "Koshcam";
    exif.author = "Shot by koshara";

    int finalHeight = targetHeight;
    std::vector<uint8_t> watermarkedRgb = koshcam::WatermarkEngine::attachBanner(
        u8Rgb.data(), targetWidth, targetHeight, finalHeight, exif
    );

    // 5. Ultra-Fast TurboJPEG Encoding (quality 98+, 4:4:4, ACCURATEDCT)
    JpegEncoder::Config jpegCfg;
    jpegCfg.width = targetWidth;
    jpegCfg.height = finalHeight;
    jpegCfg.quality = 98;
    jpegCfg.accurateDCT = true;

    int result = -1;
    if (outFd > 0) {
        result = JpegEncoder::encodeToFd(watermarkedRgb.data(), outFd, jpegCfg);
    } else {
        const char* path = env->GetStringUTFChars(outPath, nullptr);
        result = JpegEncoder::encodeToDisk(watermarkedRgb.data(), path, jpegCfg);
        env->ReleaseStringUTFChars(outPath, path);
    }

    LOGI("Koshcam full pipeline finished with status: %d", result);
    return result;
}

// Fallback JNI export for backward compatibility during package migration
extern "C" JNIEXPORT jint JNICALL
Java_com_particlesdevs_photoncamera_processing_NativeBridge_processFullPipeline(
        JNIEnv* env, jclass clazz,
        jobject burstBuffer, jint width, jint height,
        jint mode, jint targetWidth, jint targetHeight,
        jstring outPath, jint outFd
) {
    return Java_com_koshara_koshcam_processing_NativeBridge_processFullPipeline(
        env, clazz, burstBuffer, width, height, mode, targetWidth, targetHeight, outPath, outFd
    );
}

// ZSL Ring Buffer JNI interface
extern "C" JNIEXPORT void JNICALL
Java_com_koshara_koshcam_processing_NativeBridge_pushZslFrame(
        JNIEnv* env, jclass clazz,
        jobject buffer, jint size, jint width, jint height, jlong timestampNs
) {
    const uint8_t* ptr = static_cast<uint8_t*>(env->GetDirectBufferAddress(buffer));
    if (ptr) {
        gZslRingBuffer.pushFrame(ptr, size, width, height, timestampNs);
    }
}

// Dynamic Resolution Engine JNI interface
extern "C" JNIEXPORT jintArray JNICALL
Java_com_koshara_koshcam_processing_NativeBridge_calculateTargetResolution(
        JNIEnv* env, jclass clazz,
        jint presetMp, jint aspectOrdinal
) {
    koshcam::TargetPreset preset = static_cast<koshcam::TargetPreset>(presetMp);
    koshcam::AspectRatio aspect = static_cast<koshcam::AspectRatio>(aspectOrdinal);
    koshcam::TargetSize res = koshcam::DynamicResolutionEngine::calculateResolution(preset, aspect);

    jintArray result = env->NewIntArray(2);
    jint values[2] = {res.width, res.height};
    env->SetIntArrayRegion(result, 0, 2, values);
    return result;
}
