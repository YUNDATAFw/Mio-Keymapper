#include <jni.h>
#include <string>

extern "C" JNIEXPORT jstring JNICALL
Java_io_takamiya_mio_utils_NativeLib_stringFromJNI(
        JNIEnv *env,
        jobject /* this */) {
    std::string hello = "Hello from mio-native";
    return env->NewStringUTF(hello.c_str());
}
