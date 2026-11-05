#include <android/log.h>
#include <jni.h>
#include <chrono>
#include <string>
#include <vector>

#include "whisper.h"

#define LOG_TAG "whisper_jni"

namespace {

jobject buildNativeSegments(
    JNIEnv *env,
    const std::vector<int64_t> &startMs,
    const std::vector<int64_t> &endMs,
    const std::vector<std::string> &texts) {
    jclass resultClass = env->FindClass("com/easyradio/core/transcription/NativeSegments");
    jmethodID ctor = env->GetMethodID(resultClass, "<init>", "([J[J[Ljava/lang/String;)V");

    jsize n = static_cast<jsize>(startMs.size());

    jlongArray startArray = env->NewLongArray(n);
    jlongArray endArray = env->NewLongArray(n);
    env->SetLongArrayRegion(startArray, 0, n, reinterpret_cast<const jlong *>(startMs.data()));
    env->SetLongArrayRegion(endArray, 0, n, reinterpret_cast<const jlong *>(endMs.data()));

    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray textArray = env->NewObjectArray(n, stringClass, nullptr);
    for (jsize i = 0; i < n; i++) {
        jstring text = env->NewStringUTF(texts[i].c_str());
        env->SetObjectArrayElement(textArray, i, text);
        env->DeleteLocalRef(text);
    }

    jobject result = env->NewObject(resultClass, ctor, startArray, endArray, textArray);

    env->DeleteLocalRef(startArray);
    env->DeleteLocalRef(endArray);
    env->DeleteLocalRef(textArray);
    env->DeleteLocalRef(stringClass);
    env->DeleteLocalRef(resultClass);

    return result;
}

} // namespace

extern "C" JNIEXPORT jobject JNICALL
Java_com_easyradio_core_transcription_WhisperNative_transcribe(
    JNIEnv *env,
    jobject /* thiz */,
    jstring modelPath,
    jfloatArray pcm,
    jint sampleCount,
    jint numThreads) {
    const auto initStart = std::chrono::steady_clock::now();
    const char *modelPathChars = env->GetStringUTFChars(modelPath, nullptr);
    struct whisper_context_params cparams = whisper_context_default_params();
    struct whisper_context *ctx = whisper_init_from_file_with_params(modelPathChars, cparams);
    env->ReleaseStringUTFChars(modelPath, modelPathChars);
    const auto initEnd = std::chrono::steady_clock::now();
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "whisper_init took %lld ms",
                         (long long) std::chrono::duration_cast<std::chrono::milliseconds>(initEnd - initStart).count());

    if (ctx == nullptr) {
        return buildNativeSegments(env, {}, {}, {});
    }

    jfloat *pcmData = env->GetFloatArrayElements(pcm, nullptr);

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.translate = false;
    params.language = "en";
    params.n_threads = numThreads > 0 ? numThreads : 4;
    params.no_context = true;

    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "whisper_full starting: sampleCount=%d n_threads=%d",
                         sampleCount, params.n_threads);
    const auto fullStart = std::chrono::steady_clock::now();
    whisper_full(ctx, params, pcmData, sampleCount);
    const auto fullEnd = std::chrono::steady_clock::now();
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "whisper_full took %lld ms for %.1fs of audio",
                         (long long) std::chrono::duration_cast<std::chrono::milliseconds>(fullEnd - fullStart).count(),
                         sampleCount / 16000.0);

    env->ReleaseFloatArrayElements(pcm, pcmData, JNI_ABORT);

    std::vector<int64_t> startMs;
    std::vector<int64_t> endMs;
    std::vector<std::string> texts;

    int n_segments = whisper_full_n_segments(ctx);
    startMs.reserve(n_segments);
    endMs.reserve(n_segments);
    texts.reserve(n_segments);

    for (int i = 0; i < n_segments; i++) {
        // whisper.cpp reports segment times in centiseconds (10ms units).
        startMs.push_back(whisper_full_get_segment_t0(ctx, i) * 10);
        endMs.push_back(whisper_full_get_segment_t1(ctx, i) * 10);
        texts.emplace_back(whisper_full_get_segment_text(ctx, i));
    }

    whisper_free(ctx);

    return buildNativeSegments(env, startMs, endMs, texts);
}
