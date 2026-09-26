package com.shukra.stage2;

/**
 * State verification engine coupled with runtime native evolution state.
 */
public final class FinalVerifier {

    private static final int EXPECTED_LENGTH = 39;
    private static final int S_ACCEPT = 8;
    private static final int S_REJECT = 9;

    private static final int[] SBOX = {
        0x7, 0xe, 0x2, 0xd, 0x8, 0x3, 0xa, 0x6,
        0xf, 0x1, 0xc, 0x4, 0x9, 0x0, 0xb, 0x5
    };

    private static final int[] INV_SBOX = new int[16];
    static {
        for (int i = 0; i < 16; i++) {
            INV_SBOX[SBOX[i]] = i;
        }
    }

    private static final int[] CHECKPOINT_INDICES = {
        4, 9, 14, 19, 24, 29, 34, 38
    };

    private static final int[] BASE_CHECKPOINTS = {
        0x18eb6fbf, 0x41d99994, 0x2286cf39, 0x7cf69478,
        0x1aaf7dc3, (int) 0xe2a237a1L, (int) 0xb8458979L, (int) 0x9736c0f9L
    };

    private static final int BASE_TRACK2 = (int) 0xc879e8e8L;

    /**
     * Verifies the candidate input against the non-linear rolling FSM parameterized
     * by the live native state token.
     *
     * @param input      User-supplied candidate string
     * @param stateToken 32-bit state token returned from nativeDissolveStage2
     * @return true if candidate satisfies all state machine invariants
     */
    public static boolean check(String input, int stateToken) {
        if (input == null || input.length() != EXPECTED_LENGTH) {
            return false;
        }

        int state = 0;
        int hash = 0x811c9dc5 ^ stateToken;
        int prevV = (0xA5 ^ (stateToken & 0xFF)) & 0xFF;
        int g = 0x1337c0de ^ (stateToken * 0x01000193);
        int len = input.length();

        for (int i = 0; i < len; i++) {
            if (state == S_REJECT) {
                break;
            }

            char c = input.charAt(i);
            if (c > 127) {
                state = S_REJECT;
                break;
            }

            int b = (int) c;
            int low = SBOX[b & 0x0F];
            int high = SBOX[(b >> 4) & 0x0F];
            int t = (high << 4) | low;
            int v = (t ^ prevV ^ ((i * 37) & 0xFF) ^ 0x5A) & 0xFF;
            prevV = v;

            hash = (hash ^ v) * 0x01000193;

            int rol5 = (g << 5) | (g >>> 27);
            int term = INV_SBOX[b & 0x0F] * 31;
            g = rol5 ^ term ^ b ^ ((i * 13) & 0xFF);

            if (state < CHECKPOINT_INDICES.length && i == CHECKPOINT_INDICES[state]) {
                int expectedHash = BASE_CHECKPOINTS[state] ^ (stateToken * (state + 1));
                if (hash == expectedHash) {
                    state++;
                } else {
                    state = S_REJECT;
                }
            }
        }

        int expectedTrack2 = BASE_TRACK2 ^ (stateToken * 31337);
        return state == S_ACCEPT && len == EXPECTED_LENGTH && g == expectedTrack2;
    }

    private FinalVerifier() {}
}
