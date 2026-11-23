//
// Created by eszdman on 19.11.2026.
//

#include "framework/esd4d/PipelineCache.hpp"
#include "esd4d/Bayer.hpp"
#include "esd4d/KernelMap.hpp"
#include <exception>
#include <jni.h>

extern "C" JNIEXPORT jboolean JNICALL Java_com_particlesdevs_photoncamera_app_PhotonCamera_prepareBurstKernelNet(
    JNIEnv* env, jclass, jint width, jint height, jint cfa) {
    try {
        return photon::esd4d::warmKernelMap(photon::esd4d::geometryFor({width, height, cfa, {}, 1.f}));
    } catch (const std::exception& error) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), error.what());
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT jint JNICALL Java_com_particlesdevs_photoncamera_app_PhotonCamera_prepareVulkanPipelines(
    JNIEnv *env, jclass, jstring directory) {
    const char *path = env->GetStringUTFChars(directory, nullptr);
    if (!path)
        return 0;
    jint prepared = 0;
    try {
        prepared = static_cast<jint>(photon::esd4d::preparePipelines(path));
    } catch (const std::exception &error) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), error.what());
    }
    env->ReleaseStringUTFChars(directory, path);
    return prepared;
}
