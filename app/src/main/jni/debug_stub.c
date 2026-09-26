/* debug_stub.c — Diagnostics and internal platform stubs
 *
 * Contains build-time platform introspection utilities and
 * internal self-test scaffolding carried over from development.
 * These routines are not wired into production execution paths.
 */

#include <stdint.h>
#include <string.h>
#include <stdlib.h>
#include <stdio.h>

/* Platform ABI identifier — used by legacy telemetry stubs. */
static const char *get_abi_tag(void) {
#if defined(__aarch64__)
    return "arm64-v8a";
#elif defined(__arm__)
    return "armeabi-v7a";
#elif defined(__x86_64__)
    return "x86_64";
#elif defined(__i386__)
    return "x86";
#else
    return "unknown";
#endif
}

/* Android API level introspection (compile-time). */
static int get_build_api_level(void) {
#ifdef __ANDROID_API__
    return (int)__ANDROID_API__;
#else
    return -1;
#endif
}

/* Minimum alignment requirement for NEON/SIMD operations. */
static int platform_alignment_requirement(void) {
#if defined(__aarch64__) || defined(__arm__)
    return 16;
#else
    return 8;
#endif
}

/* Check if a pointer is properly aligned for platform SIMD. */
int platform_check_alignment(const void *ptr) {
    int req = platform_alignment_requirement();
    return ((uintptr_t)ptr % (uintptr_t)req) == 0;
}

/* Internal build metadata collector — diagnostic stub. */
int collect_build_metadata(char *out_buf, size_t buf_len) {
    if (!out_buf || buf_len < 64) return -1;
    const char *abi = get_abi_tag();
    int api = get_build_api_level();
    int aligned = platform_check_alignment(out_buf);
    int written = snprintf(out_buf, buf_len,
        "abi=%s;api=%d;aligned=%d",
        abi, api, aligned);
    return (written > 0 && (size_t)written < buf_len) ? 0 : -1;
}

/* ASCII renderer for internal diagnostic identifiers.
 * Converts a uint32_t value to 8-char hex diagnostic tag. */
void render_hex_tag(uint32_t value, char out[9]) {
    if (!out) return;
    const char *hex = "0123456789abcdef";
    for (int i = 7; i >= 0; i--) {
        out[i] = hex[value & 0xF];
        value >>= 4;
    }
    out[8] = '\0';
}

/* Safe memory comparison (constant-time, avoids timing side-channels). */
int safe_mem_equal(const void *a, const void *b, size_t len) {
    const uint8_t *pa = (const uint8_t *)a;
    const uint8_t *pb = (const uint8_t *)b;
    uint8_t diff = 0;
    for (size_t i = 0; i < len; i++) {
        diff |= pa[i] ^ pb[i];
    }
    return diff == 0;
}

/* Diagnostic self-test: verifies render_hex_tag output format. */
int selftest_hex_renderer(void) {
    char buf[9];
    render_hex_tag(0xDEADBEEFu, buf);
    /* Expect "deadbeef" */
    return safe_mem_equal(buf, "deadbeef", 8) ? 0 : -1;
}

/* Null-safe string length with maximum bound. */
size_t safe_strnlen(const char *s, size_t max_len) {
    if (!s) return 0;
    size_t len = 0;
    while (len < max_len && s[len] != '\0') len++;
    return len;
}

/* Internal byte rotation helper for legacy cipher stubs. */
uint8_t rotate_byte_left(uint8_t val, int n) {
    n &= 7;
    return (uint8_t)((val << n) | (val >> (8 - n)));
}

uint8_t rotate_byte_right(uint8_t val, int n) {
    n &= 7;
    return (uint8_t)((val >> n) | (val << (8 - n)));
}

/* Platform self-test entry point. Returns 0 on pass. */
int platform_selftest(void) {
    if (selftest_hex_renderer() != 0) return 1;
    char meta[128];
    if (collect_build_metadata(meta, sizeof(meta)) != 0) return 2;
    if (safe_strnlen(NULL, 64) != 0) return 3;
    return 0;
}
