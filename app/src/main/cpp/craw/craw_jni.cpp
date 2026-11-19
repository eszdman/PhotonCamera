#include <jni.h>
#include <craw/Writer.h>
#include "RawInput.h"
#include <memory>
#include <string>

namespace {
void fail(JNIEnv* env, const std::exception& e) {
    if (!env->ExceptionCheck()) env->ThrowNew(env->FindClass("java/io/IOException"), e.what());
}
std::string string(JNIEnv* env, jstring input) {
    if (!input) return "{}";
    const char* value = env->GetStringUTFChars(input, nullptr);
    if (!value) throw std::runtime_error("Cannot read metadata");
    std::string result(value);
    env->ReleaseStringUTFChars(input, value);
    return result;
}
craw::Writer& writer(jlong handle) {
    if (!handle) throw std::invalid_argument("CRAW writer closed");
    return *reinterpret_cast<craw::Writer*>(handle);
}
template<class T> void checkArray(JNIEnv* env, T array, size_t count) {
    if (!array || size_t(env->GetArrayLength(array)) != count)
        throw std::invalid_argument("Invalid CRAW array length");
}
}
#define JNI(name) Java_com_particlesdevs_photoncamera_util_CrawWriter_##name
extern "C" {
JNIEXPORT jlong JNICALL JNI(nativeCreate)(JNIEnv* env, jclass, jint fd, jstring meta) {
    try { return reinterpret_cast<jlong>(new craw::Writer(fd, string(env, meta))); }
    catch (const std::exception& e) { fail(env, e); return 0; }
}
JNIEXPORT jint JNICALL JNI(nativeEncode)(JNIEnv* env, jclass, jobject plane,
        jint width, jint height, jint stride, jboolean raw10, jint top, jint rows,
        jboolean bin, jint cfa, jint codec, jobject output) {
    try {
        auto raw = static_cast<const uint8_t*>(env->GetDirectBufferAddress(plane));
        auto dst = static_cast<uint8_t*>(env->GetDirectBufferAddress(output));
        jlong size = env->GetDirectBufferCapacity(plane), capacity = env->GetDirectBufferCapacity(output);
        if (!raw || !dst || size <= 0 || capacity <= 0)
            throw std::invalid_argument("CRAW needs direct buffers");
        thread_local std::vector<uint16_t> pixels;
        auto desc = photon::prepareRaw(raw, size, width, height, stride, raw10,
                top, rows, bin, cfa, codec, pixels);
        return craw::encodeFrame(pixels.data(), desc.width, desc, dst, capacity);
    } catch (const std::exception& e) { fail(env, e); return 0; }
}
JNIEXPORT void JNICALL JNI(nativeWriteFrame)(JNIEnv* env, jclass, jlong handle,
        jobject data, jint length, jlong ts, jstring meta) {
    try {
        auto bytes = static_cast<const uint8_t*>(env->GetDirectBufferAddress(data));
        if (!bytes || length <= 0 || length > env->GetDirectBufferCapacity(data))
            throw std::invalid_argument("Invalid CRAW frame buffer");
        writer(handle).addEncodedFrame(bytes, length, ts, string(env, meta));
    } catch (const std::exception& e) { fail(env, e); }
}
JNIEXPORT void JNICALL JNI(nativeWriteAudio)(JNIEnv* env, jclass, jlong handle,
        jshortArray samples, jlong ts, jint channels, jint rate) {
    try {
        if (!samples || channels <= 0 || channels > 255 || rate <= 0)
            throw std::invalid_argument("Invalid CRAW audio format");
        int count = env->GetArrayLength(samples);
        if (count % channels) throw std::invalid_argument("Partial audio frame");
        std::vector<jshort> pcm(count);
        env->GetShortArrayRegion(samples, 0, count, pcm.data());
        if (env->ExceptionCheck()) return;
        writer(handle).addAudio(ts, craw::AudioFormat::S16, channels, rate, pcm.data(), count / channels);
    } catch (const std::exception& e) { fail(env, e); }
}
JNIEXPORT void JNICALL JNI(nativeWriteImu)(JNIEnv* env, jclass, jlong handle,
        jint sensor, jlongArray timestamps, jfloatArray xyz) {
    try {
        if (!timestamps || (sensor != 1 && sensor != 2)) throw std::invalid_argument("Invalid IMU sensor");
        size_t count = env->GetArrayLength(timestamps);
        checkArray(env, xyz, count * 3);
        if (!count) return;
        std::vector<jlong> ts(count);
        std::vector<jfloat> values(count * 3);
        env->GetLongArrayRegion(timestamps, 0, count, ts.data());
        env->GetFloatArrayRegion(xyz, 0, count * 3, values.data());
        if (env->ExceptionCheck()) return;
        std::vector<craw::ImuSample> samples(count);
        for (size_t i = 0; i < count; ++i)
            samples[i] = {ts[i], values[i * 3], values[i * 3 + 1], values[i * 3 + 2]};
        writer(handle).addImu(static_cast<craw::ImuSensor>(sensor), samples.data(), count);
    } catch (const std::exception& e) { fail(env, e); }
}
JNIEXPORT void JNICALL JNI(nativeWriteShading)(JNIEnv* env, jclass, jlong handle,
        jlong ts, jint id, jint columns, jint rows, jfloatArray gains) {
    try {
        if (columns <= 0 || rows <= 0 || columns > 65535 || rows > 65535 || id < 0)
            throw std::invalid_argument("Invalid shading geometry");
        size_t count = size_t(columns) * rows * 4;
        checkArray(env, gains, count);
        std::vector<jfloat> values(count);
        env->GetFloatArrayRegion(gains, 0, count, values.data());
        if (env->ExceptionCheck()) return;
        writer(handle).addShading(ts, id, columns, rows, values.data());
    } catch (const std::exception& e) { fail(env, e); }
}
JNIEXPORT jlong JNICALL JNI(nativeFrameCount)(JNIEnv* env, jclass, jlong handle) {
    try { return writer(handle).frameCount(); }
    catch (const std::exception& e) { fail(env, e); return 0; }
}
JNIEXPORT void JNICALL JNI(nativeClose)(JNIEnv* env, jclass, jlong handle) {
    std::unique_ptr<craw::Writer> closing(reinterpret_cast<craw::Writer*>(handle));
    try { if (closing) closing->close(); }
    catch (const std::exception& e) { fail(env, e); }
}
}
