/*
 * Sorta's add-on host (S07, sections 2 and 6). See sorta_host.h.
 *
 * Built three times, from this one file: into the iOS app, into the Android
 * app (android/app/src/main/cpp) and into the kit's harness
 * (addons/kit/harness/Makefile).
 */
#ifndef _GNU_SOURCE
#define _GNU_SOURCE
#endif
#include "sorta_host.h"

#include <pthread.h>
#include <stdatomic.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <unistd.h>
#if defined(__APPLE__)
#include <pthread.h>
#else
#include <sys/syscall.h>
#endif

#include "../quickjs-ng/quickjs.h"
#include "sorta_prelude.h"

enum { WHY_NONE = 0, WHY_STOP = 1, WHY_TIME = 2 };

struct sa_reply {
    char *data;
    size_t len;
    char code[320];   /* a code, then a space and its message (a host name) */
    int set;
};

struct sa_runtime {
    JSRuntime *jrt;
    JSContext *ctx;          /* the add-on's: no eval */
    JSContext *compile;      /* only while loading: has eval */
    JSValue dispatch, ok, failed;
    sa_host_fn host;
    void *opaque;
    pthread_t owner;
    atomic_int stop;
    int why;
    int in_call;
    int spent;
    int loading;
    int64_t start_ns, run_ns, wall_ns, waited_ns;
    double last_run_ms;
    char load_code[32];
    /* the package's files, while loading */
    int file_count;
    const char *const *names;
    const char *const *texts;
    const size_t *lens;
};

/* ---- time and threads ---- */

static int64_t now_ns(void)
{
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t)ts.tv_sec * 1000000000 + ts.tv_nsec;
}

int sa_is_main_thread(void)
{
#if defined(__APPLE__)
    return pthread_main_np() != 0;
#else
    return getpid() == (pid_t)syscall(SYS_gettid);
#endif
}

size_t sa_current_stack_size(void)
{
#if defined(__APPLE__)
    return pthread_get_stacksize_np(pthread_self());
#else
    pthread_attr_t attr;
    size_t size = 0;
    if (pthread_getattr_np(pthread_self(), &attr) != 0)
        return 0;
    pthread_attr_getstacksize(&attr, &size);
    pthread_attr_destroy(&attr);
    return size;
#endif
}

int sa_on_own_thread(const sa_runtime *rt)
{
    return rt && pthread_equal(pthread_self(), rt->owner) && !sa_is_main_thread();
}

const char *sa_engine_version(void)
{
    return JS_GetVersion();
}

/* ---- results ---- */

static void put_code(char *code, size_t cap, const char *what)
{
    if (code && cap) {
        strncpy(code, what, cap - 1);
        code[cap - 1] = 0;
    }
}

static char *copy_out(const char *s, size_t len, size_t *out_len)
{
    char *out = malloc(len + 1);
    if (!out)
        return NULL;
    memcpy(out, s, len);
    out[len] = 0;
    if (out_len)
        *out_len = len;
    return out;
}

static char *failure(const char *code, size_t *out_len)
{
    char buf[160];
    int n = snprintf(buf, sizeof buf, "{\"code\":\"%s\",\"message\":\"%s\",\"ok\":false}", code, code);
    return copy_out(buf, (size_t)n, out_len);
}

void sa_string_free(char *s)
{
    free(s);
}

void sa_reply_ok(sa_reply *reply, const char *json, size_t len)
{
    if (!reply || reply->set)
        return;
    reply->data = copy_out(json ? json : "", json ? len : 0, &reply->len);
    reply->set = 1;
}

void sa_reply_error(sa_reply *reply, const char *code)
{
    if (!reply || reply->set)
        return;
    put_code(reply->code, sizeof reply->code, code && *code ? code : "api.unknown");
    reply->set = 2;
}

/* ---- limits ---- */

static int interrupt(JSContext *ctx, void *opaque)
{
    sa_runtime *rt = opaque;
    (void)ctx;
    if (atomic_load(&rt->stop)) {
        rt->why = WHY_STOP;
        return 1;
    }
    if (!rt->in_call)
        return 0;
    int64_t t = now_ns(), wall = t - rt->start_ns;
    if (wall - rt->waited_ns > rt->run_ns || wall > rt->wall_ns) {
        rt->why = WHY_TIME;
        return 1;
    }
    return 0;
}

static void begin(sa_runtime *rt, int run_ms, int wall_ms)
{
    rt->why = WHY_NONE;
    rt->in_call = 1;
    rt->start_ns = now_ns();
    rt->run_ns = (int64_t)(run_ms > 0 ? run_ms : 1) * 1000000;
    rt->wall_ns = (int64_t)(wall_ms > 0 ? wall_ms : 1) * 1000000;
    rt->waited_ns = 0;
}

static void end(sa_runtime *rt)
{
    rt->in_call = 0;
    rt->last_run_ms = (double)(now_ns() - rt->start_ns - rt->waited_ns) / 1e6;
}

static int past_deadline(sa_runtime *rt)
{
    if (atomic_load(&rt->stop)) {
        rt->why = WHY_STOP;
        return 1;
    }
    return interrupt(rt->ctx, rt);
}

/* What stopped the call, as a code: the host's own stops first, then the
 * engine's limits by their errors, then the add-on's own error. */
static char *classify(sa_runtime *rt, JSValue reason, size_t *out_len)
{
    JSContext *ctx = rt->ctx;
    char *out = NULL;
    if (rt->why == WHY_STOP)
        return failure("stopped", out_len);
    if (rt->why == WHY_TIME)
        return failure("limit.time", out_len);
    if (JS_IsError(reason) || JS_IsUncatchableError(reason)) {
        JSValue m = JS_GetPropertyStr(ctx, reason, "message");
        const char *text = JS_IsException(m) ? NULL : JS_ToCString(ctx, m);
        const char *code = NULL;
        if (text) {
            if (!strcmp(text, "out of memory"))
                code = "limit.memory";
            else if (!strcmp(text, "Maximum call stack size exceeded"))
                code = "limit.stack";
            else if (!strcmp(text, "eval is not supported"))
                code = "eval";
            JS_FreeCString(ctx, text);
        }
        JS_FreeValue(ctx, m);
        if (code) {
            rt->spent = 1;
            return failure(code, out_len);
        }
    } else if (JS_IsNull(reason) || JS_IsUninitialized(reason)) {
        /* the engine could not even make its out-of-memory error */
        rt->spent = 1;
        return failure("limit.memory", out_len);
    }
    JSValue s = JS_Call(ctx, rt->failed, JS_UNDEFINED, 1, &reason);
    if (JS_IsException(s)) {
        JSValue again = JS_GetException(ctx);
        JS_FreeValue(ctx, again);
        rt->spent = 1;
        return failure(rt->why == WHY_TIME ? "limit.time" : "limit.memory", out_len);
    }
    size_t len;
    const char *text = JS_ToCStringLen(ctx, &len, s);
    if (text) {
        out = copy_out(text, len, out_len);
        JS_FreeCString(ctx, text);
    }
    JS_FreeValue(ctx, s);
    return out ? out : failure("limit.memory", out_len);
}

static char *pending_failure(sa_runtime *rt, size_t *out_len)
{
    JSValue e = JS_GetException(rt->ctx);
    char *out = classify(rt, e, out_len);
    JS_FreeValue(rt->ctx, e);
    return out;
}

/* Runs the job queue until `promise` settles, the queue empties, or a limit.
 * Returns the promise's state, or -2 on an exception (left pending). */
static int settle(sa_runtime *rt, JSValueConst promise)
{
    for (;;) {
        int state = JS_PromiseState(rt->ctx, promise);
        if (state != JS_PROMISE_PENDING)
            return state;
        if (past_deadline(rt))
            return -2;
        if (!JS_IsJobPending(rt->jrt))
            return JS_PROMISE_PENDING;
        JSContext *c;
        if (JS_ExecutePendingJob(rt->jrt, &c) < 0) {
            if (c != rt->ctx) {
                JSValue e = JS_GetException(c);
                JS_FreeValue(c, e);
                JS_ThrowInternalError(rt->ctx, "a job failed");
            }
            return -2;
        }
        if (rt->why)
            return -2;
    }
}

/* After a call has its answer: the jobs it left behind run now, inside the
 * same deadline; whatever is still queued when it passes spends the runtime,
 * so no work carries into the next call. */
static void drain(sa_runtime *rt)
{
    while (JS_IsJobPending(rt->jrt)) {
        JSContext *c;
        if (past_deadline(rt) || JS_ExecutePendingJob(rt->jrt, &c) < 0) {
            rt->spent = 1;
            JSValue e = JS_GetException(rt->ctx);
            JS_FreeValue(rt->ctx, e);
            return;
        }
    }
}

/* ---- the native call the prelude holds ---- */

static int valid_text(const unsigned char *s, size_t len)
{
    size_t i = 0;
    while (i < len) {
        unsigned char c = s[i];
        if (c == 0)
            return 0;
        if (c < 0x80) {
            i++;
            continue;
        }
        size_t n;
        uint32_t cp;
        if (c >= 0xC2 && c <= 0xDF) { n = 1; cp = c & 0x1F; }
        else if (c >= 0xE0 && c <= 0xEF) { n = 2; cp = c & 0x0F; }
        else if (c >= 0xF0 && c <= 0xF4) { n = 3; cp = c & 0x07; }
        else return 0;
        if (len - i <= n)
            return 0;
        for (size_t k = 1; k <= n; k++) {
            if ((s[i + k] & 0xC0) != 0x80)
                return 0;
            cp = (cp << 6) | (s[i + k] & 0x3F);
        }
        if ((n == 2 && cp < 0x800) || (n == 3 && (cp < 0x10000 || cp > 0x10FFFF)) ||
            (cp >= 0xD800 && cp <= 0xDFFF))
            return 0;
        i += n + 1;
    }
    return 1;
}

/* A call's name is one of the API's own: lowercase ASCII letters and dots, at
 * most 40. Nothing else reaches the app's side, whatever script hands in. */
static int valid_name(const char *s)
{
    size_t n = 0;
    for (; s[n]; n++) {
        if (n >= 40 || !((s[n] >= 'a' && s[n] <= 'z') || s[n] == '.'))
            return 0;
    }
    return n > 0;
}

static JSValue host_call(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv)
{
    sa_runtime *rt = JS_GetContextOpaque(ctx);
    (void)this_val;
    if (argc < 2 || !JS_IsString(argv[0]) || !JS_IsString(argv[1]))
        return JS_NewString(ctx, "!api.args");
    const char *name = JS_ToCString(ctx, argv[0]);
    if (!name)
        return JS_EXCEPTION;
    if (!valid_name(name)) {
        JS_FreeCString(ctx, name);
        return JS_NewString(ctx, "!api.unknown");
    }
    size_t len = 0;
    const char *args = JS_ToCStringLen(ctx, &len, argv[1]);
    if (!args) {
        JS_FreeCString(ctx, name);
        return JS_EXCEPTION;
    }
    /* the arguments are JSON text the prelude wrote; anything else is refused here */
    if (len <= SA_JSON_LIMIT && !valid_text((const unsigned char *)args, len)) {
        JS_FreeCString(ctx, args);
        JS_FreeCString(ctx, name);
        return JS_NewString(ctx, "!api.args");
    }
    sa_reply reply = { 0 };
    int64_t before = now_ns();
    if (len > SA_JSON_LIMIT)
        sa_reply_error(&reply, "limit.json");
    else
        rt->host(rt->opaque, &reply, name, args, len);
    if (!strcmp(name, "net.fetch"))
        rt->waited_ns += now_ns() - before;
    JS_FreeCString(ctx, args);
    JS_FreeCString(ctx, name);
    if (atomic_load(&rt->stop)) {
        free(reply.data);
        rt->why = WHY_STOP;
        JSValue err = JS_NewError(ctx);
        JS_SetUncatchableError(ctx, err);
        return JS_Throw(ctx, err);
    }
    JSValue out;
    if (reply.set == 1) {
        out = reply.len > SA_JSON_LIMIT ? JS_NewString(ctx, "!limit.json")
                                        : JS_NewStringLen(ctx, reply.data, reply.len);
    } else {
        char buf[330];
        snprintf(buf, sizeof buf, "!%s", reply.set == 2 ? reply.code : "api.unknown");
        out = JS_NewString(ctx, buf);
    }
    free(reply.data);
    return out;
}

/* ---- compiling without eval where add-ons run ---- */

/* Compiles source text in the compile context, writes it as bytecode and
 * reads that into the add-on's context: the only bytes ever read as code
 * are the ones written here, in memory, a moment before. */
static JSValue compile_into(sa_runtime *rt, const char *name, const char *text, size_t len, int module)
{
    JSContext *cctx = rt->compile;
    if (!valid_text((const unsigned char *)text, len)) {
        put_code(rt->load_code, sizeof rt->load_code, "file.text");
        return JS_ThrowSyntaxError(rt->ctx, "file.text");
    }
    char *source = malloc(len + 1);
    if (!source)
        return JS_ThrowOutOfMemory(rt->ctx);
    memcpy(source, text, len);
    source[len] = 0;
    int flags = (module ? JS_EVAL_TYPE_MODULE : JS_EVAL_TYPE_GLOBAL) | JS_EVAL_FLAG_COMPILE_ONLY | JS_EVAL_FLAG_STRICT;
    JSValue compiled = JS_Eval(cctx, source, len, name, flags);
    free(source);
    if (JS_IsException(compiled)) {
        JSValue e = JS_GetException(cctx);
        JS_FreeValue(cctx, e);
        if (!rt->load_code[0])
            put_code(rt->load_code, sizeof rt->load_code, rt->why == WHY_TIME ? "limit.time" : "load.syntax");
        return JS_ThrowSyntaxError(rt->ctx, "load.syntax");
    }
    size_t size = 0;
    uint8_t *bytes = JS_WriteObject(cctx, &size, compiled, JS_WRITE_OBJ_BYTECODE);
    JS_FreeValue(cctx, compiled);
    if (!bytes)
        return JS_ThrowOutOfMemory(rt->ctx);
    JSValue read = JS_ReadObject(rt->ctx, bytes, size, JS_READ_OBJ_BYTECODE);
    js_free(cctx, bytes);
    return read;
}

static int find_file(sa_runtime *rt, const char *name)
{
    for (int i = 0; i < rt->file_count; i++)
        if (!strcmp(rt->names[i], name))
            return i;
    return -1;
}

static JSModuleDef *load_module(JSContext *ctx, const char *name, void *opaque)
{
    sa_runtime *rt = opaque;
    size_t n = strlen(name);
    int i = rt->loading ? find_file(rt, name) : -1;
    if (i < 0 || n < 4 || strcmp(name + n - 3, ".js")) {
        put_code(rt->load_code, sizeof rt->load_code, "load.module");
        JS_ThrowReferenceError(ctx, "load.module");
        return NULL;
    }
    JSValue m = compile_into(rt, name, rt->texts[i], rt->lens[i], 1);
    if (JS_IsException(m))
        return NULL;
    JSModuleDef *def = JS_VALUE_GET_PTR(m);
    JS_FreeValue(ctx, m);
    return def;
}

/* ---- making and freeing a runtime ---- */

static int add_intrinsics(JSContext *ctx)
{
    /* the default set less eval, performance timers and DOMException */
    return JS_AddIntrinsicBaseObjects(ctx) || JS_AddIntrinsicDate(ctx) ||
           JS_AddIntrinsicRegExp(ctx) || JS_AddIntrinsicJSON(ctx) || JS_AddIntrinsicProxy(ctx) ||
           JS_AddIntrinsicMapSet(ctx) || JS_AddIntrinsicTypedArrays(ctx) ||
           JS_AddIntrinsicPromise(ctx) || JS_AddIntrinsicWeakRef(ctx) || JS_AddIntrinsicAToB(ctx);
}

static int enter(sa_runtime *rt)
{
    if (!sa_on_own_thread(rt))
        return 0;
    JS_UpdateStackTop(rt->jrt);
    return 1;
}

sa_runtime *sa_new(sa_host_fn host, void *opaque, char *code, size_t code_cap)
{
    if (sa_is_main_thread()) {
        put_code(code, code_cap, "thread");
        return NULL;
    }
    sa_runtime *rt = calloc(1, sizeof *rt);
    if (!rt) {
        put_code(code, code_cap, "limit.memory");
        return NULL;
    }
    rt->host = host;
    rt->opaque = opaque;
    rt->owner = pthread_self();
    rt->dispatch = rt->ok = rt->failed = JS_UNDEFINED;
    atomic_init(&rt->stop, 0);
    rt->jrt = JS_NewRuntime();
    if (!rt->jrt)
        goto fail;
    JS_SetRuntimeOpaque(rt->jrt, rt);
    JS_SetMemoryLimit(rt->jrt, SA_MEMORY_LIMIT);
    JS_SetMaxStackSize(rt->jrt, SA_JS_STACK);
    JS_UpdateStackTop(rt->jrt);
    JS_SetInterruptHandler(rt->jrt, interrupt, rt);
    JS_SetModuleLoaderFunc(rt->jrt, NULL, load_module, rt);
    rt->ctx = JS_NewContextRaw(rt->jrt);
    if (!rt->ctx || add_intrinsics(rt->ctx))
        goto fail;
    JS_SetContextOpaque(rt->ctx, rt);

    rt->compile = JS_NewContext(rt->jrt);
    if (!rt->compile)
        goto fail;
    begin(rt, SA_LOAD_RUN_MS, SA_LOAD_WALL_MS);
    JSValue fn = compile_into(rt, "<sorta>", sa_prelude, sizeof sa_prelude - 1, 0);
    JS_FreeContext(rt->compile);
    rt->compile = NULL;
    if (JS_IsException(fn))
        goto fail_js;
    JSValue setup = JS_EvalFunction(rt->ctx, fn);
    if (JS_IsException(setup))
        goto fail_js;
    JSValue native = JS_NewCFunction(rt->ctx, host_call, "call", 2);
    JSValue parts = JS_Call(rt->ctx, setup, JS_UNDEFINED, 1, &native);
    JS_FreeValue(rt->ctx, native);
    JS_FreeValue(rt->ctx, setup);
    if (JS_IsException(parts))
        goto fail_js;
    rt->dispatch = JS_GetPropertyUint32(rt->ctx, parts, 0);
    rt->ok = JS_GetPropertyUint32(rt->ctx, parts, 1);
    rt->failed = JS_GetPropertyUint32(rt->ctx, parts, 2);
    JS_FreeValue(rt->ctx, parts);
    end(rt);
    return rt;

fail_js: {
        JSValue e = JS_GetException(rt->ctx);
        JS_FreeValue(rt->ctx, e);
    }
fail:
    put_code(code, code_cap, "limit.memory");
    sa_free(rt);
    return NULL;
}

void sa_free(sa_runtime *rt)
{
    if (!rt)
        return;
    if (rt->ctx) {
        JS_FreeValue(rt->ctx, rt->dispatch);
        JS_FreeValue(rt->ctx, rt->ok);
        JS_FreeValue(rt->ctx, rt->failed);
        JS_FreeContext(rt->ctx);
    }
    if (rt->compile)
        JS_FreeContext(rt->compile);
    if (rt->jrt)
        JS_FreeRuntime(rt->jrt);
    free(rt);
}

void sa_stop(sa_runtime *rt)
{
    if (rt)
        atomic_store(&rt->stop, 1);
}

void sa_unstop(sa_runtime *rt)
{
    if (rt)
        atomic_store(&rt->stop, 0);
}

int sa_spent(const sa_runtime *rt)
{
    return !rt || rt->spent;
}

double sa_last_run_ms(const sa_runtime *rt)
{
    return rt ? rt->last_run_ms : 0;
}

/* ---- loading an add-on ---- */

/* A failed load's code: the loader's own (file.text, load.syntax,
 * load.module), else the failure's. */
static void code_of(sa_runtime *rt, const char *env, char *code, size_t cap)
{
    static const char head[] = "{\"code\":\"";
    if (rt->load_code[0]) {
        put_code(code, cap, rt->load_code);
    } else if (env && !strncmp(env, head, sizeof head - 1)) {
        char buf[64];
        const char *c = env + sizeof head - 1;
        size_t n = 0;
        while (c[n] && c[n] != '"' && n < sizeof buf - 1) {
            buf[n] = c[n];
            n++;
        }
        buf[n] = 0;
        put_code(code, cap, buf);
    } else {
        put_code(code, cap, "js.error");
    }
}

int sa_load(sa_runtime *rt, int count, const char *const *names, const char *const *texts,
            const size_t *lens, const char *main, char *code, size_t code_cap)
{
    if (!rt || !enter(rt)) {
        put_code(code, code_cap, "thread");
        return -1;
    }
    rt->load_code[0] = 0;
    rt->file_count = count;
    rt->names = names;
    rt->texts = texts;
    rt->lens = lens;
    rt->loading = 1;
    rt->compile = JS_NewContext(rt->jrt);
    int result = -1;
    begin(rt, SA_LOAD_RUN_MS, SA_LOAD_WALL_MS);
    int i = rt->compile ? find_file(rt, main) : -1;
    JSValue m = JS_UNDEFINED;
    if (!rt->compile) {
        put_code(code, code_cap, "limit.memory");
        goto done;
    }
    if (i < 0) {
        put_code(code, code_cap, "load.module");
        goto done;
    }
    m = compile_into(rt, main, texts[i], lens[i], 1);
    if (JS_IsException(m) || JS_ResolveModule(rt->ctx, m) < 0) {
        JS_FreeValue(rt->ctx, m);
        goto error;
    }
    JSValue p = JS_EvalFunction(rt->ctx, m);
    if (JS_IsException(p))
        goto error;
    int state = settle(rt, p);
    if (state == JS_PROMISE_FULFILLED) {
        result = 0;
        drain(rt);
    } else if (state == JS_PROMISE_REJECTED) {
        JSValue reason = JS_PromiseResult(rt->ctx, p);
        size_t len;
        char *env = classify(rt, reason, &len);
        JS_FreeValue(rt->ctx, reason);
        code_of(rt, env, code, code_cap);
        free(env);
    } else if (state == JS_PROMISE_PENDING) {
        put_code(code, code_cap, "load.pending");
    } else {
        JS_FreeValue(rt->ctx, p);
        goto error;
    }
    JS_FreeValue(rt->ctx, p);
    goto done;
error: {
        size_t len;
        char *env = pending_failure(rt, &len);
        code_of(rt, env, code, code_cap);
        free(env);
    }
done:
    end(rt);
    rt->loading = 0;
    rt->names = rt->texts = NULL;
    rt->lens = NULL;
    rt->file_count = 0;
    if (rt->compile) {
        JS_FreeContext(rt->compile);
        rt->compile = NULL;
    }
    if (result != 0)
        rt->spent = 1;
    return result;
}

/* ---- calls ---- */

static char *one_call(sa_runtime *rt, const char *kind, const char *id, const char *input,
                      size_t input_len, int run_ms, int wall_ms, size_t *out_len)
{
    JSContext *ctx = rt->ctx;
    char *out = NULL;
    begin(rt, run_ms, wall_ms);
    JSValue args[3] = { JS_NewString(ctx, kind), JS_NewString(ctx, id ? id : ""),
                        JS_NewStringLen(ctx, input, input_len) };
    JSValue p = JS_Call(ctx, rt->dispatch, JS_UNDEFINED, 3, args);
    JS_FreeValue(ctx, args[0]);
    JS_FreeValue(ctx, args[1]);
    JS_FreeValue(ctx, args[2]);
    if (JS_IsException(p)) {
        out = pending_failure(rt, out_len);
        goto done;
    }
    int state = settle(rt, p);
    if (state == JS_PROMISE_FULFILLED) {
        JSValue v = JS_PromiseResult(ctx, p);
        JSValue s = JS_Call(ctx, rt->ok, JS_UNDEFINED, 1, &v);
        JS_FreeValue(ctx, v);
        if (JS_IsException(s)) {
            out = pending_failure(rt, out_len);
        } else {
            size_t len;
            const char *text = JS_ToCStringLen(ctx, &len, s);
            if (text) {
                /* the prelude's ok() answers "!limit.json" for a result it stopped writing, and
                 * "!result.shape" for one that holds half a surrogate pair */
                if (!strcmp(text, "!result.shape"))
                    out = failure("result.shape", out_len);
                else
                    out = (len > SA_JSON_LIMIT || text[0] == '!') ? failure("limit.json", out_len)
                                                                  : copy_out(text, len, out_len);
                JS_FreeCString(ctx, text);
            }
            JS_FreeValue(ctx, s);
            if (!out)
                out = failure("limit.memory", out_len);
        }
        if (!rt->why)
            drain(rt);
    } else if (state == JS_PROMISE_REJECTED) {
        JSValue reason = JS_PromiseResult(ctx, p);
        out = classify(rt, reason, out_len);
        JS_FreeValue(ctx, reason);
        if (!rt->why)
            drain(rt);
    } else if (state == JS_PROMISE_PENDING) {
        out = failure("result.pending", out_len);
    } else {
        out = pending_failure(rt, out_len);
    }
    JS_FreeValue(ctx, p);
done:
    if (rt->why || JS_IsJobPending(rt->jrt))
        rt->spent = 1;
    end(rt);
    return out ? out : failure("limit.memory", out_len);
}

static char *cards(sa_runtime *rt, const char *input, size_t input_len, int per_ms, int batch_ms,
                   size_t *out_len)
{
    JSContext *ctx = rt->ctx;
    char *z = copy_out(input, input_len, NULL);
    if (!z)
        return failure("limit.memory", out_len);
    JSValue list = JS_ParseJSON(ctx, z, input_len, "<cards>");
    free(z);
    int64_t count = -1;
    if (JS_IsException(list) || !JS_IsArray(list) || JS_GetLength(ctx, list, &count) < 0) {
        JSValue e = JS_GetException(ctx);
        JS_FreeValue(ctx, e);
        JS_FreeValue(ctx, list);
        return failure("api.args", out_len);
    }
    if (count > SA_CARDS_MAX) {
        JS_FreeValue(ctx, list);
        return failure("limit.batch", out_len);
    }
    size_t cap = 64, len = 0;
    char *out = malloc(cap);
    if (!out) {
        JS_FreeValue(ctx, list);
        return failure("limit.memory", out_len);
    }
    len = (size_t)snprintf(out, cap, "{\"ok\":true,\"value\":[");
    int64_t started = now_ns();
    double ran = 0;
    for (int64_t i = 0; i < count; i++) {
        char *one = NULL;
        size_t one_len = 0;
        int left = batch_ms - (int)((now_ns() - started) / 1000000);
        if (left <= 0 || rt->spent) {
            one = failure("limit.batch", &one_len);
        } else {
            JSValue idea = JS_GetPropertyUint32(ctx, list, (uint32_t)i);
            JSValue text = JS_JSONStringify(ctx, idea, JS_UNDEFINED, JS_UNDEFINED);
            size_t tl = 0;
            const char *t = JS_IsException(text) ? NULL : JS_ToCStringLen(ctx, &tl, text);
            if (t) {
                int run = per_ms < left ? per_ms : left;
                one = one_call(rt, "card", "", t, tl, run, run, &one_len);
                ran += rt->last_run_ms;
                JS_FreeCString(ctx, t);
            } else {
                JSValue e = JS_GetException(ctx);
                JS_FreeValue(ctx, e);
                one = failure("api.args", &one_len);
            }
            JS_FreeValue(ctx, text);
            JS_FreeValue(ctx, idea);
        }
        if (!one) {
            free(out);
            JS_FreeValue(ctx, list);
            return failure("limit.memory", out_len);
        }
        if (len + one_len + 4 > cap) {
            while (len + one_len + 4 > cap)
                cap *= 2;
            char *grown = realloc(out, cap);
            if (!grown) {
                free(one);
                free(out);
                JS_FreeValue(ctx, list);
                return failure("limit.memory", out_len);
            }
            out = grown;
        }
        if (i > 0)
            out[len++] = ',';
        memcpy(out + len, one, one_len);
        len += one_len;
        free(one);
    }
    JS_FreeValue(ctx, list);
    memcpy(out + len, "]}", 3);
    len += 2;
    rt->last_run_ms = ran;
    if (len > SA_JSON_LIMIT) {
        free(out);
        return failure("limit.json", out_len);
    }
    if (out_len)
        *out_len = len;
    return out;
}

char *sa_call(sa_runtime *rt, const char *kind, const char *id, const char *input, size_t input_len,
              int run_ms, int wall_ms, size_t *out_len)
{
    if (!rt || !enter(rt))
        return failure("thread", out_len);
    if (rt->spent)
        return failure("engine.spent", out_len);
    if (atomic_load(&rt->stop))
        return failure("stopped", out_len);
    if (input_len > SA_JSON_LIMIT)
        return failure("limit.json", out_len);
    if (!strcmp(kind, "cards"))
        return cards(rt, input, input_len, run_ms, wall_ms, out_len);
    return one_call(rt, kind, id, input, input_len, run_ms, wall_ms, out_len);
}

/* ---- the bytecode case ---- */

unsigned char *sa_bytecode_of(const char *source, size_t len, size_t *out_len)
{
    JSRuntime *jrt = JS_NewRuntime();
    if (!jrt)
        return NULL;
    JS_UpdateStackTop(jrt);
    JSContext *ctx = JS_NewContext(jrt);
    unsigned char *out = NULL;
    if (ctx) {
        char *z = copy_out(source, len, NULL);
        JSValue v = z ? JS_Eval(ctx, z, len, "main.js", JS_EVAL_TYPE_MODULE | JS_EVAL_FLAG_COMPILE_ONLY)
                      : JS_EXCEPTION;
        free(z);
        if (!JS_IsException(v)) {
            size_t size = 0;
            uint8_t *bytes = JS_WriteObject(ctx, &size, v, JS_WRITE_OBJ_BYTECODE);
            if (bytes) {
                out = malloc(size);
                if (out) {
                    memcpy(out, bytes, size);
                    *out_len = size;
                }
                js_free(ctx, bytes);
            }
            JS_FreeValue(ctx, v);
        } else {
            JSValue e = JS_GetException(ctx);
            JS_FreeValue(ctx, e);
        }
        JS_FreeContext(ctx);
    }
    JS_FreeRuntime(jrt);
    return out;
}
