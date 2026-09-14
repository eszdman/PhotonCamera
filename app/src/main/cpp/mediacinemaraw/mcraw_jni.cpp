// SPDX-License-Identifier: GPL-3.0-only
// JNI bridge for the MediaCinemaRAW (.mcraw) encoder.
//
// Two-stage frame path, both zero-copy on the camera side:
//  - nativeEncode reads the direct ByteBuffer that backs an android.media.Image
//    RAW plane in place (the Image stays open, only for the duration of the
//    encode) and writes the compressed frame into a caller-provided direct
//    ByteBuffer scratch slot.
//  - nativeWriteFrame appends an already-encoded buffer to the container.
// Splitting the stages lets the camera Image be released as soon as encoding
// finishes, while disk writes proceed independently.
#include <jni.h>
#include <MediaCinemaRAW/Encoder.h>
#include <MediaCinemaRAW/ContainerWriter.h>

#include <exception>
#include <cstring>
#include <string>
#include <vector>

// Scratch buffer reused across frames per encoder thread to avoid per-frame
// heap allocation spikes; the final copy into the caller's slot is small
// relative to the encode itself.
static thread_local std::vector<uint8_t> t_encoded;

namespace {

void throwJava(JNIEnv* env, const char* clazz, const char* msg) {
    if (env->ExceptionCheck()) return;
    env->ThrowNew(env->FindClass(clazz), msg ? msg : "unknown native error");
}

std::string toStdString(JNIEnv* env, jstring s) {
    if (!s) return std::string();
    const char* chars = env->GetStringUTFChars(s, nullptr);
    std::string out(chars ? chars : "");
    env->ReleaseStringUTFChars(s, chars);
    return out;
}

} // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_particlesdevs_photoncamera_util_McrawWriter_nativeCreate(JNIEnv* env, jclass, jint fd, jstring metadata) {
    if (fd < 0) {
        throwJava(env, "java/lang/IllegalArgumentException", "bad file descriptor");
        return 0;
    }
    try {
        return reinterpret_cast<jlong>(
                new mediacinemaraw::ContainerWriter(fd, toStdString(env, metadata)));
    } catch (const std::exception& e) {
        throwJava(env, "java/io/IOException", e.what());
    } catch (...) {
        throwJava(env, "java/io/IOException", "mcraw create failed");
    }
    return 0;
}

// Encodes one frame in place from the camera plane into the output slot.
// Returns the encoded size in bytes, or 0 on failure after throwing.
JNIEXPORT jint JNICALL
Java_com_particlesdevs_photoncamera_util_McrawWriter_nativeEncode(JNIEnv* env, jclass,
        jobject rawPlane, jint width, jint height, jint stride, jboolean raw10,
        jint cropTop, jint cropHeight, jboolean bin, jobject outputSlot) {
    const auto* raw = reinterpret_cast<const uint8_t*>(env->GetDirectBufferAddress(rawPlane));
    const jlong rawSize = env->GetDirectBufferCapacity(rawPlane);
    auto* dst = reinterpret_cast<uint8_t*>(env->GetDirectBufferAddress(outputSlot));
    const jlong dstSize = env->GetDirectBufferCapacity(outputSlot);
    if (!raw || rawSize <= 0 || !dst || dstSize <= 0) {
        throwJava(env, "java/lang/IllegalArgumentException", "raw plane or output slot is not a direct buffer");
        return 0;
    }
    try {
        t_encoded.clear();
        mediacinemaraw::encode(raw, static_cast<size_t>(rawSize), width, height, stride,
                               raw10 == JNI_TRUE, cropTop, cropHeight, bin == JNI_TRUE, t_encoded);
        if (t_encoded.size() > static_cast<size_t>(dstSize)) {
            throwJava(env, "java/lang/IllegalArgumentException", "encoded frame exceeds output slot capacity");
            return 0;
        }
        std::memcpy(dst, t_encoded.data(), t_encoded.size());
        return static_cast<jint>(t_encoded.size());
    } catch (const std::exception& e) {
        throwJava(env, "java/io/IOException", e.what());
    } catch (...) {
        throwJava(env, "java/io/IOException", "mcraw encode failed");
    }
    return 0;
}

// Appends an already-encoded frame (from a slot filled by nativeEncode).
// Returns the frame size in bytes, or 0 on failure after throwing.
JNIEXPORT jint JNICALL
Java_com_particlesdevs_photoncamera_util_McrawWriter_nativeWriteFrame(JNIEnv* env, jclass, jlong handle,
        jobject encoded, jint length, jlong timestampNs, jstring frameMetadata) {
    auto* writer = reinterpret_cast<mediacinemaraw::ContainerWriter*>(handle);
    if (!writer) {
        throwJava(env, "java/lang/IllegalStateException", "mcraw writer is closed");
        return 0;
    }
    const auto* data = reinterpret_cast<const uint8_t*>(env->GetDirectBufferAddress(encoded));
    const jlong capacity = env->GetDirectBufferCapacity(encoded);
    if (!data || length <= 0 || length > capacity) {
        throwJava(env, "java/lang/IllegalArgumentException", "invalid encoded frame buffer");
        return 0;
    }
    try {
        writer->writeFrame(data, static_cast<size_t>(length), timestampNs,
                           toStdString(env, frameMetadata));
        return length;
    } catch (const std::exception& e) {
        throwJava(env, "java/io/IOException", e.what());
    } catch (...) {
        throwJava(env, "java/io/IOException", "mcraw frame write failed");
    }
    return 0;
}

JNIEXPORT jlong JNICALL
Java_com_particlesdevs_photoncamera_util_McrawWriter_nativeFrameCount(JNIEnv* env, jclass, jlong handle) {
    auto* writer = reinterpret_cast<mediacinemaraw::ContainerWriter*>(handle);
    if (!writer) return 0;
    return static_cast<jlong>(writer->frameCount());
}

// Appends one PCM16 audio chunk (samples = interleaved channels).
JNIEXPORT void JNICALL
Java_com_particlesdevs_photoncamera_util_McrawWriter_nativeWriteAudio(JNIEnv* env, jclass, jlong handle,
        jshortArray samples, jint count, jlong timestampNs) {
    auto* writer = reinterpret_cast<mediacinemaraw::ContainerWriter*>(handle);
    if (!writer) {
        throwJava(env, "java/lang/IllegalStateException", "mcraw writer is closed");
        return;
    }
    if (count <= 0 || env->GetArrayLength(samples) < count) return;
    try {
        std::vector<int16_t> pcm(static_cast<size_t>(count));
        env->GetShortArrayRegion(samples, 0, count, reinterpret_cast<jshort*>(pcm.data()));
        writer->writeAudio(pcm.data(), pcm.size(), timestampNs);
    } catch (const std::exception& e) {
        throwJava(env, "java/io/IOException", e.what());
    } catch (...) {
        throwJava(env, "java/io/IOException", "mcraw audio write failed");
    }
}

// Appends the gyro sample block (timestamps in the camera time domain).
JNIEXPORT void JNICALL
Java_com_particlesdevs_photoncamera_util_McrawWriter_nativeWriteGyro(JNIEnv* env, jclass, jlong handle,
        jlongArray timestamps, jfloatArray x, jfloatArray y, jfloatArray z, jint count) {
    auto* writer = reinterpret_cast<mediacinemaraw::ContainerWriter*>(handle);
    if (!writer) {
        throwJava(env, "java/lang/IllegalStateException", "mcraw writer is closed");
        return;
    }
    if (count <= 0) return;
    try {
        std::vector<mediacinemaraw::GyroSample> samples(static_cast<size_t>(count));
        std::vector<jlong> ts(static_cast<size_t>(count));
        std::vector<jfloat> vx(static_cast<size_t>(count));
        std::vector<jfloat> vy(static_cast<size_t>(count));
        std::vector<jfloat> vz(static_cast<size_t>(count));
        env->GetLongArrayRegion(timestamps, 0, count, ts.data());
        env->GetFloatArrayRegion(x, 0, count, vx.data());
        env->GetFloatArrayRegion(y, 0, count, vy.data());
        env->GetFloatArrayRegion(z, 0, count, vz.data());
        for (jint i = 0; i < count; ++i)
            samples[static_cast<size_t>(i)] = {ts[static_cast<size_t>(i)],
                                               vx[static_cast<size_t>(i)],
                                               vy[static_cast<size_t>(i)],
                                               vz[static_cast<size_t>(i)]};
        writer->writeGyro(samples.data(), samples.size());
    } catch (const std::exception& e) {
        throwJava(env, "java/io/IOException", e.what());
    } catch (...) {
        throwJava(env, "java/io/IOException", "mcraw gyro write failed");
    }
}

JNIEXPORT void JNICALL
Java_com_particlesdevs_photoncamera_util_McrawWriter_nativeClose(JNIEnv* env, jclass, jlong handle) {
    auto* writer = reinterpret_cast<mediacinemaraw::ContainerWriter*>(handle);
    if (!writer) return;
    try {
        writer->close();
    } catch (const std::exception& e) {
        throwJava(env, "java/io/IOException", e.what());
    } catch (...) {
        throwJava(env, "java/io/IOException", "mcraw finalize failed");
    }
    delete writer;
}

} // extern "C"
