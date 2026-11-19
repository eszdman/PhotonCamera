#define TINY_DNG_LOADER_IMPLEMENTATION
#define STB_IMAGE_IMPLEMENTATION
#include "tinydng/tiny_dng_loader.h"
#include <jni.h>
#include <sstream>
#include <iomanip>
#include <memory>

namespace {
struct Frame { tinydng::DNGImage image; };
void fail(JNIEnv *env, const std::string &s) {
    env->ThrowNew(env->FindClass("java/io/IOException"), s.c_str());
}
template<class T> void array(std::ostringstream &s, const T *v, int n) {
    s << '[';
    for (int i = 0; i < n; ++i) { if (i) s << ','; s << v[i]; }
    s << ']';
}
}
extern "C" JNIEXPORT jlong JNICALL
Java_com_particlesdevs_photoncamera_processing_opengl_TinyDng_open(JNIEnv *env, jclass, jstring path) {
    const char *name = env->GetStringUTFChars(path, nullptr);
    std::string filename(name);
    env->ReleaseStringUTFChars(path, name);
    try {
        std::vector<tinydng::DNGImage> images;
        std::vector<tinydng::FieldInfo> fields;
        std::string warn, err;
        if (!tinydng::LoadDNG(filename.c_str(), fields, &images, &warn, &err)) {
            fail(env, "tinydng: " + err); return 0;
        }
        for (auto &im : images) {
            if (im.samples_per_pixel != 1 || im.cfa_pattern_dim != 2 || im.bits_per_sample != 16) continue;
            if (im.width <= 0 || im.height <= 0 || im.data.size() != size_t(im.width)*im.height*2) continue;
            auto frame = std::unique_ptr<Frame>(new Frame);
            frame->image = std::move(im);
            return reinterpret_cast<jlong>(frame.release());
        }
        fail(env, "Replay requires a decoded 16-bit 2x2 Bayer DNG");
    } catch (const std::exception &e) { fail(env, e.what()); }
    return 0;
}
extern "C" JNIEXPORT jobject JNICALL
Java_com_particlesdevs_photoncamera_processing_opengl_TinyDng_pixels(JNIEnv *env, jclass, jlong handle) {
    auto &im = reinterpret_cast<Frame*>(handle)->image;
    return env->NewDirectByteBuffer(im.data.data(), im.data.size());
}
extern "C" JNIEXPORT jstring JNICALL
Java_com_particlesdevs_photoncamera_processing_opengl_TinyDng_metadata(JNIEnv *env, jclass, jlong handle) {
    auto &im = reinterpret_cast<Frame*>(handle)->image;
    std::ostringstream s; s << std::setprecision(17);
    s << "{\"width\":" << im.width << ",\"height\":" << im.height << ",\"black\":";
    array(s, im.black_level, 4); s << ",\"white\":" << im.white_level[0] << ",\"cfa\":";
    array(s, &im.cfa_pattern[0][0], 4); s << ",\"neutral\":";
    array(s, im.as_shot_neutral, 3); s << ",\"orientation\":" << im.orientation << '}';
    return env->NewStringUTF(s.str().c_str());
}
extern "C" JNIEXPORT void JNICALL
Java_com_particlesdevs_photoncamera_processing_opengl_TinyDng_destroy(JNIEnv*, jclass, jlong handle) {
    delete reinterpret_cast<Frame*>(handle);
}
