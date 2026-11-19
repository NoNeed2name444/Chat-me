/*
 * Sorta's add-on engine, as Kotlin calls it (S07b): AddonNative's functions
 * over the shared host (addons/engine/host/sorta_host.h).
 *
 * Every text that may hold more than ASCII crosses as bytes (a byte[] of
 * UTF-8), never as a Java string, whose JNI form is modified UTF-8. A runtime
 * is made, used and freed on one thread, the add-on's own, so the JNIEnv of
 * the call in progress is the one the host's callback uses.
 */
#include <jni.h>
#include <stdlib.h>
#include <string.h>

#include <stdint.h>
#include "sorta_host.h"

#define EXPORT JNIEXPORT __attribute__((visibility("default")))
#define NAME(f) Java_com_cramdown_sorta_addons_AddonNative_##f

typedef struct {
    sa_runtime *rt;
    JNIEnv *env;     /* the entry in progress, on the runtime's own thread */
    jobject host;    /* a global reference to the AddonNative.Host */
    jmethodID ask;   /* byte[] ask(byte[] name, byte[] args) */
} bridge;

static void host_call(void *opaque, sa_reply *reply, const char *name, const char *args, size_t len)
{
    bridge *b = opaque;
    JNIEnv *env = b ? b->env : NULL;
    if (!env || !b->host) {
        sa_reply_error(reply, "api.unknown");
        return;
    }
    /* the name crosses as bytes like every other text: NewStringUTF takes modified UTF-8 only, and
     * must never be given text an add-on had a hand in. The host has already checked it (lowercase
     * ASCII letters and dots), and Kotlin checks it again. */
    size_t name_len = strlen(name);
    jbyteArray jname = (*env)->NewByteArray(env, (jsize)name_len);
    jbyteArray jargs = (*env)->NewByteArray(env, (jsize)len);
    if (!jname || !jargs) {
        (*env)->ExceptionClear(env);
        if (jname)
            (*env)->DeleteLocalRef(env, jname);
        if (jargs)
            (*env)->DeleteLocalRef(env, jargs);
        sa_reply_error(reply, "limit.memory");
        return;
    }
    (*env)->SetByteArrayRegion(env, jname, 0, (jsize)name_len, (const jbyte *)name);
    (*env)->SetByteArrayRegion(env, jargs, 0, (jsize)len, (const jbyte *)args);
    jbyteArray out = (*env)->CallObjectMethod(env, b->host, b->ask, jname, jargs);
    (*env)->DeleteLocalRef(env, jname);
    (*env)->DeleteLocalRef(env, jargs);
    if ((*env)->ExceptionCheck(env) || !out) {
        (*env)->ExceptionClear(env);
        sa_reply_error(reply, "api.args");
        return;
    }
    jsize n = (*env)->GetArrayLength(env, out);
    char *text = malloc((size_t)n + 1);
    if (!text) {
        (*env)->DeleteLocalRef(env, out);
        sa_reply_error(reply, "limit.memory");
        return;
    }
    (*env)->GetByteArrayRegion(env, out, 0, n, (jbyte *)text);
    text[n] = 0;
    (*env)->DeleteLocalRef(env, out);
    if (n > 0 && text[0] == '!')
        sa_reply_error(reply, text + 1);
    else
        sa_reply_ok(reply, text, (size_t)n);
    free(text);
}

static jbyteArray bytes_of(JNIEnv *env, const char *p, size_t n)
{
    jbyteArray a = (*env)->NewByteArray(env, (jsize)n);
    if (a)
        (*env)->SetByteArrayRegion(env, a, 0, (jsize)n, (const jbyte *)p);
    return a;
}

static char *copy_bytes(JNIEnv *env, jbyteArray a, size_t *len)
{
    jsize n = a ? (*env)->GetArrayLength(env, a) : 0;
    char *p = malloc((size_t)n + 1);
    if (!p)
        return NULL;
    if (n)
        (*env)->GetByteArrayRegion(env, a, 0, n, (jbyte *)p);
    p[n] = 0;
    if (len)
        *len = (size_t)n;
    return p;
}

/* long make(Host host, byte[] code): 0 and the code (ASCII, NUL-ended) when the host makes none */
EXPORT jlong JNICALL NAME(make)(JNIEnv *env, jclass cls, jobject host, jbyteArray code_out)
{
    char code[64] = "";
    bridge *b = calloc(1, sizeof *b);
    if (!b)
        return 0;
    if (host) {
        jclass hc = (*env)->GetObjectClass(env, host);
        b->ask = (*env)->GetMethodID(env, hc, "ask", "([B[B)[B");
        b->host = (*env)->NewGlobalRef(env, host);
    }
    b->env = env;
    b->rt = sa_new(host_call, b, code, sizeof code);
    b->env = NULL;
    if (!b->rt) {
        if (code_out && (*env)->GetArrayLength(env, code_out) >= (jsize)sizeof code)
            (*env)->SetByteArrayRegion(env, code_out, 0, (jsize)sizeof code, (const jbyte *)code);
        if (b->host)
            (*env)->DeleteGlobalRef(env, b->host);
        free(b);
        return 0;
    }
    return (jlong)(intptr_t)b;
}

EXPORT void JNICALL NAME(free)(JNIEnv *env, jclass cls, jlong handle)
{
    bridge *b = (bridge *)(intptr_t)handle;
    if (!b)
        return;
    b->env = env;
    sa_free(b->rt);
    if (b->host)
        (*env)->DeleteGlobalRef(env, b->host);
    free(b);
}

/* byte[] load(long, byte[][] names, byte[][] texts, byte[] main): null when loaded, else the code */
EXPORT jbyteArray JNICALL NAME(load)(JNIEnv *env, jclass cls, jlong handle, jobjectArray names, jobjectArray texts,
                                     jbyteArray main_name)
{
    bridge *b = (bridge *)(intptr_t)handle;
    char code[64] = "limit.memory";
    jsize count = (*env)->GetArrayLength(env, names);
    char **n = calloc((size_t)count + 1, sizeof *n);
    char **t = calloc((size_t)count + 1, sizeof *t);
    size_t *l = calloc((size_t)count + 1, sizeof *l);
    char *m = copy_bytes(env, main_name, NULL);
    int ok = n && t && l && m;
    for (jsize i = 0; ok && i < count; i++) {
        jbyteArray jn = (*env)->GetObjectArrayElement(env, names, i);
        jbyteArray jt = (*env)->GetObjectArrayElement(env, texts, i);
        n[i] = copy_bytes(env, jn, NULL);
        t[i] = copy_bytes(env, jt, &l[i]);
        (*env)->DeleteLocalRef(env, jn);
        (*env)->DeleteLocalRef(env, jt);
        ok = n[i] && t[i];
    }
    int r = -1;
    if (ok) {
        b->env = env;
        r = sa_load(b->rt, (int)count, (const char *const *)n, (const char *const *)t, l, m, code, sizeof code);
        b->env = NULL;
    }
    for (jsize i = 0; n && t && i < count; i++) {
        free(n[i]);
        free(t[i]);
    }
    free(n);
    free(t);
    free(l);
    free(m);
    return r == 0 ? NULL : bytes_of(env, code, strlen(code));
}

/* byte[] call(long, String kind, byte[] id, byte[] input, int runMs, int wallMs): the envelope, or null */
EXPORT jbyteArray JNICALL NAME(call)(JNIEnv *env, jclass cls, jlong handle, jstring kind, jbyteArray id, jbyteArray input,
                                     jint run_ms, jint wall_ms)
{
    bridge *b = (bridge *)(intptr_t)handle;
    const char *k = (*env)->GetStringUTFChars(env, kind, NULL);
    size_t in_len = 0, out_len = 0;
    char *i = copy_bytes(env, id, NULL);
    char *in = copy_bytes(env, input, &in_len);
    jbyteArray result = NULL;
    if (k && i && in) {
        b->env = env;
        char *out = sa_call(b->rt, k, i, in, in_len, run_ms, wall_ms, &out_len);
        b->env = NULL;
        if (out) {
            result = bytes_of(env, out, out_len);
            sa_string_free(out);
        }
    }
    if (k)
        (*env)->ReleaseStringUTFChars(env, kind, k);
    free(i);
    free(in);
    return result;
}

EXPORT void JNICALL NAME(stop)(JNIEnv *env, jclass cls, jlong handle)
{
    bridge *b = (bridge *)(intptr_t)handle;
    if (b)
        sa_stop(b->rt);
}

EXPORT jboolean JNICALL NAME(spent)(JNIEnv *env, jclass cls, jlong handle)
{
    bridge *b = (bridge *)(intptr_t)handle;
    return b && sa_spent(b->rt) ? JNI_TRUE : JNI_FALSE;
}

EXPORT jdouble JNICALL NAME(lastRunMs)(JNIEnv *env, jclass cls, jlong handle)
{
    bridge *b = (bridge *)(intptr_t)handle;
    return b ? sa_last_run_ms(b->rt) : 0;
}

EXPORT jstring JNICALL NAME(engineVersion)(JNIEnv *env, jclass cls)
{
    return (*env)->NewStringUTF(env, sa_engine_version());
}

EXPORT jlong JNICALL NAME(currentStackSize)(JNIEnv *env, jclass cls)
{
    return (jlong)sa_current_stack_size();
}

EXPORT jboolean JNICALL NAME(isMainThread)(JNIEnv *env, jclass cls)
{
    return sa_is_main_thread() ? JNI_TRUE : JNI_FALSE;
}

/* byte[] bytecodeOf(byte[] source): what JS_WriteObject gives for a module (the bytecode case) */
EXPORT jbyteArray JNICALL NAME(bytecodeOf)(JNIEnv *env, jclass cls, jbyteArray source)
{
    size_t len = 0, out_len = 0;
    char *s = copy_bytes(env, source, &len);
    if (!s)
        return NULL;
    unsigned char *p = sa_bytecode_of(s, len, &out_len);
    free(s);
    if (!p)
        return NULL;
    jbyteArray a = bytes_of(env, (const char *)p, out_len);
    free(p);
    return a;
}
