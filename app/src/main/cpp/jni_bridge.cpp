#include <jni.h>

#include <vector>

#include "gesture_engine.h"

using tapcounter::GestureConfig;
using tapcounter::GestureEngine;
using tapcounter::GestureEvent;

namespace {

GestureEngine* fromHandle(jlong h) { return reinterpret_cast<GestureEngine*>(h); }

// Flat triples: [keyCode, gestureId, timeMs, keyCode, gestureId, timeMs, ...]
jlongArray toArray(JNIEnv* env, const std::vector<GestureEvent>& events) {
    jlongArray arr = env->NewLongArray(static_cast<jsize>(events.size() * 3));
    if (arr == nullptr) return nullptr;
    std::vector<jlong> flat;
    flat.reserve(events.size() * 3);
    for (const auto& e : events) {
        flat.push_back(e.keyCode);
        flat.push_back(static_cast<jlong>(e.gesture));
        flat.push_back(e.timeMs);
    }
    if (!flat.empty()) env->SetLongArrayRegion(arr, 0, static_cast<jsize>(flat.size()), flat.data());
    return arr;
}

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_example_tapcounter_headset_NativeGestureEngine_nativeCreate(
    JNIEnv*, jobject, jlong debounceMs, jlong multiTapMs, jlong longPressMs) {
    GestureConfig c;
    c.debounceMs = debounceMs;
    c.multiTapWindowMs = multiTapMs;
    c.longPressMs = longPressMs;
    return reinterpret_cast<jlong>(new GestureEngine(c));
}

JNIEXPORT void JNICALL
Java_com_example_tapcounter_headset_NativeGestureEngine_nativeDestroy(JNIEnv*, jobject, jlong h) {
    delete fromHandle(h);
}

JNIEXPORT void JNICALL
Java_com_example_tapcounter_headset_NativeGestureEngine_nativeConfigure(
    JNIEnv*, jobject, jlong h, jlong debounceMs, jlong multiTapMs, jlong longPressMs) {
    GestureConfig c;
    c.debounceMs = debounceMs;
    c.multiTapWindowMs = multiTapMs;
    c.longPressMs = longPressMs;
    fromHandle(h)->configure(c);
}

JNIEXPORT jlongArray JNICALL
Java_com_example_tapcounter_headset_NativeGestureEngine_nativeOnKey(
    JNIEnv* env, jobject, jlong h, jint keyCode, jboolean down, jlong tMs) {
    return toArray(env, fromHandle(h)->onKey(keyCode, down == JNI_TRUE, tMs));
}

JNIEXPORT jlongArray JNICALL
Java_com_example_tapcounter_headset_NativeGestureEngine_nativeTick(
    JNIEnv* env, jobject, jlong h, jlong nowMs) {
    return toArray(env, fromHandle(h)->tick(nowMs));
}

JNIEXPORT jlong JNICALL
Java_com_example_tapcounter_headset_NativeGestureEngine_nativeNextDeadline(JNIEnv*, jobject, jlong h) {
    return fromHandle(h)->nextDeadlineMs();
}

JNIEXPORT void JNICALL
Java_com_example_tapcounter_headset_NativeGestureEngine_nativeReset(JNIEnv*, jobject, jlong h) {
    fromHandle(h)->reset();
}

}  // extern "C"
