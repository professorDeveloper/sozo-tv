#include <jni.h>
#include <string>

// Shu yerga API key ni bevosita yozamiz
#define API_KEY "eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiIzZjVlYWQ5OTIzNmRhNGU1YzUyNjA1NTJjYzI5NTQzYyIsIm5iZiI6MTY0OTcwMTIwMy44ODk5OTk5LCJzdWIiOiI2MjU0NzE1MzY3ZTBmNzM5YzFhMjIyMzQiLCJzY29wZXMiOlsiYXBpX3JlYWQiXSwidmVyc2lvbiI6MX0.s6vYAqE1eUVxpVRVRoG6BGwiq8BuI9mf0tBHCiCDl7s"
extern "C" JNIEXPORT jstring JNICALL
Java_com_saikou_sozo_1tv_utils_Security_getApiKey(JNIEnv *env, jobject) {
    std::string apiKey = API_KEY;
    return env->NewStringUTF(apiKey.c_str());
}
