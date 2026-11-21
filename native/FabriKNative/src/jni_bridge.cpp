#include <jni.h>

#include "fabrik.h"

namespace {

constexpr const char* kClassName = "au/edu/federation/fabrik/FabriKNatives";

jlong to_handle(void* p) noexcept { return reinterpret_cast<jlong>(p); }

template <class T>
T* from_handle(jlong handle) noexcept { return reinterpret_cast<T*>(handle); }

jstring jstring_from(JNIEnv* env, const char* text) {
    return env->NewStringUTF(text);
}
void with_floats(JNIEnv* env, jfloatArray array, jfloat*& out) {
    out = array ? env->GetFloatArrayElements(array, nullptr) : nullptr;
}

void release_floats(JNIEnv* env, jfloatArray array, jfloat* ptr) {
    if (array && ptr) env->ReleaseFloatArrayElements(array, ptr, JNI_ABORT);
}

jstring JNICALL version(JNIEnv* env, jclass) { return jstring_from(env, fk_version()); }
jstring JNICALL simd_backend(JNIEnv* env, jclass) { return jstring_from(env, fk_simd_backend()); }

jlong JNICALL chain_create(JNIEnv*, jclass, jint bones) {
    return to_handle(fk_chain_create(bones));
}

void JNICALL chain_destroy(JNIEnv*, jclass, jlong handle) {
    fk_chain_destroy(from_handle<FkChain>(handle));
}

void JNICALL chain_set_bones(JNIEnv* env, jclass, jlong handle, jfloatArray lengths,
                             jfloatArray starts, jfloatArray ends) {
    jfloat* l = nullptr; jfloat* s = nullptr; jfloat* e = nullptr;
    with_floats(env, lengths, l);
    with_floats(env, starts, s);
    with_floats(env, ends, e);
    fk_chain_set_bones(from_handle<FkChain>(handle), l, s, e);
    release_floats(env, lengths, l);
    release_floats(env, starts, s);
    release_floats(env, ends, e);
}

void JNICALL chain_get_bones(JNIEnv* env, jclass, jlong handle, jfloatArray starts, jfloatArray ends) {
    jfloat* s = nullptr; jfloat* e = nullptr;
    if (starts) s = env->GetFloatArrayElements(starts, nullptr);
    if (ends) e = env->GetFloatArrayElements(ends, nullptr);
    fk_chain_get_bones(from_handle<FkChain>(handle), s, e);
    release_floats(env, starts, s);
    release_floats(env, ends, e);
}

void JNICALL chain_set_params(JNIEnv*, jclass, jlong handle, jint iterations, jfloat min_change, jfloat threshold) {
    fk_chain_set_params(from_handle<FkChain>(handle), iterations, min_change, threshold);
}

void JNICALL chain_set_fixed_base(JNIEnv* env, jclass, jlong handle, jboolean fixed, jfloatArray base) {
    jfloat* b = nullptr;
    with_floats(env, base, b);
    fk_chain_set_fixed_base(from_handle<FkChain>(handle), fixed ? 1 : 0, b);
    release_floats(env, base, b);
}

jfloat JNICALL chain_solve(JNIEnv*, jclass, jlong handle, jfloat x, jfloat y, jfloat z) {
    return fk_chain_solve(from_handle<FkChain>(handle), x, y, z);
}

jlong JNICALL batch_create(JNIEnv*, jclass, jint chains, jint bones, jlong config) {
    return to_handle(fk_batch_create(chains, bones, from_handle<FkChain>(config)));
}

void JNICALL batch_set_chain(JNIEnv* env, jclass, jlong handle, jint index,
                             jfloatArray lengths, jfloatArray starts, jfloatArray ends) {
    jfloat* l = nullptr; jfloat* s = nullptr; jfloat* e = nullptr;
    with_floats(env, lengths, l);
    with_floats(env, starts, s);
    with_floats(env, ends, e);
    fk_batch_set_chain(from_handle<FkBatch>(handle), index, l, s, e);
    release_floats(env, lengths, l);
    release_floats(env, starts, s);
    release_floats(env, ends, e);
}

void JNICALL batch_solve(JNIEnv* env, jclass, jlong handle, jfloatArray targets, jfloatArray out) {
    jfloat* t = nullptr; jfloat* o = nullptr;
    if (targets) t = env->GetFloatArrayElements(targets, nullptr);
    if (out) o = env->GetFloatArrayElements(out, nullptr);
    fk_batch_solve(from_handle<FkBatch>(handle), t, o);
    release_floats(env, targets, t);
    release_floats(env, out, o);
}

JNINativeMethod kMethods[] = {
    {const_cast<char*>("version"), const_cast<char*>("()Ljava/lang/String;"), reinterpret_cast<void*>(&version)},
    {const_cast<char*>("simdBackend"), const_cast<char*>("()Ljava/lang/String;"), reinterpret_cast<void*>(&simd_backend)},
    {const_cast<char*>("chainCreate"), const_cast<char*>("(I)J"), reinterpret_cast<void*>(&chain_create)},
    {const_cast<char*>("chainDestroy"), const_cast<char*>("(J)V"), reinterpret_cast<void*>(&chain_destroy)},
    {const_cast<char*>("chainSetBones"), const_cast<char*>("(J[F[F[F)V"), reinterpret_cast<void*>(&chain_set_bones)},
    {const_cast<char*>("chainGetBones"), const_cast<char*>("(J[F[F)V"), reinterpret_cast<void*>(&chain_get_bones)},
    {const_cast<char*>("chainSetParams"), const_cast<char*>("(JIFF)V"), reinterpret_cast<void*>(&chain_set_params)},
    {const_cast<char*>("chainSetFixedBase"), const_cast<char*>("(JZ[F)V"), reinterpret_cast<void*>(&chain_set_fixed_base)},
    {const_cast<char*>("chainSolve"), const_cast<char*>("(JFFF)F"), reinterpret_cast<void*>(&chain_solve)},
    {const_cast<char*>("batchCreate"), const_cast<char*>("(IIJ)J"), reinterpret_cast<void*>(&batch_create)},
    {const_cast<char*>("batchSetChain"), const_cast<char*>("(JI[F[F[F)V"), reinterpret_cast<void*>(&batch_set_chain)},
    {const_cast<char*>("batchSolve"), const_cast<char*>("(J[F[F)V"), reinterpret_cast<void*>(&batch_solve)}
};

} // namespace

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_8) != JNI_OK) return JNI_ERR;
    jclass clazz = env->FindClass(kClassName);
    if (!clazz) return JNI_ERR;
    if (env->RegisterNatives(clazz, kMethods, sizeof(kMethods) / sizeof(kMethods[0])) < 0) return JNI_ERR;
    return JNI_VERSION_1_8;
}

