#include <jni.h>
#include <android/log.h>
#include <thread>
#include <future>
JNIEnv *envg;
jstring getSignature(jobject MethodObj){
    jmethodID getAnnotatedReturnType = envg->GetMethodID(envg->GetObjectClass(MethodObj),"getAnnotatedReturnType","()Ljava/lang/reflect/AnnotatedType;");
    jobject AnnotatedType = envg->CallObjectMethod(MethodObj,getAnnotatedReturnType);
    jmethodID getType = envg->GetMethodID(envg->GetObjectClass(AnnotatedType),"getType","()Ljava/lang/reflect/Type;");
    jobject Type = envg->CallObjectMethod(AnnotatedType,getType);
    jmethodID typeName = envg->GetMethodID(envg->GetObjectClass(Type),"getTypeName","()Ljava/lang/String;");
    auto strName = (jstring)envg->CallObjectMethod(Type,typeName);
    return strName;
}
extern "C"
JNIEXPORT jobject JNICALL
Java_com_eszdman_photonbypass_ReflectBypass_getDeclaredField(JNIEnv *env, jclass clazz, jclass obj,
                                                             jstring name,jstring sig) {
    auto fieldid = env->GetFieldID(obj,env->GetStringUTFChars(name, nullptr),env->GetStringUTFChars(sig, nullptr));
    return env->GetObjectField(obj,fieldid);
}extern "C"
JNIEXPORT jclass JNICALL
Java_com_eszdman_photonbypass_ReflectBypass_findClass(JNIEnv *env, jclass clazz, jstring name) {
    return env->FindClass(env->GetStringUTFChars(name,nullptr));
}