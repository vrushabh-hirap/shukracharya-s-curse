package com.vrushabhhirap.shukracharyascurse;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.vrushabhhirap.shukracharyascurse.model.UserSession;
import com.vrushabhhirap.shukracharyascurse.ui.ThemeController;
import com.vrushabhhirap.shukracharyascurse.utils.AnalyticsHelper;
import com.vrushabhhirap.shukracharyascurse.utils.AppPreferences;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Vault activity managing session authentication and archival verification.
 */
public class SecretActivity extends AppCompatActivity {

    private static final String TAG = "SecretActivity";

    private static final byte[] DEV_KEY = {
        0x4b, 0x7a, 0x09, 0x2f, 0x1d, (byte)0x8e, 0x3a, 0x56,
        0x4b, 0x7a, 0x09, 0x2f, 0x1d, (byte)0x8e, 0x3a, 0x56
    };

    private static final String FRAG_A = "sanjivani_seed_v1";

    private ShukraJNI mJni;
    private UserSession mCurrentSession;
    private AppPreferences mPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_secret);
        ThemeController.INSTANCE.applyTheme(this);

        mPreferences = AppPreferences.Companion.getInstance(this);
        mCurrentSession = new UserSession(mPreferences.getLastKnownUser(), UserSession.AccessTier.SCHOLAR);
        mCurrentSession.addFlag(UserSession.FLAG_RESTRICTED_ACCESS);

        AnalyticsHelper.INSTANCE.logEvent(
            AnalyticsHelper.EventType.NAVIGATION,
            "SecretActivity",
            "Initial entrance to restricted vault"
        );

        System.loadLibrary("shukra");
        mJni = new ShukraJNI();

        // Native State Evolution Sequence:
        // Transition 1: Native Entropy Pool Initialization
        mJni.nativeBeginSession();

        // Transition 2: Hardware Entropy Non-linear Diffusion
        mJni.nativeSyncHardwareEntropy(Build.BOARD);

        // Transition 3: Lore Syllable Ingestion & Verification
        mJni.nativeVerifyLoreInvocation(getDevanagariPhrase());

        String hexBlob = computeSealedHex();
        TextView tvBlob = findViewById(R.id.tv_blob);
        if (tvBlob != null) {
            tvBlob.setText(hexBlob);
        }

        View btnCopy = findViewById(R.id.btn_copy_blob);
        if (btnCopy != null) {
            btnCopy.setOnClickListener(v -> {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Sealed Fragment Hex", hexBlob);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(this, "Hex fragment copied to clipboard", Toast.LENGTH_SHORT).show();
                }
            });
        }

        EditText etInput = findViewById(R.id.et_candidate);
        Button btnSubmit = findViewById(R.id.btn_submit);
        if (btnSubmit != null && etInput != null) {
            btnSubmit.setOnClickListener(v -> {
                String candidate = etInput.getText().toString().trim();
                AnalyticsHelper.INSTANCE.logEvent(
                    AnalyticsHelper.EventType.AUTH_ATTEMPT,
                    "CredentialVerification",
                    "Candidate length: " + candidate.length()
                );
                String result = mJni.nativeVerify(candidate);
                Toast.makeText(this, result, Toast.LENGTH_LONG).show();
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mCurrentSession != null) {
            mCurrentSession.touch();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        AnalyticsHelper.INSTANCE.flush();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "SecretActivity destroyed.");
    }

    private String getDevanagariPhrase() {
        try (InputStream is = getResources().openRawResource(R.raw.notes);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                for (int i = 0; i < line.length(); i++) {
                    char c = line.charAt(i);
                    if (c >= '\u0900' && c <= '\u097F') {
                        return Normalizer.normalize(line, Normalizer.Form.NFC);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading notes: " + e.getMessage());
        }
        return "";
    }

    private String computeSealedHex() {
        try {
            byte[] iv = new byte[16];
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(DEV_KEY, "AES"),
                    new IvParameterSpec(iv));
            byte[] encrypted = cipher.doFinal(FRAG_A.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(encrypted);
        } catch (Exception e) {
            return "error";
        }
    }

    public void attemptRealPath(String candidate) {
        boolean result = SanjivaniLoader.verify(this, mJni, candidate);
        if (result) {
            Toast.makeText(this, "The secret seal has shattered. The knowledge is unlocked.", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "The seal holds.", Toast.LENGTH_SHORT).show();
        }
    }

    // Utility
    static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b & 0xff));
        return sb.toString();
    }

    static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2)
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        return data;
    }
}
