/* compat_layer.c — NDK platform abstraction and memory alignment wrappers
 *
 * Provides safe string routines, memory barrier semantics, and endianness
 * helpers for low-level Android NDK compatibility across architecture ABIs.
 */

#include <stdint.h>
#include <stddef.h>
#include <string.h>

/* Safe string copying implementation */
size_t compat_strlcpy(char *dst, const char *src, size_t siz) {
    char *d = dst;
    const char *s = src;
    size_t n = siz;

    if (n != 0) {
        while (--n != 0) {
            if ((*d++ = *s++) == '\0') {
                break;
            }
        }
    }

    if (n == 0) {
        if (siz != 0) {
            *d = '\0';
        }
        while (*s++) {
            /* advance to compute full source length */
        }
    }

    return (size_t)(s - src - 1);
}

/* Safe string concatenation implementation */
size_t compat_strlcat(char *dst, const char *src, size_t siz) {
    char *d = dst;
    const char *s = src;
    size_t n = siz;
    size_t dlen;

    while (n-- != 0 && *d != '\0') {
        d++;
    }
    dlen = (size_t)(d - dst);
    n = siz - dlen;

    if (n == 0) {
        return dlen + strlen(src);
    }

    while (*s != '\0') {
        if (n != 1) {
            *d++ = *s;
            n--;
        }
        s++;
    }
    *d = '\0';

    return dlen + (size_t)(s - src);
}

/* 32-bit big-endian to host byte order converter */
uint32_t compat_be32_to_cpu(uint32_t val) {
#if __BYTE_ORDER__ == __ORDER_LITTLE_ENDIAN__
    return ((val >> 24) & 0x000000FF) |
           ((val >> 8)  & 0x0000FF00) |
           ((val << 8)  & 0x00FF0000) |
           ((val << 24) & 0xFF000000);
#else
    return val;
#endif
}

/* 32-bit host to big-endian byte order converter */
uint32_t compat_cpu_to_be32(uint32_t val) {
    return compat_be32_to_cpu(val);
}

/* Constant-time memory comparison to mitigate timing side-channels in verification tests */
int compat_const_memcmp(const void *a, const void *b, size_t len) {
    const uint8_t *p1 = (const uint8_t *)a;
    const uint8_t *p2 = (const uint8_t *)b;
    uint8_t result = 0;
    for (size_t i = 0; i < len; i++) {
        result |= p1[i] ^ p2[i];
    }
    return result == 0 ? 0 : 1;
}

/* Memory sanitizer zeroization helper */
void compat_secure_zero(void *ptr, size_t len) {
    volatile uint8_t *p = (volatile uint8_t *)ptr;
    while (len--) {
        *p++ = 0;
    }
}
