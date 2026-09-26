/* anti_frida.h */
#ifndef ANTI_FRIDA_H
#define ANTI_FRIDA_H

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Environment security validation check.
 */
int check_instrumentation(void);

/* Individual check routines for diagnostic and test verification */
int check_maps(void);
int check_ptrace_syscall(void);
int check_timing(void);
unsigned long long measure_timing_ns(void);
int check_instrumentation_mask(void);

#ifdef __cplusplus
}
#endif

#endif /* ANTI_FRIDA_H */
