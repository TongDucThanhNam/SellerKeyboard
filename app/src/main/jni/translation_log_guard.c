#include <android/log.h>
#include <dlfcn.h>
#include <jni.h>
#include <stdint.h>

#define UNSUPPORTED_PRIORITY INT32_MIN

typedef int32_t (*set_minimum_priority_fn)(int32_t priority);

JNIEXPORT jboolean JNICALL
Java_com_terasumi_sellerkeyboard_translation_NativeTranslationLogGuard_nativeIsSupported(
    JNIEnv *env,
    jobject instance
) {
    (void)env;
    (void)instance;
    void *handle = dlopen("liblog.so", RTLD_NOW | RTLD_LOCAL);
    if (handle == NULL) {
        return JNI_FALSE;
    }
    void *function = dlsym(handle, "__android_log_set_minimum_priority");
    dlclose(handle);
    return function != NULL ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_terasumi_sellerkeyboard_translation_NativeTranslationLogGuard_nativeBegin(
    JNIEnv *env,
    jobject instance
) {
    (void)env;
    (void)instance;
    void *handle = dlopen("liblog.so", RTLD_NOW | RTLD_LOCAL);
    if (handle == NULL) {
        return UNSUPPORTED_PRIORITY;
    }
    set_minimum_priority_fn function =
        (set_minimum_priority_fn)dlsym(handle, "__android_log_set_minimum_priority");
    int32_t previous_priority = function == NULL
        ? UNSUPPORTED_PRIORITY
        : function(ANDROID_LOG_SILENT);
    dlclose(handle);
    return previous_priority;
}

JNIEXPORT void JNICALL
Java_com_terasumi_sellerkeyboard_translation_NativeTranslationLogGuard_nativeRestore(
    JNIEnv *env,
    jobject instance,
    jint previous_priority
) {
    (void)env;
    (void)instance;
    if (previous_priority == UNSUPPORTED_PRIORITY) {
        return;
    }
    void *handle = dlopen("liblog.so", RTLD_NOW | RTLD_LOCAL);
    if (handle == NULL) {
        return;
    }
    set_minimum_priority_fn function =
        (set_minimum_priority_fn)dlsym(handle, "__android_log_set_minimum_priority");
    if (function != NULL) {
        function(previous_priority);
    }
    dlclose(handle);
}
