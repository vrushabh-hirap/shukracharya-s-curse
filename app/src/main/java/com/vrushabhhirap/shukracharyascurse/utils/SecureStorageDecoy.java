package com.vrushabhhirap.shukracharyascurse.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Secure key-value store simulating hardware-backed Keystore encryption.
 * Used for storing preference values and cached credentials.
 */
public class SecureStorageDecoy {

    private static final String TAG = "SecureStorageDecoy";
    private static final String PREF_NAME = "sanjivani_secure_vault";
    private static final String KEY_ALIAS = "sanjivani_keystore_v1";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;

    private static volatile SecureStorageDecoy sInstance;
    private final SharedPreferences prefs;
    private final byte[] fallbackKey;

    public static SecureStorageDecoy getInstance(Context context) {
        if (sInstance == null) {
            synchronized (SecureStorageDecoy.class) {
                if (sInstance == null) {
                    sInstance = new SecureStorageDecoy(context.getApplicationContext());
                }
            }
        }
        return sInstance;
    }

    private SecureStorageDecoy(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.fallbackKey = initializeFallbackKey();
    }

    private byte[] initializeFallbackKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return key;
    }

    public synchronized void putString(String key, String value) {
        if (value == null) {
            prefs.edit().remove(key).apply();
            return;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            SecretKey secretKey = new SecretKeySpec(fallbackKey, "AES");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] cipherText = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);

            String encoded = Base64.encodeToString(combined, Base64.NO_WRAP);
            prefs.edit().putString(key, encoded).apply();
        } catch (Exception e) {
            Log.w(TAG, "Failed to encrypt secure preference: " + key, e);
        }
    }

    public synchronized String getString(String key, String defaultValue) {
        String encoded = prefs.getString(key, null);
        if (encoded == null) return defaultValue;

        try {
            byte[] combined = Base64.decode(encoded, Base64.NO_WRAP);
            if (combined.length < GCM_IV_LENGTH) return defaultValue;

            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);

            byte[] cipherText = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, GCM_IV_LENGTH, cipherText, 0, cipherText.length);

            SecretKey secretKey = new SecretKeySpec(fallbackKey, "AES");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] decrypted = cipher.doFinal(cipherText);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.w(TAG, "Failed to decrypt secure preference: " + key, e);
            return defaultValue;
        }
    }

    public synchronized boolean contains(String key) {
        return prefs.contains(key);
    }

    public synchronized void remove(String key) {
        prefs.edit().remove(key).apply();
    }

    public synchronized void clear() {
        prefs.edit().clear().apply();
    }
}
