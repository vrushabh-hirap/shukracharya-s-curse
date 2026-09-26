/* anti_frida.c — Environment validation checks */

#include "anti_frida.h"

#include <stdio.h>
#include <string.h>
#include <sys/syscall.h>
#include <sys/ptrace.h>
#include <errno.h>
#include <unistd.h>
#include <time.h>
#include <stdint.h>

/*
 * Technique 1: Scan /proc/self/maps for injected Frida library artifacts.
 */
int check_maps(void) {
    FILE *f = fopen("/proc/self/maps", "r");
    if (!f) return 0;

    char line[512];
    int found = 0;
    while (fgets(line, sizeof(line), f)) {
        if (strstr(line, "frida") ||
            strstr(line, "gum-js-loop") ||
            strstr(line, "linjector")) {
            found = 1;
            break;
        }
    }
    fclose(f);
    return found;
}

/*
 * Technique 2: Raw syscall PTRACE_TRACEME self-attach check.
 * Uses SYS_ptrace / __NR_ptrace directly so libc wrapper hooks are bypassed.
 */
int check_ptrace_syscall(void) {
#if defined(SYS_ptrace) || defined(__NR_ptrace)
#ifndef SYS_ptrace
#define SYS_ptrace __NR_ptrace
#endif
    errno = 0;
    long ret = syscall(SYS_ptrace, PTRACE_TRACEME, 0, (void*)1, 0);
    if (ret == -1 && errno == EPERM) {
        /* Already being traced by a debugger / Frida agent */
        return 1;
    }
    /* Successfully attached to self — detach now */
    syscall(SYS_ptrace, PTRACE_DETACH, 0, (void*)1, 0);
#endif
    return 0;
}

unsigned long long measure_timing_ns(void) {
    struct timespec t1, t2;
    if (clock_gettime(CLOCK_MONOTONIC, &t1) != 0) {
        return 0;
    }

    volatile uint64_t acc = 0x12345678ULL;
    for (int i = 0; i < 50000; i++) {
        acc = (acc ^ (acc << 13)) + (acc >> 7) + (uint64_t)i;
    }

    if (clock_gettime(CLOCK_MONOTONIC, &t2) != 0) {
        return 0;
    }

    return (unsigned long long)(t2.tv_sec - t1.tv_sec) * 1000000000ULL +
           (unsigned long long)(t2.tv_nsec - t1.tv_nsec);
}

/*
 * Technique 3: Monotonic clock timing check.
 * Measures execution time of a 50,000 iteration computation loop.
 * Uses a conservative 200 ms threshold to ensure zero false positives on slower emulators,
 * while reliably catching debugger single-stepping or heavy JS engine instruction hooking.
 */
int check_timing(void) {
    unsigned long long elapsed_ns = measure_timing_ns();
    /* 200 ms threshold (normal execution is < 0.5 ms on all Android CPUs; 200ms prevents emulator jitter) */
    if (elapsed_ns > 200000000ULL) {
        return 1;
    }
    return 0;
}

/*
 * Public API: check_instrumentation()
 * Returns: 1 if ANY detection technique triggers, 0 if clean.
 */
int check_instrumentation(void) {
    if (check_maps()) return 1;
    if (check_ptrace_syscall()) return 1;
    if (check_timing()) return 1;
    return 0;
}

int check_instrumentation_mask(void) {
    int mask = 0;
    if (check_maps()) mask |= 1;
    if (check_ptrace_syscall()) mask |= 2;
    if (check_timing()) mask |= 4;
    return mask;
}
