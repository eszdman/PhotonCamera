#include <jni.h>
#include <android/log.h>
#include "AiUpscalerPipeline.cpp"
#include "JpegEncoder.cpp"

extern "C" JNIEXPORT jboolean JNICALL
Java_com_particlesdevs_photoncamera_processing_NativeBridge_processAndEncode(
        JNIEnv* env,
        jclass clazz,
        jobject mergedRawBuffer, // Буфер сшитого кадра после HDR+ Fusion
        jint inWidth,
        jint inHeight,
        jint aiMode,
        jint targetWidth,
        jint targetHeight,
        jstring outFilePath,
        jint jpegQuality
) {
    uint8_t* inBufferPtr = static_cast<uint8_t*>(env->GetDirectBufferAddress(mergedRawBuffer));
    if (!inBufferPtr) {
        __android_log_print(ANDROID_LOG_ERROR, "NativeBridge", "Failed to get DirectBuffer address");
        return JNI_FALSE;
    }

    // Выделяем память под итоговое разрешение
    std::vector<uint8_t> upscaledBuffer(targetWidth * targetHeight * 3);

    AiUpscalerPipeline pipeline;
    if (!pipeline.loadModel(aiMode)) {
        return JNI_FALSE;
    }

    // Инференс NCNN Vulkan
    pipeline.runRealSRVulkan(
            inBufferPtr, inWidth, inHeight,
            upscaledBuffer.data(), targetWidth, targetHeight
    );

    const char* nativeOutPath = env->GetStringUTFChars(outFilePath, nullptr);

    // Ультра-быстрое кодирование
    JpegEncoder encoder;
    bool success = encoder.encodeToFile(
            upscaledBuffer.data(),
            targetWidth,
            targetHeight,
            jpegQuality,
            nativeOutPath
    );

    env->ReleaseStringUTFChars(outFilePath, nativeOutPath);

    return success ? JNI_TRUE : JNI_FALSE;
}