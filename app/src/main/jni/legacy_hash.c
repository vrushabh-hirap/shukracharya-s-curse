/* legacy_hash.c — Archival and legacy native hashing routines
 *
 * Implements historical checksum and rolling hash algorithms originally
 * designed for local cache block validation and packet integrity checks.
 * Retained for backward compatibility with archival schemas.
 */

#include <stdint.h>
#include <stddef.h>

/* Classic djb2 hash */
uint32_t legacy_djb2(const unsigned char *str) {
    uint32_t hash = 5381;
    int c;
    while ((c = *str++)) {
        hash = ((hash << 5) + hash) + (uint32_t)c; /* hash * 33 + c */
    }
    return hash;
}

/* 32-bit FNV-1a */
uint32_t legacy_fnv1a(const void *data, size_t len) {
    const uint8_t *p = (const uint8_t *)data;
    uint32_t hash = 2166136261u;
    for (size_t i = 0; i < len; i++) {
        hash ^= p[i];
        hash *= 16777619u;
    }
    return hash;
}

/* Adler-32 checksum implementation */
uint32_t legacy_adler32(const uint8_t *data, size_t len) {
    const uint32_t MOD_ADLER = 65521;
    uint32_t a = 1, b = 0;
    for (size_t i = 0; i < len; i++) {
        a = (a + data[i]) % MOD_ADLER;
        b = (b + a) % MOD_ADLER;
    }
    return (b << 16) | a;
}

/* CRC-16 (CCITT-False standard polynomial 0x1021) */
uint16_t legacy_crc16_ccitt(const uint8_t *data, size_t len) {
    uint16_t crc = 0xFFFF;
    for (size_t i = 0; i < len; i++) {
        crc ^= (uint16_t)data[i] << 8;
        for (int j = 0; j < 8; j++) {
            if (crc & 0x8000) {
                crc = (crc << 1) ^ 0x1021;
            } else {
                crc = (crc << 1);
            }
        }
    }
    return crc;
}

/* Simplified MurmurHash3 32-bit variant */
uint32_t legacy_murmur3_32(const uint8_t *key, size_t len, uint32_t seed) {
    uint32_t h = seed;
    const size_t nblocks = len / 4;
    const uint32_t *blocks = (const uint32_t *)key;

    for (size_t i = 0; i < nblocks; i++) {
        uint32_t k = blocks[i];
        k *= 0xcc9e2d51;
        k = (k << 15) | (k >> 17);
        k *= 0x1b873593;

        h ^= k;
        h = (h << 13) | (h >> 19);
        h = h * 5 + 0xe6546b64;
    }

    const uint8_t *tail = (const uint8_t *)(key + nblocks * 4);
    uint32_t k1 = 0;
    switch (len & 3) {
        case 3: k1 ^= (uint32_t)tail[2] << 16; /* fall through */
        case 2: k1 ^= (uint32_t)tail[1] << 8;  /* fall through */
        case 1: k1 ^= (uint32_t)tail[0];
                k1 *= 0xcc9e2d51;
                k1 = (k1 << 15) | (k1 >> 17);
                k1 *= 0x1b873593;
                h ^= k1;
    }

    h ^= (uint32_t)len;
    h ^= h >> 16;
    h *= 0x85ebca6b;
    h ^= h >> 13;
    h *= 0xc2b2ae35;
    h ^= h >> 16;

    return h;
}

/* Rabin-Karp polynomial rolling hash */
uint64_t legacy_rabin_karp_hash(const char *str, size_t len) {
    const uint64_t P = 31337;
    const uint64_t M = 1000000007;
    uint64_t hash_val = 0;
    uint64_t p_pow = 1;

    for (size_t i = 0; i < len; i++) {
        hash_val = (hash_val + ((uint8_t)str[i] + 1) * p_pow) % M;
        p_pow = (p_pow * P) % M;
    }
    return hash_val;
}
