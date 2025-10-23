#include <jni.h>
#include <string>

extern "C" JNIEXPORT jstring JNICALL
Java_com_saikou_sozo_1tv_utils_Security_getApiKey(JNIEnv* env, jobject) {
    std::string apiKey = API_KEY;
    return env->NewStringUTF(apiKey.c_str());
}
