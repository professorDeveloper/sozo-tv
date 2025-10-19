#include <jni.h>
#include <string>

#ifndef API_KEY
#define API_KEY ""
#endif

extern "C" JNIEXPORT jstring JNICALL
Java_com_saikou_sozo_tv_utils_Security_getApiKey(JNIEnv* env, jobject ) {
    std::string apiKey = API_KEY;
    return env->NewStringUTF(apiKey.c_str());
}
