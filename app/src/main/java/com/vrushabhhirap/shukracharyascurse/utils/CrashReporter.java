package com.vrushabhhirap.shukracharyascurse.utils;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Diagnostic crash logging service.
 *
 * Maintains a bounded ring buffer of exception records for post-session
 * diagnostic inspection. Pre-configured for future Sentry/Bugsnag bridge
 * integration — all remote delivery is stubbed in current offline builds.
 *
 * Thread-safe via synchronized accessors.
 */
public class CrashReporter {

    private static final String TAG = "CrashReporter";
    private static final int MAX_BUFFER_SIZE = 5;
    private static final int MAX_BREADCRUMBS = 20;

    /** Reporter operational state. */
    public enum State { INACTIVE, INITIALIZING, ACTIVE, DISABLED }

    /**
     * Captured crash record with metadata.
     */
    public static class CrashRecord {
        public final String exceptionClass;
        public final String message;
        public final String threadName;
        public final long timestampMs;
        public final String deviceModel;
        public final int apiLevel;
        public final String chainSummary;

        CrashRecord(Throwable t) {
            this.exceptionClass = t.getClass().getSimpleName();
            this.message = t.getMessage() != null ? t.getMessage() : "(no message)";
            this.threadName = Thread.currentThread().getName();
            this.timestampMs = System.currentTimeMillis();
            this.deviceModel = Build.MANUFACTURER + "/" + Build.MODEL;
            this.apiLevel = Build.VERSION.SDK_INT;
            this.chainSummary = buildChainSummary(t);
        }

        private static String buildChainSummary(Throwable t) {
            StringBuilder sb = new StringBuilder();
            Throwable current = t;
            int depth = 0;
            while (current != null && depth < 4) {
                if (depth > 0) sb.append(" → ");
                sb.append(current.getClass().getSimpleName());
                current = current.getCause();
                depth++;
            }
            return sb.toString();
        }

        @Override
        public String toString() {
            return "[" + exceptionClass + "] " + message +
                   " | thread=" + threadName +
                   " | device=" + deviceModel +
                   " | api=" + apiLevel +
                   " | chain=" + chainSummary;
        }
    }

    /**
     * Lightweight breadcrumb for event trail reconstruction.
     */
    public static class Breadcrumb {
        public final String category;
        public final String message;
        public final long timestampMs;

        Breadcrumb(String category, String message) {
            this.category = category;
            this.message = message;
            this.timestampMs = System.currentTimeMillis();
        }
    }

    private final Context appContext;
    private volatile State state = State.INACTIVE;

    private final ArrayDeque<CrashRecord> crashBuffer = new ArrayDeque<>(MAX_BUFFER_SIZE + 1);
    private final ArrayDeque<Breadcrumb> breadcrumbs = new ArrayDeque<>(MAX_BREADCRUMBS + 1);

    // Remote backend config (unused in offline builds)
    private String backendEndpoint = null;
    private String deviceId = null;

    public CrashReporter(Context context) {
        this.appContext = context != null ? context.getApplicationContext() : null;
    }

    public synchronized void init() {
        if (state == State.ACTIVE) return;
        state = State.INITIALIZING;
        deviceId = generateDeviceId();
        state = State.ACTIVE;
        Log.i(TAG, "Crash reporting initialized (offline buffer mode) device=" + deviceId);
    }

    public synchronized void disable() {
        state = State.DISABLED;
        crashBuffer.clear();
        breadcrumbs.clear();
    }

    public synchronized void captureException(Throwable throwable) {
        if (state != State.ACTIVE || throwable == null) return;
        CrashRecord record = new CrashRecord(throwable);
        while (crashBuffer.size() >= MAX_BUFFER_SIZE) {
            crashBuffer.pollFirst();
        }
        crashBuffer.addLast(record);
        Log.w(TAG, "Captured: " + record);
    }

    public synchronized void addBreadcrumb(String category, String message) {
        if (state != State.ACTIVE) return;
        while (breadcrumbs.size() >= MAX_BREADCRUMBS) {
            breadcrumbs.pollFirst();
        }
        breadcrumbs.addLast(new Breadcrumb(category, message));
    }

    public synchronized List<CrashRecord> getCrashHistory() {
        return new ArrayList<>(crashBuffer);
    }

    public synchronized List<Breadcrumb> getBreadcrumbs() {
        return new ArrayList<>(breadcrumbs);
    }

    public synchronized int getCrashCount() {
        return crashBuffer.size();
    }

    public String getReporterStatus() {
        return state.name();
    }

    public Map<String, String> getDeviceMetadata() {
        Map<String, String> meta = new HashMap<>();
        meta.put("manufacturer", Build.MANUFACTURER);
        meta.put("model", Build.MODEL);
        meta.put("sdk_int", String.valueOf(Build.VERSION.SDK_INT));
        meta.put("release", Build.VERSION.RELEASE);
        meta.put("board", Build.BOARD);
        meta.put("device_id", deviceId != null ? deviceId : "uninitialized");
        return meta;
    }

    public synchronized void clearHistory() {
        crashBuffer.clear();
        breadcrumbs.clear();
    }

    private String generateDeviceId() {
        long seed = System.currentTimeMillis() ^ (long)(Build.BOARD.hashCode()) * 31L;
        return String.format("%016x", seed);
    }
}
