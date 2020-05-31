#include <jni.h>
#include <string>
#include <android/log.h>
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, "photon_native", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "photon_native", __VA_ARGS__)
extern "C"
JNIEXPORT void JNICALL
Java_com_eszdman_photoncamera_Wrapper_Test(JNIEnv *env, jclass clazz) {
    LOGD("Yeah, working");
}
extern "C"
JNIEXPORT jobject JNICALL
Java_com_eszdman_photoncamera_Wrapper_ProcessOpenCL(JNIEnv *env, jclass clazz, jobject in) {
    // TODO: implement ProcessOpenCL()
    
}