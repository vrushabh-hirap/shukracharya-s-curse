/* shukra_native.c — Hardened native state evolution engine & dynamic verifier coupling */

#include <jni.h>
#include <string.h>
#include <stdlib.h>
#include <stdio.h>
#include <stdint.h>
#include <android/log.h>
#include "anti_frida.h"

#define LOG_TAG "libshukra"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/* ── SHA-256 implementation (standalone, hermetic) ── */

typedef unsigned int  uint32;
typedef unsigned long long uint64;
typedef unsigned char uchar;

static const uint32 K[64] = {
    0x428a2f98,0x71374491,0xb5c0fbcf,0xe9b5dba5,
    0x3956c25b,0x59f111f1,0x923f82a4,0xab1c5ed5,
    0xd807aa98,0x12835b01,0x243185be,0x550c7dc3,
    0x72be5d74,0x80deb1fe,0x9bdc06a7,0xc19bf174,
    0xe49b69c1,0xefbe4786,0x0fc19dc6,0x240ca1cc,
    0x2de92c6f,0x4a7484aa,0x5cb0a9dc,0x76f988da,
    0x983e5152,0xa831c66d,0xb00327c8,0xbf597fc7,
    0xc6e00bf3,0xd5a79147,0x06ca6351,0x14292967,
    0x27b70a85,0x2e1b2138,0x4d2c6dfc,0x53380d13,
    0x650a7354,0x766a0abb,0x81c2c92e,0x92722c85,
    0xa2bfe8a1,0xa81a664b,0xc24b8b70,0xc76c51a3,
    0xd192e819,0xd6990624,0xf40e3585,0x106aa070,
    0x19a4c116,0x1e376c08,0x2748774c,0x34b0bcb5,
    0x391c0cb3,0x4ed8aa4a,0x5b9cca4f,0x682e6ff3,
    0x748f82ee,0x78a5636f,0x84c87814,0x8cc70208,
    0x90befffa,0xa4506ceb,0xbef9a3f7,0xc67178f2
};

#define ROTR(x,n) (((x)>>(n))|((x)<<(32-(n))))
#define CH(x,y,z)  (((x)&(y))^(~(x)&(z)))
#define MAJ(x,y,z) (((x)&(y))^((x)&(z))^((y)&(z)))
#define SIG0(x) (ROTR(x,2)^ROTR(x,13)^ROTR(x,22))
#define SIG1(x) (ROTR(x,6)^ROTR(x,11)^ROTR(x,25))
#define sig0(x) (ROTR(x,7)^ROTR(x,18)^((x)>>3))
#define sig1(x) (ROTR(x,17)^ROTR(x,19)^((x)>>10))

static void sha256_transform(uint32 state[8], const uchar data[64]) {
    uint32 W[64], a,b,c,d,e,f,g,h,T1,T2;
    int i;
    for (i = 0; i < 16; i++)
        W[i] = ((uint32)data[i*4]<<24)|((uint32)data[i*4+1]<<16)|
               ((uint32)data[i*4+2]<<8)|(uint32)data[i*4+3];
    for (i = 16; i < 64; i++)
        W[i] = sig1(W[i-2]) + W[i-7] + sig0(W[i-15]) + W[i-16];
    a=state[0];b=state[1];c=state[2];d=state[3];
    e=state[4];f=state[5];g=state[6];h=state[7];
    for (i = 0; i < 64; i++) {
        T1 = h + SIG1(e) + CH(e,f,g) + K[i] + W[i];
        T2 = SIG0(a) + MAJ(a,b,c);
        h=g; g=f; f=e; e=d+T1;
        d=c; c=b; b=a; a=T1+T2;
    }
    state[0]+=a;state[1]+=b;state[2]+=c;state[3]+=d;
    state[4]+=e;state[5]+=f;state[6]+=g;state[7]+=h;
}

static void sha256(const uchar *in, size_t inlen, uchar out[32]) {
    uint32 state[8] = {
        0x6a09e667,0xbb67ae85,0x3c6ef372,0xa54ff53a,
        0x510e527f,0x9b05688c,0x1f83d9ab,0x5be0cd19
    };
    uchar buf[64];
    size_t i;
    uint64 bits = (uint64)inlen * 8;

    while (inlen >= 64) {
        sha256_transform(state, in);
        in += 64; inlen -= 64;
    }
    memcpy(buf, in, inlen);
    buf[inlen++] = 0x80;
    if (inlen > 56) {
        memset(buf+inlen, 0, 64-inlen);
        sha256_transform(state, buf);
        inlen = 0;
    }
    memset(buf+inlen, 0, 56-inlen);
    for (i = 0; i < 8; i++) buf[56+i] = (uchar)(bits >> (56 - 8*i));
    sha256_transform(state, buf);

    for (i = 0; i < 8; i++) {
        out[i*4]   = (state[i]>>24)&0xff;
        out[i*4+1] = (state[i]>>16)&0xff;
        out[i*4+2] = (state[i]>>8)&0xff;
        out[i*4+3] =  state[i]    &0xff;
    }
}

/* ── AES-256-CBC Decryption Implementation ── */

static const uint8_t sbox[256] = {
    0x63, 0x7c, 0x77, 0x7b, 0xf2, 0x6b, 0x6f, 0xc5, 0x30, 0x01, 0x67, 0x2b, 0xfe, 0xd7, 0xab, 0x76,
    0xca, 0x82, 0xc9, 0x7d, 0xfa, 0x59, 0x47, 0xf0, 0xad, 0xd4, 0xa2, 0xaf, 0x9c, 0xa4, 0x72, 0xc0,
    0xb7, 0xfd, 0x93, 0x26, 0x36, 0x3f, 0xf7, 0xcc, 0x34, 0xa5, 0xe5, 0xf1, 0x71, 0xd8, 0x31, 0x15,
    0x04, 0xc7, 0x23, 0xc3, 0x18, 0x96, 0x05, 0x9a, 0x07, 0x12, 0x80, 0xe2, 0xeb, 0x27, 0xb2, 0x75,
    0x09, 0x83, 0x2c, 0x1a, 0x1b, 0x6e, 0x5a, 0xa0, 0x52, 0x3b, 0xd6, 0xb3, 0x29, 0xe3, 0x2f, 0x84,
    0x53, 0xd1, 0x00, 0xed, 0x20, 0xfc, 0xb1, 0x5b, 0x6a, 0xcb, 0xbe, 0x39, 0x4a, 0x4c, 0x58, 0xcf,
    0xd0, 0xef, 0xaa, 0xfb, 0x43, 0x4d, 0x33, 0x85, 0x45, 0xf9, 0x02, 0x7f, 0x50, 0x3c, 0x9f, 0xa8,
    0x51, 0xa3, 0x40, 0x8f, 0x92, 0x9d, 0x38, 0xf5, 0xbc, 0xb6, 0xda, 0x21, 0x10, 0xff, 0xf3, 0xd2,
    0xcd, 0x0c, 0x13, 0xec, 0x5f, 0x97, 0x44, 0x17, 0xc4, 0xa7, 0x7e, 0x3d, 0x64, 0x5d, 0x19, 0x73,
    0x60, 0x81, 0x4f, 0xdc, 0x22, 0x2a, 0x90, 0x88, 0x46, 0xee, 0xb8, 0x14, 0xde, 0x5e, 0x0b, 0xdb,
    0xe0, 0x32, 0x3a, 0x0a, 0x49, 0x06, 0x24, 0x5c, 0xc2, 0xd3, 0xac, 0x62, 0x91, 0x95, 0xe4, 0x79,
    0xe7, 0xc8, 0x37, 0x6d, 0x8d, 0xd5, 0x4e, 0xa9, 0x6c, 0x56, 0xf4, 0xea, 0x65, 0x7a, 0xae, 0x08,
    0xba, 0x78, 0x25, 0x2e, 0x1c, 0xa6, 0xb4, 0xc6, 0xe8, 0xdd, 0x74, 0x1f, 0x4b, 0xbd, 0x8b, 0x8a,
    0x70, 0x3e, 0xb5, 0x66, 0x48, 0x03, 0xf6, 0x0e, 0x61, 0x35, 0x57, 0xb9, 0x86, 0xc1, 0x1d, 0x9e,
    0xe1, 0xf8, 0x98, 0x11, 0x69, 0xd9, 0x8e, 0x94, 0x9b, 0x1e, 0x87, 0xe9, 0xce, 0x55, 0x28, 0xdf,
    0x8c, 0xa1, 0x89, 0x0d, 0xbf, 0xe6, 0x42, 0x68, 0x41, 0x99, 0x2d, 0x0f, 0xb0, 0x54, 0xbb, 0x16
};

static const uint8_t rsbox[256] = {
    0x52, 0x09, 0x6a, 0xd5, 0x30, 0x36, 0xa5, 0x38, 0xbf, 0x40, 0xa3, 0x9e, 0x81, 0xf3, 0xd7, 0xfb,
    0x7c, 0xe3, 0x39, 0x82, 0x9b, 0x2f, 0xff, 0x87, 0x34, 0x8e, 0x43, 0x44, 0xc4, 0xde, 0xe9, 0xcb,
    0x54, 0x7b, 0x94, 0x32, 0xa6, 0xc2, 0x23, 0x3d, 0xee, 0x4c, 0x95, 0x0b, 0x42, 0xfa, 0xc3, 0x4e,
    0x08, 0x2e, 0xa1, 0x66, 0x28, 0xd9, 0x24, 0xb2, 0x76, 0x5b, 0xa2, 0x49, 0x6d, 0x8b, 0xd1, 0x25,
    0x72, 0xf8, 0xf6, 0x64, 0x86, 0x68, 0x98, 0x16, 0xd4, 0xa4, 0x5c, 0xcc, 0x5d, 0x65, 0xb6, 0x92,
    0x6c, 0x70, 0x48, 0x50, 0xfd, 0xed, 0xb9, 0xda, 0x5e, 0x15, 0x46, 0x57, 0xa7, 0x8d, 0x9d, 0x84,
    0x90, 0xd8, 0xab, 0x00, 0x8c, 0xbc, 0xd3, 0x0a, 0xf7, 0xe4, 0x58, 0x05, 0xb8, 0xb3, 0x45, 0x06,
    0xd0, 0x2c, 0x1e, 0x8f, 0xca, 0x3f, 0x0f, 0x02, 0xc1, 0xaf, 0xbd, 0x03, 0x01, 0x13, 0x8a, 0x6b,
    0x3a, 0x91, 0x11, 0x41, 0x4f, 0x67, 0xdc, 0xea, 0x97, 0xf2, 0xcf, 0xce, 0xf0, 0xb4, 0xe6, 0x73,
    0x96, 0xac, 0x74, 0x22, 0xe7, 0xad, 0x35, 0x85, 0xe2, 0xf9, 0x37, 0xe8, 0x1c, 0x75, 0xdf, 0x6e,
    0x47, 0xf1, 0x1a, 0x71, 0x1d, 0x29, 0xc5, 0x89, 0x6f, 0xb7, 0x62, 0x0e, 0xaa, 0x18, 0xbe, 0x1b,
    0xfc, 0x56, 0x3e, 0x4b, 0xc6, 0xd2, 0x79, 0x20, 0x9a, 0xdb, 0xc0, 0xfe, 0x78, 0xcd, 0x5a, 0xf4,
    0x1f, 0xdd, 0xa8, 0x33, 0x88, 0x07, 0xc7, 0x31, 0xb1, 0x12, 0x10, 0x59, 0x27, 0x80, 0xec, 0x5f,
    0x60, 0x51, 0x7f, 0xa9, 0x19, 0xb5, 0x4a, 0x0d, 0x2d, 0xe5, 0x7a, 0x9f, 0x93, 0xc9, 0x9c, 0xef,
    0xa0, 0xe0, 0x3b, 0x4d, 0xae, 0x2a, 0xf5, 0xb0, 0xc8, 0xeb, 0xbb, 0x3c, 0x83, 0x53, 0x99, 0x61,
    0x17, 0x2b, 0x04, 0x7e, 0xba, 0x77, 0xd6, 0x26, 0xe1, 0x69, 0x14, 0x63, 0x55, 0x21, 0x0c, 0x7d
};

static const uint8_t Rcon[11] = {
    0x00, 0x01, 0x02, 0x04, 0x08, 0x10, 0x20, 0x40, 0x80, 0x1b, 0x36
};

static void aes_key_expansion_256(const uint8_t *key, uint8_t *round_keys) {
    memcpy(round_keys, key, 32);
    uint8_t temp[4];
    int bytes_generated = 32;
    int rcon_idx = 1;

    while (bytes_generated < 240) {
        for (int i = 0; i < 4; i++) {
            temp[i] = round_keys[bytes_generated - 4 + i];
        }

        if (bytes_generated % 32 == 0) {
            uint8_t k0 = temp[0];
            temp[0] = sbox[temp[1]] ^ Rcon[rcon_idx++];
            temp[1] = sbox[temp[2]];
            temp[2] = sbox[temp[3]];
            temp[3] = sbox[k0];
        } else if (bytes_generated % 32 == 16) {
            for (int i = 0; i < 4; i++) {
                temp[i] = sbox[temp[i]];
            }
        }

        for (int i = 0; i < 4; i++) {
            round_keys[bytes_generated] = round_keys[bytes_generated - 32] ^ temp[i];
            bytes_generated++;
        }
    }
}

static uint8_t gmult(uint8_t a, uint8_t b) {
    uint8_t p = 0;
    for (int i = 0; i < 8; i++) {
        if (b & 1) p ^= a;
        uint8_t hi_bit_set = (a & 0x80);
        a <<= 1;
        if (hi_bit_set) a ^= 0x1b;
        b >>= 1;
    }
    return p;
}

static void inv_sub_bytes(uint8_t state[4][4]) {
    for (int r = 0; r < 4; r++)
        for (int c = 0; c < 4; c++)
            state[r][c] = rsbox[state[r][c]];
}

static void inv_shift_rows(uint8_t state[4][4]) {
    uint8_t temp;
    temp = state[1][3]; state[1][3] = state[1][2]; state[1][2] = state[1][1]; state[1][1] = state[1][0]; state[1][0] = temp;
    temp = state[2][0]; state[2][0] = state[2][2]; state[2][2] = temp;
    temp = state[2][1]; state[2][1] = state[2][3]; state[2][3] = temp;
    temp = state[3][0]; state[3][0] = state[3][1]; state[3][1] = state[3][2]; state[3][2] = state[3][3]; state[3][3] = temp;
}

static void inv_mix_columns(uint8_t state[4][4]) {
    for (int c = 0; c < 4; c++) {
        uint8_t a = state[0][c];
        uint8_t b = state[1][c];
        uint8_t d = state[2][c];
        uint8_t e = state[3][c];

        state[0][c] = gmult(a, 0x0e) ^ gmult(b, 0x0b) ^ gmult(d, 0x0d) ^ gmult(e, 0x09);
        state[1][c] = gmult(a, 0x09) ^ gmult(b, 0x0e) ^ gmult(d, 0x0b) ^ gmult(e, 0x0d);
        state[2][c] = gmult(a, 0x0d) ^ gmult(b, 0x09) ^ gmult(d, 0x0e) ^ gmult(e, 0x0b);
        state[3][c] = gmult(a, 0x0b) ^ gmult(b, 0x0d) ^ gmult(d, 0x09) ^ gmult(e, 0x0e);
    }
}

static void add_round_key(uint8_t state[4][4], const uint8_t *round_key) {
    for (int c = 0; c < 4; c++) {
        for (int r = 0; r < 4; r++) {
            state[r][c] ^= round_key[c * 4 + r];
        }
    }
}

static void aes_decrypt_block_256(const uint8_t in[16], uint8_t out[16], const uint8_t *round_keys) {
    uint8_t state[4][4];
    for (int c = 0; c < 4; c++)
        for (int r = 0; r < 4; r++)
            state[r][c] = in[c * 4 + r];

    add_round_key(state, &round_keys[14 * 16]);

    for (int round = 13; round > 0; round--) {
        inv_shift_rows(state);
        inv_sub_bytes(state);
        add_round_key(state, &round_keys[round * 16]);
        inv_mix_columns(state);
    }

    inv_shift_rows(state);
    inv_sub_bytes(state);
    add_round_key(state, &round_keys[0]);

    for (int c = 0; c < 4; c++)
        for (int r = 0; r < 4; r++)
            out[c * 4 + r] = state[r][c];
}

static uint8_t* aes_cbc_decrypt_pkcs5(const uint8_t *ciphertext_with_iv, size_t len, const uint8_t *key, size_t *out_len) {
    if (len < 32 || (len % 16) != 0) return NULL;

    const uint8_t *iv = ciphertext_with_iv;
    const uint8_t *ct = ciphertext_with_iv + 16;
    size_t ct_len = len - 16;

    uint8_t round_keys[240];
    aes_key_expansion_256(key, round_keys);

    uint8_t *pt = (uint8_t*)malloc(ct_len);
    if (!pt) return NULL;

    const uint8_t *prev_block = iv;
    for (size_t i = 0; i < ct_len; i += 16) {
        uint8_t decrypted_block[16];
        aes_decrypt_block_256(ct + i, decrypted_block, round_keys);
        for (int j = 0; j < 16; j++) {
            pt[i + j] = decrypted_block[j] ^ prev_block[j];
        }
        prev_block = ct + i;
    }

    /* PKCS#5 Unpadding */
    uint8_t pad = pt[ct_len - 1];
    if (pad < 1 || pad > 16) {
        free(pt);
        return NULL;
    }
    for (size_t k = ct_len - pad; k < ct_len; k++) {
        if (pt[k] != pad) {
            free(pt);
            return NULL;
        }
    }

    *out_len = ct_len - pad;
    return pt;
}


/* ── Native State Evolution Engine ───────────────────────────────────────── */

enum {
    STATE_ZERO = 0,
    STATE_ENTROPY_INITIALIZED = 1,
    STATE_HARDWARE_DIFFUSED = 2,
    STATE_LORE_BOUND = 3,
    STATE_DISSOLVED = 4
};

static uint32_t g_fsm_stage = STATE_ZERO;
static uint32_t g_native_state_matrix[8] = {0};
static uint32_t g_dynamic_state_token = 0;

/* Fixed authoritative base seed (Sage Shukra prime seed) */
static const uint32_t SHUKRA_PRIME_SEED[8] = {
    0x5348554b, 0x52414348, 0x41525941, 0x53414e4a,
    0x4956414e, 0x49564944, 0x59415631, 0x9e3779b9
};

static const char *get_decoy(void) {
    static char decoy[32] = {0};
    if (decoy[0]) return decoy;
    const char p1[] = "FLAG{SHU";
    const char p2[] = "KRACHARYA";
    const char p3[] = "_IS_DEAD}";
    snprintf(decoy, sizeof(decoy), "%s%s%s", p1, p2, p3);
    return decoy;
}

/* Decoy verification entry point */
static jstring native_verify_decoy(JNIEnv *env, jobject thiz, jstring input) {
    (void)thiz; (void)input;
    const char *decoy = get_decoy();
    return (*env)->NewStringUTF(env, decoy);
}

/* Step 1: Initialize native state evolution matrix */
static jboolean native_begin_session(JNIEnv *env, jobject thiz) {
    (void)env; (void)thiz;
    memcpy(g_native_state_matrix, SHUKRA_PRIME_SEED, sizeof(SHUKRA_PRIME_SEED));

    /* Native code window self-integrity folding */
    const uint8_t *code_ptr = (const uint8_t *)&sha256_transform;
    for (size_t i = 0; i < 256; i++) {
        g_native_state_matrix[i % 8] = ((g_native_state_matrix[i % 8] ^ code_ptr[i]) * 16777619u) ^ (uint32_t)(i * 31);
    }

    g_fsm_stage = STATE_ENTROPY_INITIALIZED;
    return JNI_TRUE;
}

/* Step 2: Non-linearly diffuse hardware environment anchor */
static jboolean native_sync_hardware_entropy(JNIEnv *env, jobject thiz, jstring jboard) {
    (void)thiz;
    if (g_fsm_stage < STATE_ENTROPY_INITIALIZED || !jboard) return JNI_FALSE;
    const char *board_str = (*env)->GetStringUTFChars(env, jboard, NULL);
    if (!board_str) return JNI_FALSE;

    size_t len = strlen(board_str);
    for (size_t i = 0; i < len; i++) {
        uint32_t b = (uint8_t)board_str[i];
        g_native_state_matrix[i % 8] = ROTR(g_native_state_matrix[i % 8] ^ (b << 16), (i % 24) + 1);
        g_native_state_matrix[(i + 1) % 8] += (b * 0x01000193u);
    }

    (*env)->ReleaseStringUTFChars(env, jboard, board_str);
    g_fsm_stage = STATE_HARDWARE_DIFFUSED;
    return JNI_TRUE;
}

/* Step 3: Non-linearly ingest sacred lore invocation phrase */
static jboolean native_verify_lore_invocation(JNIEnv *env, jobject thiz, jstring jlore) {
    (void)thiz;
    if (g_fsm_stage < STATE_HARDWARE_DIFFUSED || !jlore) return JNI_FALSE;
    const char *lore_str = (*env)->GetStringUTFChars(env, jlore, NULL);
    if (!lore_str) return JNI_FALSE;

    size_t len = strlen(lore_str);
    for (size_t i = 0; i < len; i++) {
        uint32_t c = (uint8_t)lore_str[i];
        g_native_state_matrix[i % 8] = (g_native_state_matrix[i % 8] ^ c) * 16777619u;
        g_native_state_matrix[(i + 3) % 8] ^= ROTR(c, 3);
    }

    (*env)->ReleaseStringUTFChars(env, jlore, lore_str);
    g_fsm_stage = STATE_LORE_BOUND;
    return JNI_TRUE;
}

/* Step 4: Solves native state matrix and decrypts Stage-2 payload into direct ByteBuffer */
static jobject native_dissolve_stage2(JNIEnv *env, jobject thiz, jbyteArray jencryptedAsset) {
    (void)thiz;
    if (g_fsm_stage < STATE_LORE_BOUND || !jencryptedAsset) return NULL;

    if (check_instrumentation()) {
        return NULL;
    }

    /* Compute dynamic 32-byte AES key directly from evolved native matrix */
    unsigned char evolved_key[32];
    sha256((const unsigned char *)g_native_state_matrix, sizeof(g_native_state_matrix), evolved_key);

    /* Produce dynamic state token for Stage-2 FinalVerifier coupling */
    g_dynamic_state_token = g_native_state_matrix[0] ^ g_native_state_matrix[4] ^ 0x5a5aa5a5;

    jsize asset_len = (*env)->GetArrayLength(env, jencryptedAsset);
    jbyte *asset_bytes = (*env)->GetByteArrayElements(env, jencryptedAsset, NULL);
    if (!asset_bytes) return NULL;

    size_t dex_len = 0;
    uint8_t *dex_bytes = aes_cbc_decrypt_pkcs5((const uint8_t*)asset_bytes, (size_t)asset_len, evolved_key, &dex_len);
    (*env)->ReleaseByteArrayElements(env, jencryptedAsset, asset_bytes, JNI_ABORT);

    if (!dex_bytes || dex_len < 4 || memcmp(dex_bytes, "dex\n", 4) != 0) {
        if (dex_bytes) free(dex_bytes);
        return NULL;
    }

    /* Wrap in direct ByteBuffer for Dalvik InMemoryDexClassLoader */
    jobject byteBuffer = (*env)->NewDirectByteBuffer(env, dex_bytes, dex_len);
    g_fsm_stage = STATE_DISSOLVED;

    return byteBuffer;
}

/* Dynamic state token retrieval for FinalVerifier parameterization */
static jint native_get_state_token(JNIEnv *env, jobject thiz) {
    (void)env; (void)thiz;
    if (g_fsm_stage < STATE_DISSOLVED) {
        return 0;
    }
    return (jint)g_dynamic_state_token;
}

/* ── JNI_OnLoad: Dynamic Registration with Stripped Symbols ── */
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void)reserved;
    JNIEnv *env = NULL;

    if ((*vm)->GetEnv(vm, (void**)&env, JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    const char *class_name = "com/vrushabhhirap/shukracharyascurse/ShukraJNI";
    jclass clazz = (*env)->FindClass(env, class_name);
    if (!clazz) {
        LOGE("class not found: %s", class_name);
        return JNI_ERR;
    }

    static const JNINativeMethod methods[] = {
        { "nativeVerify",                "(Ljava/lang/String;)Ljava/lang/String;", (void*)native_verify_decoy },
        { "nativeBeginSession",          "()Z",                                    (void*)native_begin_session },
        { "nativeSyncHardwareEntropy",   "(Ljava/lang/String;)Z",                  (void*)native_sync_hardware_entropy },
        { "nativeVerifyLoreInvocation",  "(Ljava/lang/String;)Z",                  (void*)native_verify_lore_invocation },
        { "nativeDissolveStage2",        "([B)Ljava/nio/ByteBuffer;",              (void*)native_dissolve_stage2 },
        { "nativeGetStateToken",         "()I",                                    (void*)native_get_state_token },
    };

    if ((*env)->RegisterNatives(env, clazz, methods, sizeof(methods)/sizeof(methods[0])) < 0) {
        LOGE("RegisterNatives failed");
        return JNI_ERR;
    }

    return JNI_VERSION_1_6;
}
