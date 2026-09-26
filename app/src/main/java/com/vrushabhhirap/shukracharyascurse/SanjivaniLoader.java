package com.vrushabhhirap.shukracharyascurse;

import android.content.Context;
import android.util.Log;

import java.io.InputStream;
import java.nio.ByteBuffer;

import dalvik.system.InMemoryDexClassLoader;

/**
 * Hardened loader and execution runtime for auxiliary assets.
 * Evaluates Stage-2 verifier using the live native state machine evolution token.
 */
public class SanjivaniLoader {

    private static final String TAG = "SanjivaniLoader";
    private static final String ASSET_NAME = "sanjivani.dat";
    private static final String VERIFIER_CLASS = "com.shukra.stage2.FinalVerifier";
    private static final String VERIFIER_METHOD = "check";

    /**
     * Executes Stage-2 evaluation by binding dynamic native state token to the verifier.
     *
     * @param ctx       Application context
     * @param jni       Native bridge
     * @param candidate User-supplied input string
     * @return true on valid verification
     */
    public static boolean verify(Context ctx, ShukraJNI jni, String candidate) {
        try {
            byte[] encryptedData;
            try (InputStream is = ctx.getAssets().open(ASSET_NAME)) {
                encryptedData = is.readAllBytes();
            }

            if (encryptedData.length < 32) {
                Log.e(TAG, "Encrypted payload invalid");
                return false;
            }

            ByteBuffer dexBuffer = jni.nativeDissolveStage2(encryptedData);
            if (dexBuffer == null) {
                Log.e(TAG, "Native dissolution failed: state machine rejected transition");
                return false;
            }

            int stateToken = jni.nativeGetStateToken();

            InMemoryDexClassLoader loader = new InMemoryDexClassLoader(
                    dexBuffer,
                    SanjivaniLoader.class.getClassLoader()
            );

            Class<?> clazz = loader.loadClass(VERIFIER_CLASS);
            java.lang.reflect.Method method = clazz.getMethod(VERIFIER_METHOD, String.class, int.class);
            Object result = method.invoke(null, candidate, stateToken);

            return Boolean.TRUE.equals(result);

        } catch (Exception e) {
            Log.e(TAG, "Verification error: " + e.getClass().getSimpleName());
            return false;
        }
    }
}
