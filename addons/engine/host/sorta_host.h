/*
 * Sorta's add-on host: the one C file around QuickJS-ng that the iOS app,
 * the Android app and the kit's harness all build (S07, section 2).
 *
 * One sa_runtime per add-on. Each is made, used and freed on one thread that
 * Sorta made for it with a 4 MB stack, never the main thread: every entry
 * checks that and calls JS_UpdateStackTop first. Add-on code is compiled from
 * source text in a separate compile context, written with JS_WriteObject and
 * read into the add-on's context, which has no eval.
 *
 * Results and errors come back as canonical JSON (keys sorted, no spaces):
 *   {"ok":true,"value":...}
 *   {"code":"<code>","message":"<text>","ok":false}
 */
#ifndef SORTA_HOST_H
#define SORTA_HOST_H

#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

/* Section 6's limits. */
#define SA_MEMORY_LIMIT ((size_t)32 << 20)   /* each runtime's heap */
#define SA_JS_STACK ((size_t)1 << 20)        /* JS_SetMaxStackSize */
#define SA_THREAD_STACK ((size_t)4 << 20)    /* the thread Sorta makes */
#define SA_JSON_LIMIT ((size_t)1 << 20)      /* each call's JSON, in and out */
#define SA_CARDS_MAX 50                      /* cards in one batch */
#define SA_LOAD_RUN_MS 500                   /* running the add-on's top level */
#define SA_LOAD_WALL_MS 10000

typedef struct sa_runtime sa_runtime;
typedef struct sa_reply sa_reply;

/*
 * The app's answer to one API call (name, its arguments as a JSON array),
 * made on the runtime's own thread while the add-on waits. It answers with
 * sa_reply_ok or sa_reply_error before returning. A reply of JSON text of
 * length 0 is `undefined` to the add-on.
 */
typedef void (*sa_host_fn)(void *opaque, sa_reply *reply, const char *name,
                           const char *args, size_t args_len);

void sa_reply_ok(sa_reply *reply, const char *json, size_t len);
void sa_reply_error(sa_reply *reply, const char *code);

/* Makes a runtime on the calling thread. NULL and a code when it can't. */
sa_runtime *sa_new(sa_host_fn host, void *opaque, char *code, size_t code_cap);
void sa_free(sa_runtime *rt);

/*
 * Loads an add-on: `count` files by package path, each UTF-8 source text,
 * and runs `main` as a module. 0, or -1 with a code: file.text, load.syntax,
 * load.module, limit.*, js.error, thread.
 */
int sa_load(sa_runtime *rt, int count, const char *const *names,
            const char *const *texts, const size_t *lens, const char *main,
            char *code, size_t code_cap);

/*
 * One call into the add-on: kind "command" (id: the command's id), "card"
 * (input: one idea), "settings" (input: the values) or "cards" (input: an
 * array of at most 50 ideas; run_ms is per card and wall_ms the batch).
 * Returns malloc'd canonical JSON (free it with sa_string_free).
 */
char *sa_call(sa_runtime *rt, const char *kind, const char *id,
              const char *input, size_t input_len, int run_ms, int wall_ms,
              size_t *out_len);

/* Stops the running call, from any thread (a revoke, the kill switch). */
void sa_stop(sa_runtime *rt);
/* Clears a stop once the call has ended, so the next call can run. */
void sa_unstop(sa_runtime *rt);
/* After a stop or a limit that may leave work behind, the runtime is spent:
 * free it and make a new one. */
int sa_spent(const sa_runtime *rt);
/* The last call's running time (wall time less net waits), in ms. */
double sa_last_run_ms(const sa_runtime *rt);

void sa_string_free(char *s);

/* Thread checks, for the entry assert and the tests. */
int sa_on_own_thread(const sa_runtime *rt);
int sa_is_main_thread(void);
size_t sa_current_stack_size(void);

/* For the conformance case that offers bytecode as a module (test only):
 * the bytes JS_WriteObject gives for a module compiled from `source`. */
unsigned char *sa_bytecode_of(const char *source, size_t len, size_t *out_len);

const char *sa_engine_version(void);

#ifdef __cplusplus
}
#endif

#endif
