/*
 * The kit's harness (S07, section 8): Sorta's own add-on host and engine,
 * built for a laptop or a CI runner, with a stand-in Sorta (standin.js) that
 * keeps sample ideas in memory.
 *
 *   harness run <dir> --ideas <file> --command <id> [--grants a,b] [--input <json>]
 *   harness run <dir> --ideas <file> --card <card view id | idea id> [--grants a,b]
 *   harness conformance <dir>      prints every case; CI compares expected.txt
 *   harness hostile <dir>          each fixture stopped with its code, in time
 *   harness permissions <file>     the shared permission table
 *
 * Add-on code runs on a thread made here with a 4 MB stack, never on the
 * main thread, which keeps a heartbeat while it waits. The stand-in runs in a
 * second, trusted runtime on that same thread.
 */
#ifndef _GNU_SOURCE
#define _GNU_SOURCE
#endif
#include <dirent.h>
#include <pthread.h>
#include <stdatomic.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <time.h>

#include "../../engine/host/sorta_host.h"
#include "../../engine/quickjs-ng/quickjs.h"

#ifndef HARNESS_DIR
#define HARNESS_DIR "."
#endif

#define MAX_FILES 200
#define CODE_CAP 64

static int64_t now_ns(void)
{
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t)ts.tv_sec * 1000000000 + ts.tv_nsec;
}

static void die(const char *what, const char *detail)
{
    fprintf(stderr, "harness: %s%s%s\n", what, detail ? ": " : "", detail ? detail : "");
    exit(2);
}

static char *slurp(const char *path, size_t *len)
{
    FILE *f = fopen(path, "rb");
    if (!f)
        return NULL;
    size_t cap = 4096, n = 0;
    char *buf = malloc(cap + 1);
    for (;;) {
        if (n == cap) {
            cap *= 2;
            buf = realloc(buf, cap + 1);
        }
        size_t got = fread(buf + n, 1, cap - n, f);
        if (!got)
            break;
        n += got;
    }
    fclose(f);
    buf[n] = 0;
    if (len)
        *len = n;
    return buf;
}

static char *join_path(const char *a, const char *b)
{
    size_t n = strlen(a) + strlen(b) + 2;
    char *p = malloc(n);
    snprintf(p, n, "%s/%s", a, b);
    return p;
}

/* ---- the stand-in Sorta, in its own trusted runtime ---- */

static JSRuntime *srt;
static JSContext *sctx;
static JSValue sobj;

static void show_exception(void)
{
    JSValue e = JS_GetException(sctx);
    const char *m = JS_ToCString(sctx, e);
    JSValue st = JS_GetPropertyStr(sctx, e, "stack");
    const char *s = JS_IsUndefined(st) ? NULL : JS_ToCString(sctx, st);
    fprintf(stderr, "stand-in: %s\n%s\n", m ? m : "?", s ? s : "");
    exit(2);
}

/* Calls stand-in method `name` with string arguments; a malloc'd answer. */
static char *sv(const char *name, int argc, const char *const *argv, const size_t *lens, size_t *out_len)
{
    JS_UpdateStackTop(srt);
    JSValue args[4];
    for (int i = 0; i < argc; i++)
        args[i] = JS_NewStringLen(sctx, argv[i], lens ? lens[i] : strlen(argv[i]));
    JSValue fn = JS_GetPropertyStr(sctx, sobj, name);
    JSValue r = JS_Call(sctx, fn, sobj, argc, args);
    JS_FreeValue(sctx, fn);
    for (int i = 0; i < argc; i++)
        JS_FreeValue(sctx, args[i]);
    if (JS_IsException(r))
        show_exception();
    size_t n;
    const char *t = JS_ToCStringLen(sctx, &n, r);
    if (!t)
        show_exception();
    char *out = malloc(n + 1);
    memcpy(out, t, n);
    out[n] = 0;
    JS_FreeCString(sctx, t);
    JS_FreeValue(sctx, r);
    if (out_len)
        *out_len = n;
    return out;
}

static char *sv1(const char *name, const char *a)
{
    return sv(name, 1, &a, NULL, NULL);
}

static void standin_open(void)
{
    srt = JS_NewRuntime();
    JS_SetMaxStackSize(srt, (size_t)1 << 20);
    sctx = JS_NewContext(srt);
    char *path = join_path(HARNESS_DIR, "standin.js");
    size_t len;
    char *src = slurp(path, &len);
    if (!src)
        die("can't read", path);
    JS_UpdateStackTop(srt);
    sobj = JS_Eval(sctx, src, len, "standin.js", JS_EVAL_TYPE_GLOBAL | JS_EVAL_FLAG_STRICT);
    if (JS_IsException(sobj))
        show_exception();
    free(src);
    free(path);
}

static void standin_close(void)
{
    JS_FreeValue(sctx, sobj);
    JS_FreeContext(sctx);
    JS_FreeRuntime(srt);
}

/* A string field of a JSON object, through the trusted runtime; malloc'd or NULL. */
static char *json_field(const char *json, const char *path)
{
    JS_UpdateStackTop(srt);
    JSValue v = JS_ParseJSON(sctx, json, strlen(json), "<json>");
    if (JS_IsException(v))
        show_exception();
    char *out = NULL;
    char key[128];
    const char *p = path;
    while (*p && !JS_IsUndefined(v) && !JS_IsNull(v)) {
        size_t n = strcspn(p, ".");
        snprintf(key, sizeof key, "%.*s", (int)n, p);
        JSValue next = JS_GetPropertyStr(sctx, v, key);
        JS_FreeValue(sctx, v);
        v = next;
        p += n + (p[n] == '.');
    }
    if (!JS_IsUndefined(v) && !JS_IsNull(v)) {
        JSValue s = JS_IsString(v) ? JS_DupValue(sctx, v) : JS_JSONStringify(sctx, v, JS_UNDEFINED, JS_UNDEFINED);
        const char *t = JS_ToCString(sctx, s);
        if (t) {
            out = strdup(t);
            JS_FreeCString(sctx, t);
        }
        JS_FreeValue(sctx, s);
    }
    JS_FreeValue(sctx, v);
    return out;
}

/* Item i of a JSON array as JSON text (a string item as itself when `raw`);
 * NULL past the end. */
static char *json_item(const char *json, int i, int raw)
{
    JS_UpdateStackTop(srt);
    JSValue v = JS_ParseJSON(sctx, json, strlen(json), "<json>");
    if (JS_IsException(v))
        show_exception();
    JSValue item = JS_GetPropertyUint32(sctx, v, (uint32_t)i);
    char *out = NULL;
    if (!JS_IsUndefined(item)) {
        JSValue s = raw && JS_IsString(item) ? JS_DupValue(sctx, item)
                                             : JS_JSONStringify(sctx, item, JS_UNDEFINED, JS_UNDEFINED);
        const char *t = JS_ToCString(sctx, s);
        out = strdup(t);
        JS_FreeCString(sctx, t);
        JS_FreeValue(sctx, s);
    }
    JS_FreeValue(sctx, item);
    JS_FreeValue(sctx, v);
    return out;
}

/* The app's side of each API call: the stand-in answers it. */
static void host(void *opaque, sa_reply *reply, const char *name, const char *args, size_t len)
{
    (void)opaque;
    const char *argv[2] = { name, args };
    size_t lens[2] = { strlen(name), len };
    size_t n;
    char *r = sv("handle", 2, argv, lens, &n);
    if (n > 0 && r[0] == '!')
        sa_reply_error(reply, r + 1);
    else
        sa_reply_ok(reply, r, n);
    free(r);
}

/* ---- packages ---- */

typedef struct {
    int n;
    char *names[MAX_FILES];
    char *texts[MAX_FILES];
    size_t lens[MAX_FILES];
    char main[256];
} package;

static void add_dir(package *p, const char *root, const char *rel, int depth)
{
    char *dir = rel[0] ? join_path(root, rel) : strdup(root);
    DIR *d = opendir(dir);
    if (!d)
        die("can't open", dir);
    struct dirent *e;
    while ((e = readdir(d))) {
        if (e->d_name[0] == '.')
            continue;
        char *name = rel[0] ? join_path(rel, e->d_name) : strdup(e->d_name);
        char *full = join_path(root, name);
        struct stat st;
        if (stat(full, &st) == 0 && S_ISDIR(st.st_mode)) {
            if (depth < 2 && strcmp(name, "tests"))
                add_dir(p, root, name, depth + 1);
            free(name);
        } else if (p->n < MAX_FILES) {
            p->names[p->n] = name;
            p->texts[p->n] = slurp(full, &p->lens[p->n]);
            p->n++;
        } else {
            free(name);
        }
        free(full);
    }
    closedir(d);
    free(dir);
}

static void read_package(package *p, const char *dir)
{
    memset(p, 0, sizeof *p);
    add_dir(p, dir, "", 1);
    snprintf(p->main, sizeof p->main, "main.js");
    for (int i = 0; i < p->n; i++) {
        if (!strcmp(p->names[i], "manifest.json")) {
            char *m = json_field(p->texts[i], "main");
            if (m)
                snprintf(p->main, sizeof p->main, "%s", m);
            free(m);
        }
    }
}

static void free_package(package *p)
{
    for (int i = 0; i < p->n; i++) {
        free(p->names[i]);
        free(p->texts[i]);
    }
    p->n = 0;
}

/* A case's own version of a file: ["text", ...], ["hex", ...] or ["bytecodeOf", source]. */
static void override_file(package *p, const char *name, const char *how, char *value)
{
    char *text = NULL, *hex = NULL, *src = NULL;
    if (!strcmp(how, "text"))
        text = value;
    else if (!strcmp(how, "hex"))
        hex = value;
    else if (!strcmp(how, "bytecodeOf"))
        src = value;
    else
        die("unknown file override", how);
    char *bytes = NULL;
    size_t len = 0;
    if (text) {
        bytes = strdup(text);
        len = strlen(text);
    } else if (hex) {
        len = strlen(hex) / 2;
        bytes = malloc(len + 1);
        for (size_t i = 0; i < len; i++) {
            unsigned v;
            sscanf(hex + 2 * i, "%2x", &v);
            bytes[i] = (char)v;
        }
        bytes[len] = 0;
    } else if (src) {
        unsigned char *b = sa_bytecode_of(src, strlen(src), &len);
        if (!b)
            die("can't compile bytecode case", name);
        bytes = realloc(b, len + 1);
        bytes[len] = 0;
    }
    int i = 0;
    while (i < p->n && strcmp(p->names[i], name))
        i++;
    if (i == p->n) {
        p->names[i] = strdup(name);
        p->n++;
    } else {
        free(p->texts[i]);
    }
    p->texts[i] = bytes;
    p->lens[i] = len;
}

static sa_runtime *start(package *p, char *code)
{
    code[0] = 0;
    sa_runtime *rt = sa_new(host, NULL, code, CODE_CAP);
    if (!rt)
        return NULL;
    if (sa_load(rt, p->n, (const char *const *)p->names, (const char *const *)p->texts, p->lens, p->main, code,
                CODE_CAP) != 0) {
        sa_free(rt);
        return NULL;
    }
    return rt;
}

/* Section 6's limits for each kind of call. */
static void limits(const char *kind, int *run_ms, int *wall_ms)
{
    if (!strcmp(kind, "card")) {
        *run_ms = 20;
        *wall_ms = 20;
    } else if (!strcmp(kind, "cards")) {
        *run_ms = 20;
        *wall_ms = 250;
    } else if (!strcmp(kind, "settings")) {
        *run_ms = 100;
        *wall_ms = 100;
    } else {
        *run_ms = 500;
        *wall_ms = 10000;
    }
}

static char *failure_text(const char *code)
{
    char buf[200];
    snprintf(buf, sizeof buf, "{\"code\":\"%s\",\"message\":\"%s\",\"ok\":false}", code, code);
    return strdup(buf);
}

/* A stop from another thread after `ms`: a revoke while the call runs. */
typedef struct {
    sa_runtime *rt;
    int ms;
} stopper;

static void *stop_later(void *arg)
{
    stopper *s = arg;
    struct timespec ts = { s->ms / 1000, (long)(s->ms % 1000) * 1000000 };
    nanosleep(&ts, NULL);
    sa_stop(s->rt);
    return NULL;
}

/*
 * One case, start to end: the stand-in's state, the load, the call, and the
 * lines the case gives. `ms` gets the call's wall time.
 */
static char *run_case(const char *dir, const char *case_json, double *ms)
{
    char *kind = json_field(case_json, "kind");
    if (!kind)
        kind = strdup("command");
    char *begun = sv1("begin", case_json);
    free(begun);

    package p;
    read_package(&p, dir);
    /* the files the case replaces or adds: [[name, how, value], ...] */
    char *files = sv1("files", case_json);
    for (int i = 0;; i++) {
        char *one = json_item(files, i, 0);
        if (!one)
            break;
        char *name = json_item(one, 0, 1), *how = json_item(one, 1, 1), *value = json_item(one, 2, 1);
        override_file(&p, name, how, value);
        free(value);
        free(how);
        free(name);
        free(one);
    }
    free(files);

    char code[CODE_CAP];
    char *out_lines;
    size_t cap = 4096;
    char *text = malloc(cap);
    text[0] = 0;
    int64_t t0 = now_ns();
    sa_runtime *rt = start(&p, code);
    if (ms)
        *ms = (double)(now_ns() - t0) / 1e6;
    char head[160];
    snprintf(head, sizeof head, "load %s\n", rt ? "ok" : code);
    strcat(text, head);
    char *env = NULL;
    int skipped = 0;
    if (!rt) {
        env = failure_text(code);
    } else if (strcmp(kind, "load")) {
        char *input = sv1("input", kind);
        char *command = json_field(case_json, "command");
        const char *id = strcmp(kind, "command") ? "" : command ? command : "case";
        int run_ms, wall_ms;
        limits(kind, &run_ms, &wall_ms);
        char *ran = sv1("run", kind);
        free(ran);
        char *stop_ms = json_field(case_json, "stopAfterMs");
        pthread_t th;
        stopper st = { rt, stop_ms ? atoi(stop_ms) : 0 };
        if (stop_ms)
            pthread_create(&th, NULL, stop_later, &st);
        size_t len;
        t0 = now_ns();
        skipped = input[0] == '!';   /* a card with no idea it may read: no call */
        env = skipped ? failure_text(input + 1) : sa_call(rt, kind, id, input, strlen(input), run_ms, wall_ms, &len);
        if (ms)
            *ms = (double)(now_ns() - t0) / 1e6;
        if (stop_ms) {
            pthread_join(th, NULL);
            free(stop_ms);
        }
        free(input);
        free(command);
    }
    out_lines = sv1("finish", env ? env : "");
    size_t need = strlen(text) + strlen(out_lines) + 2;
    if (need > cap)
        text = realloc(text, need);
    strcat(text, out_lines);
    free(out_lines);
    if (env) {
        if (rt && !skipped)
            sa_string_free(env);
        else
            free(env);
    }
    if (rt)
        sa_free(rt);
    free_package(&p);
    free(kind);
    return text;
}

/* ---- the modes ---- */

static char *store_text(const char *path)
{
    char *s = slurp(path, NULL);
    if (!s)
        die("can't read", path);
    return s;
}

static int conformance(const char *dir)
{
    char *cases_path = join_path(dir, "cases.json");
    char *cases = store_text(cases_path);
    char *store_path = join_path(dir, "store.json");
    char *store = store_text(store_path);
    char *routes_name = json_field(cases, "routes");
    char *now = json_field(cases, "now");
    char *addon_name = json_field(cases, "addon");
    char *routes = NULL;
    if (routes_name) {
        char *rp = join_path(dir, routes_name);
        routes = store_text(rp);
        free(rp);
    }
    const char *argv[3] = { store, routes ? routes : "", now ? now : "2026-11-01T09:00:00.000Z" };
    free(sv("setup", 3, argv, NULL, NULL));
    char *addon = join_path(dir, addon_name ? addon_name : "addon");
    char *list = json_field(cases, "cases");
    int count = 0;
    for (int i = 0;; i++) {
        char *c = json_item(list, i, 0);
        if (!c)
            break;
        char *name = json_field(c, "name");
        char *lines = run_case(addon, c, NULL);
        printf("== %s\n%s\n", name, lines);
        free(lines);
        free(name);
        free(c);
        count++;
    }
    fprintf(stderr, "conformance: %d cases\n", count);
    free(list);
    free(addon);
    free(addon_name);
    free(now);
    free(routes);
    free(routes_name);
    free(store);
    free(store_path);
    free(cases);
    free(cases_path);
    return 0;
}

static char *result_code(const char *lines)
{
    const char *r = strstr(lines, "result {");
    if (!r)
        return strdup("?");
    const char *c = strstr(r, "\"code\":\"");
    const char *nl = strchr(r, '\n');
    if (!c || (nl && c > nl))
        return strdup("ok");
    c += 8;
    size_t n = strcspn(c, "\"");
    char *out = malloc(n + 1);
    memcpy(out, c, n);
    out[n] = 0;
    return out;
}

static int hostile(const char *dir)
{
    char *cases_path = join_path(dir, "cases.json");
    char *cases = store_text(cases_path);
    char *store_name = json_field(cases, "store");
    char *store_path = join_path(dir, store_name);
    char *store = store_text(store_path);
    const char *argv[3] = { store, "", "2026-11-01T09:00:00.000Z" };
    free(sv("setup", 3, argv, NULL, NULL));
    char *list = json_field(cases, "fixtures");
    int failed = 0, count = 0;
    for (int i = 0;; i++) {
        char *c = json_item(list, i, 0);
        if (!c)
            break;
        char *name = json_field(c, "name");
        char *expect = json_field(c, "expect");
        char *limit = json_field(c, "limitMs");
        char *fixture = join_path(dir, name);
        double ms = 0;
        char *lines = run_case(fixture, c, &ms);
        char *code = result_code(lines);
        int in_time = ms <= atof(limit) + 50;
        int applied_none = strstr(lines, "applied none") != NULL;
        int pass = !strcmp(code, expect) && in_time && applied_none;
        printf("%s %s %s %.1f ms (limit %s ms + 50)%s\n", pass ? "PASS" : "FAIL", name, code, ms, limit,
               applied_none ? "" : " (applied)");
        failed += !pass;
        count++;
        free(code);
        free(lines);
        free(fixture);
        free(limit);
        free(expect);
        free(name);
        free(c);
    }
    printf("hostile: %d fixtures, %d failed\n", count, failed);
    free(list);
    free(store);
    free(store_path);
    free(store_name);
    free(cases);
    free(cases_path);
    return failed ? 1 : 0;
}

static int permissions(const char *path)
{
    char *table = store_text(path);
    char *rows = json_field(table, "rows");
    int failed = 0, count = 0;
    for (int i = 0;; i++) {
        char *row = json_item(rows, i, 0);
        if (!row)
            break;
        char *want = json_field(row, "allowed");
        char *got = sv1("allows", row);
        if (!want || strcmp(want, got)) {
            printf("FAIL %s: %s\n", row, got);
            failed++;
        }
        count++;
        free(got);
        free(want);
        free(row);
    }
    printf("permissions: %d rows, %d failed\n", count, failed);
    free(rows);
    free(table);
    return failed ? 1 : 0;
}

static const char *opt(int argc, char **argv, const char *name)
{
    for (int i = 0; i + 1 < argc; i++)
        if (!strcmp(argv[i], name))
            return argv[i + 1];
    return NULL;
}

/* `run`: one command, or the card view on each sample idea, under the app's limits.
 * `--card` takes the manifest's cardView id (every sample idea, one call each) or one idea's id. */
static int run(int argc, char **argv)
{
    const char *dir = argv[2], *ideas = opt(argc, argv, "--ideas");
    const char *command = opt(argc, argv, "--command"), *card = opt(argc, argv, "--card");
    const char *grants = opt(argc, argv, "--grants"), *input = opt(argc, argv, "--input");
    const char *settings = opt(argc, argv, "--settings");
    if (!ideas || (!command && !card))
        die("usage", "harness run <dir> --ideas <file> (--command <id> | --card <card view id or idea id>) "
                     "[--grants a,b] [--input json] [--settings json]");
    char *store = store_text(ideas);
    const char *argv3[3] = { store, "", "2026-11-01T09:00:00.000Z" };
    free(sv("setup", 3, argv3, NULL, NULL));
    /* grants: as given, else all the manifest asks for */
    char *manifest_path = join_path(dir, "manifest.json");
    char *manifest = slurp(manifest_path, NULL);
    char *asked = manifest ? json_field(manifest, "permissions") : NULL;
    char *hosts = manifest ? json_field(manifest, "hosts") : NULL;
    char *commands = manifest ? json_field(manifest, "contributes.commands") : NULL;
    char *view = manifest ? json_field(manifest, "contributes.cardView.id") : NULL;
    size_t cap = 4096 + strlen(input ? input : "") + strlen(settings ? settings : "") + strlen(view ? view : "")
                 + (grants ? strlen(grants) * 3 : 0) + strlen(asked ? asked : "") + strlen(hosts ? hosts : "")
                 + strlen(commands ? commands : "");
    char *c = malloc(cap);
    int n = snprintf(c, cap, "{\"kind\":\"%s\",", command ? "command" : "card");
    if (command)
        n += snprintf(c + n, cap - n, "\"command\":\"%s\",\"input\":%s,", command, input ? input : "{\"ideas\":[]}");
    else
        n += snprintf(c + n, cap - n, "\"card\":\"%s\",\"view\":\"%s\",", card, view ? view : "");
    n += snprintf(c + n, cap - n, "\"grantsText\":\"%s\",\"settings\":%s,", grants ? grants : "",
                  settings ? settings : "{}");
    n += snprintf(c + n, cap - n, "\"asked\":%s,\"hosts\":%s,\"commandList\":%s}", asked ? asked : "[]",
                  hosts ? hosts : "[]", commands ? commands : "[]");
    char *all = sv1("runCases", c);
    int rc = 0, count = 0;
    double total = 0, worst = 0;
    for (int i = 0;; i++) {
        char *one = json_item(all, i, 0);
        if (!one)
            break;
        double ms = 0;
        char *lines = run_case(dir, one, &ms);
        char *name = json_field(one, "card");
        if (card && name)
            printf("== %s\n", name);
        printf("%s\n", lines);
        char *code = result_code(lines);
        if (strcmp(code, "ok"))
            rc = 1;
        total += ms;
        if (ms > worst)
            worst = ms;
        count++;
        free(code);
        free(name);
        free(lines);
        free(one);
    }
    if (!count) {
        fprintf(stderr, "harness: nothing to run (no idea this add-on may read)\n");
        rc = 1;
    }
    fprintf(stderr, "harness: %d call%s, %.1f ms in all, slowest %.1f ms\n", count, count == 1 ? "" : "s", total, worst);
    free(all);
    free(c);
    free(view);
    free(commands);
    free(hosts);
    free(asked);
    free(manifest);
    free(manifest_path);
    free(store);
    return rc;
}

/* ---- threads: the add-on's own, and the main thread's heartbeat ---- */

typedef struct {
    int argc;
    char **argv;
    int rc;
    atomic_int done;
    size_t stack;
} work;

static void *worker(void *arg)
{
    work *w = arg;
    w->stack = sa_current_stack_size();
    standin_open();
    const char *mode = w->argv[1];
    if (!strcmp(mode, "conformance"))
        w->rc = conformance(w->argv[2]);
    else if (!strcmp(mode, "hostile"))
        w->rc = hostile(w->argv[2]);
    else if (!strcmp(mode, "permissions"))
        w->rc = permissions(w->argv[2]);
    else if (!strcmp(mode, "run"))
        w->rc = run(w->argc, w->argv);
    else
        die("unknown mode", mode);
    standin_close();
    atomic_store(&w->done, 1);
    return NULL;
}

/* The main thread's beat every 4 ms: its widest gap, while it waits. */
static double beat(atomic_int *done, int64_t for_ns)
{
    int64_t last = now_ns(), until = last + for_ns, widest = 0;
    struct timespec tick = { 0, 4000000 };
    while (done ? !atomic_load(done) : now_ns() < until) {
        nanosleep(&tick, NULL);
        int64_t t = now_ns();
        if (t - last > widest)
            widest = t - last;
        last = t;
    }
    return (double)widest / 1e6;
}

int main(int argc, char **argv)
{
    setvbuf(stdout, NULL, _IOFBF, 1 << 16);
    if (argc < 3)
        die("usage", "harness (run|conformance|hostile|permissions) <path> ...");
    work w = { argc, argv, 0, 0, 0 };
    atomic_init(&w.done, 0);
    double baseline = beat(NULL, 200000000);
    pthread_attr_t attr;
    pthread_attr_init(&attr);
    pthread_attr_setstacksize(&attr, SA_THREAD_STACK);
    pthread_t th;
    if (pthread_create(&th, &attr, worker, &w))
        die("can't start the add-on thread", NULL);
    pthread_attr_destroy(&attr);
    double widest = beat(&w.done, 0);
    pthread_join(th, NULL);
    fflush(stdout);
    fprintf(stderr, "harness: engine %s, add-on thread stack %zu bytes, main-thread beat %.1f ms (baseline %.1f ms)\n",
            sa_engine_version(), w.stack, widest, baseline);
    if (w.stack != SA_THREAD_STACK)
        die("the add-on thread's stack is not 4 MB", NULL);
    if (widest > baseline + 50)
        die("the main thread missed its beat", NULL);
    return w.rc;
}
