#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <algorithm>
#include "BayerFusion.h"
#include "AiUpscalerPipeline.h"
#include "JpegEncoder.h"

#define LOG_TAG "NativeBridge"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jint JNICALL
Java_com_particlesdevs_photoncamera_processing_NativeBridge_processFullPipeline(
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

    // 1. Bayer Fusion
    BayerFusion::Config fusionCfg;
    fusionCfg.width = width;
    fusionCfg.height = height;
    BayerFusion fusion(fusionCfg);

    std::vector<float> fusedBayer(static_cast<size_t>(width) * height);
    fusion.process(burstPtr, fusedBayer.data());
    LOGD("Fusion completed");

    // 2. AI Upscale (ncnn + Vulkan)
    AiUpscalerPipeline upscaler;
    // Assume model is in internal storage
    std::string modelPath = "/data/data/com.particlesdevs.photoncamera/files/models";
    upscaler.init(modelPath.c_str(), mode);

    std::vector<float> upscaledRgb(static_cast<size_t>(targetWidth) * targetHeight * 3);
    upscaler.process(fusedBayer.data(), width, height, upscaledRgb.data(), targetWidth, targetHeight);
    LOGD("Upscaling completed to %dx%d", targetWidth, targetHeight);

    // 3. Jpeg Encoding (libjpeg-turbo)
    JpegEncoder::Config jpegCfg;
    jpegCfg.width = targetWidth;
    jpegCfg.height = targetHeight;

    // Convert F32 RGB to U8 RGB for TurboJPEG
    std::vector<uint8_t> u8Rgb(upscaledRgb.size());
    for (size_t i = 0; i < upscaledRgb.size(); ++i) {
        u8Rgb[i] = static_cast<uint8_t>(std::clamp(upscaledRgb[i], 0.0f, 255.0f));
    }

    int result = -1;
    if (outFd > 0) {
        result = JpegEncoder::encodeToFd(u8Rgb.data(), outFd, jpegCfg);
    } else {
        const char* path = env->GetStringUTFChars(outPath, nullptr);
        result = JpegEncoder::encodeToDisk(u8Rgb.data(), path, jpegCfg);
        env->ReleaseStringUTFChars(outPath, path);
    }

    LOGD("Pipeline finished with status: %d", result);
    return result;
}
