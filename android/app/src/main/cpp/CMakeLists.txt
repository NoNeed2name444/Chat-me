# Sorta's add-on engine for Android (S07b): QuickJS-ng, vendored unmodified in
# addons/engine/quickjs-ng, and the shared host in addons/engine/host, the same
# sources the iOS app and the kit's harness build. sorta_addons_jni.c is the
# only Android-only C.
cmake_minimum_required(VERSION 3.22.1)
project(sorta_addons C)

set(ENGINE ${CMAKE_CURRENT_SOURCE_DIR}/../../../../../addons/engine)

add_library(sorta_addons SHARED
    ${ENGINE}/quickjs-ng/quickjs.c
    ${ENGINE}/quickjs-ng/dtoa.c
    ${ENGINE}/quickjs-ng/libregexp.c
    ${ENGINE}/quickjs-ng/libunicode.c
    ${ENGINE}/host/sorta_host.c
    sorta_addons_jni.c)

set_source_files_properties(
    ${ENGINE}/quickjs-ng/quickjs.c ${ENGINE}/quickjs-ng/dtoa.c
    ${ENGINE}/quickjs-ng/libregexp.c ${ENGINE}/quickjs-ng/libunicode.c
    PROPERTIES COMPILE_FLAGS "-w")

target_include_directories(sorta_addons PRIVATE ${ENGINE}/quickjs-ng ${ENGINE}/host)
target_compile_definitions(sorta_addons PRIVATE _GNU_SOURCE)
target_compile_options(sorta_addons PRIVATE -std=gnu11 -fvisibility=hidden)
# 16 KB pages (Android 15 and later)
target_link_options(sorta_addons PRIVATE "-Wl,-z,max-page-size=16384")
target_link_libraries(sorta_addons m)
