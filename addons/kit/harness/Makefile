# The kit's harness (S07, section 8): Sorta's add-on host and QuickJS-ng from
# ../../engine, the same files the iOS and Android apps build.
#
#   make            builds build/harness
#   make check      conformance, hostile fixtures and the permission table
#                   (from a Sorta checkout, which has addons/conformance)

ENGINE  := ../../engine
QJS     := $(ENGINE)/quickjs-ng
HOST    := $(ENGINE)/host
CC      ?= cc
CFLAGS  ?= -O2 -g
# the engine's own sources build quietly; the host and harness with warnings
QJS_FLAGS  := -std=gnu11 -D_GNU_SOURCE -w
OWN_FLAGS  := -std=gnu11 -Wall -Wextra -Wno-unused-parameter -DHARNESS_DIR='"$(CURDIR)"'
LDLIBS  := -lm -pthread

QJS_SRC := $(QJS)/quickjs.c $(QJS)/libregexp.c $(QJS)/libunicode.c $(QJS)/dtoa.c
QJS_OBJ := $(patsubst $(QJS)/%.c,build/qjs/%.o,$(QJS_SRC))

all: build/harness harness

# the kit's documented name: make -C kit/harness, then kit/harness/harness
harness: build/harness
	cp build/harness harness

build/qjs/%.o: $(QJS)/%.c
	@mkdir -p build/qjs
	$(CC) $(CFLAGS) $(QJS_FLAGS) -pthread -c $< -o $@

build/sorta_host.o: $(HOST)/sorta_host.c $(HOST)/sorta_host.h $(HOST)/sorta_prelude.h
	@mkdir -p build
	$(CC) $(CFLAGS) $(OWN_FLAGS) -pthread -c $< -o $@

build/harness.o: harness.c $(HOST)/sorta_host.h
	@mkdir -p build
	$(CC) $(CFLAGS) $(OWN_FLAGS) -pthread -c $< -o $@

build/harness: build/harness.o build/sorta_host.o $(QJS_OBJ)
	$(CC) $(CFLAGS) -o $@ $^ $(LDLIBS)

CONF := ../../conformance
FIX  := ../../fixtures

check: build/harness harness
	./build/harness conformance $(CONF) > build/conformance.txt
	diff -u $(CONF)/expected.txt build/conformance.txt
	./build/harness hostile $(FIX)/hostile
	./build/harness permissions $(CONF)/permissions.json

clean:
	rm -rf build harness

.PHONY: all check clean
