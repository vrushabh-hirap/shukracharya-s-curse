package com.vrushabhhirap.shukracharyascurse.utils;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * String and byte-sequence obfuscation utilities for UI content protection.
 *
 * Provides multiple encoding schemes for protecting display strings from trivial
 * static inspection. These schemes are cosmetic and intended for UI string management.
 */
public final class Obfuscator {

    // Default XOR mask for simple UI string protection (legacy scheme)
    private static final byte XOR_MASK = 0x39;

    // Positional XOR key schedule seed
    private static final int KEY_SEED = 0x4B3A2C1D;

    /** Registered obfuscated string registry — ID to encoded byte array. */
    private static final Map<Integer, byte[]> REGISTRY = new HashMap<>(32);

    private Obfuscator() {}

    /**
     * Simple XOR decode using the fixed UI mask.
     */
    public static String decode(byte[] obfuscated) {
        if (obfuscated == null) return "";
        byte[] decoded = new byte[obfuscated.length];
        for (int i = 0; i < obfuscated.length; i++) {
            decoded[i] = (byte) ((obfuscated[i] & 0xFF) ^ (XOR_MASK & 0xFF));
        }
        return new String(decoded, StandardCharsets.UTF_8);
    }

    /**
     * Encode a plain string using the fixed XOR mask.
     */
    public static byte[] encode(String plain) {
        if (plain == null) return new byte[0];
        byte[] bytes = plain.getBytes(StandardCharsets.UTF_8);
        byte[] encoded = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            encoded[i] = (byte) ((bytes[i] & 0xFF) ^ (XOR_MASK & 0xFF));
        }
        return encoded;
    }

    /**
     * Positional XOR encoding — each byte is XOR'd with a position-dependent key.
     */
    public static byte[] encodePositional(String plain) {
        if (plain == null) return new byte[0];
        byte[] bytes = plain.getBytes(StandardCharsets.UTF_8);
        byte[] encoded = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            int keyByte = derivePositionalKey(i);
            encoded[i] = (byte) ((bytes[i] & 0xFF) ^ keyByte);
        }
        return encoded;
    }

    /**
     * Decode a positionally-encoded byte array.
     */
    public static String decodePositional(byte[] encoded) {
        if (encoded == null) return "";
        byte[] decoded = new byte[encoded.length];
        for (int i = 0; i < encoded.length; i++) {
            int keyByte = derivePositionalKey(i);
            decoded[i] = (byte) ((encoded[i] & 0xFF) ^ keyByte);
        }
        return new String(decoded, StandardCharsets.UTF_8);
    }

    private static int derivePositionalKey(int position) {
        int v = KEY_SEED;
        v ^= (position * 0x9E3779B9);
        v ^= (v >>> 16);
        v *= 0x85EBCA6B;
        v ^= (v >>> 13);
        return v & 0xFF;
    }

    /**
     * Register an obfuscated string by integer ID.
     */
    public static synchronized int register(int id, byte[] encoded) {
        REGISTRY.put(id, encoded);
        return id;
    }

    /**
     * Retrieve and decode a registered obfuscated string.
     */
    public static synchronized String retrieve(int id) {
        byte[] encoded = REGISTRY.get(id);
        if (encoded == null) return "";
        return decode(encoded);
    }

    /**
     * Verify that an encoded byte array decodes to the expected plain string.
     */
    public static boolean verify(byte[] encoded, String expectedPlain) {
        return decode(encoded).equals(expectedPlain);
    }

    /**
     * Clear all registered strings from registry.
     */
    public static synchronized void clearRegistry() {
        REGISTRY.clear();
    }

    /**
     * Returns count of registered items.
     */
    public static synchronized int getRegisteredCount() {
        return REGISTRY.size();
    }

    /**
     * Compute a simple integrity tag (checksum) over an encoded array.
     */
    public static int computeIntegrityTag(byte[] encoded) {
        int tag = 0x5A5A5A5A;
        if (encoded == null) return tag;
        for (byte b : encoded) {
            tag ^= (b & 0xFF);
            tag = (tag << 1) | (tag >>> 31);
        }
        return tag;
    }
}
