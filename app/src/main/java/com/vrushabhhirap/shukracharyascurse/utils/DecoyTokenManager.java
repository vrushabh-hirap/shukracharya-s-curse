package com.vrushabhhirap.shukracharyascurse.utils;

import android.util.Base64;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Token and session credentials manager.
 * Implements token parsing, payload claims validation, and expiration verification
 * for the archival protocol.
 */
public class DecoyTokenManager {

    private static final String TAG = "DecoyTokenManager";

    public static class TokenClaims {
        public final String issuer;
        public final String subject;
        public final String audience;
        public final long issuedAt;
        public final long expiresAt;
        public final Map<String, Object> customClaims;

        public TokenClaims(String issuer, String subject, String audience, long issuedAt, long expiresAt) {
            this.issuer = issuer;
            this.subject = subject;
            this.audience = audience;
            this.issuedAt = issuedAt;
            this.expiresAt = expiresAt;
            this.customClaims = new HashMap<>();
        }

        public boolean isExpired() {
            return System.currentTimeMillis() / 1000L > expiresAt;
        }

        public void putClaim(String key, Object value) {
            this.customClaims.put(key, value);
        }

        public Object getClaim(String key) {
            return this.customClaims.get(key);
        }
    }

    private static volatile DecoyTokenManager sInstance;
    private String currentToken;
    private TokenClaims currentClaims;

    public static DecoyTokenManager getInstance() {
        if (sInstance == null) {
            synchronized (DecoyTokenManager.class) {
                if (sInstance == null) {
                    sInstance = new DecoyTokenManager();
                }
            }
        }
        return sInstance;
    }

    private DecoyTokenManager() {
        this.currentToken = generateMockToken("kacha_disciple", "ashrama_tier_1");
        this.currentClaims = parseToken(this.currentToken);
    }

    public String getCurrentToken() {
        return currentToken;
    }

    public TokenClaims getCurrentClaims() {
        return currentClaims;
    }

    public boolean isTokenValid() {
        return currentClaims != null && !currentClaims.isExpired();
    }

    /**
     * Synthesizes a mock JWT token for offline development mode.
     */
    public String generateMockToken(String username, String role) {
        long now = System.currentTimeMillis() / 1000L;
        long exp = now + 86400; // 24 hours

        String headerJson = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
        String payloadJson = String.format(
                "{\"iss\":\"sanjivani-auth\",\"sub\":\"%s\",\"aud\":\"ashrama-app\",\"iat\":%d,\"exp\":%d,\"role\":\"%s\"}",
                username, now, exp, role
        );

        String b64Header = Base64.encodeToString(headerJson.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP | Base64.URL_SAFE);
        String b64Payload = Base64.encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP | Base64.URL_SAFE);

        byte[] mockSigData = (b64Header + "." + b64Payload + ".session_salt").getBytes(StandardCharsets.UTF_8);
        String b64Signature = Base64.encodeToString(mockSigData, Base64.NO_WRAP | Base64.URL_SAFE);

        return b64Header + "." + b64Payload + "." + b64Signature;
    }

    /**
     * Decodes and parses token claims without cryptographic validation.
     */
    public TokenClaims parseToken(String token) {
        if (token == null) return null;
        String[] parts = token.split("\\.");
        if (parts.length < 2) return null;

        try {
            byte[] payloadBytes = Base64.decode(parts[1], Base64.URL_SAFE);
            String json = new String(payloadBytes, StandardCharsets.UTF_8);

            // Simple parser for well-known mock fields
            String sub = extractJsonString(json, "sub", "anonymous");
            String iss = extractJsonString(json, "iss", "sanjivani-auth");
            String aud = extractJsonString(json, "aud", "ashrama-app");
            long iat = extractJsonLong(json, "iat", System.currentTimeMillis() / 1000L);
            long exp = extractJsonLong(json, "exp", iat + 3600);

            TokenClaims claims = new TokenClaims(iss, sub, aud, iat, exp);
            claims.putClaim("role", extractJsonString(json, "role", "SEEKER"));
            return claims;
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse token payload", e);
            return null;
        }
    }

    private static String extractJsonString(String json, String key, String defVal) {
        String marker = "\"" + key + "\":\"";
        int idx = json.indexOf(marker);
        if (idx == -1) return defVal;
        int start = idx + marker.length();
        int end = json.indexOf("\"", start);
        return end != -1 ? json.substring(start, end) : defVal;
    }

    private static long extractJsonLong(String json, String key, long defVal) {
        String marker = "\"" + key + "\":";
        int idx = json.indexOf(marker);
        if (idx == -1) return defVal;
        int start = idx + marker.length();
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
            end++;
        }
        try {
            return Long.parseLong(json.substring(start, end));
        } catch (Exception e) {
            return defVal;
        }
    }
}
