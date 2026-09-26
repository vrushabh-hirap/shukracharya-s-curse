package com.vrushabhhirap.shukracharyascurse.network;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.vrushabhhirap.shukracharyascurse.db.ArchiveDatabaseHelper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Background synchronization coordinator for reconciling local manuscript
 * database caches with the simulated remote ashrama repository server.
 */
public class RemoteArchiveSyncService {

    private static final String TAG = "RemoteArchiveSync";

    public enum SyncStatus {
        IDLE,
        INITIALIZING,
        CHECKING_MANIFEST,
        DOWNLOADING_DELTA,
        APPLYING_UPDATES,
        COMPLETED,
        FAILED
    }

    public interface SyncCallback {
        void onProgress(SyncStatus status, int percent);
        void onComplete(boolean success, int recordsUpdated);
    }

    private static volatile RemoteArchiveSyncService sInstance;

    private final Context context;
    private final ExecutorService executor;
    private final Handler mainHandler;
    private final AtomicBoolean isSyncing;
    private final AtomicInteger retryCount;
    private volatile SyncStatus currentStatus;

    public static RemoteArchiveSyncService getInstance(Context context) {
        if (sInstance == null) {
            synchronized (RemoteArchiveSyncService.class) {
                if (sInstance == null) {
                    sInstance = new RemoteArchiveSyncService(context.getApplicationContext());
                }
            }
        }
        return sInstance;
    }

    private RemoteArchiveSyncService(Context context) {
        this.context = context;
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.isSyncing = new AtomicBoolean(false);
        this.retryCount = new AtomicInteger(0);
        this.currentStatus = SyncStatus.IDLE;
    }

    public boolean isSyncRunning() {
        return isSyncing.get();
    }

    public SyncStatus getCurrentStatus() {
        return currentStatus;
    }

    public void startSync(final SyncCallback callback) {
        if (!isSyncing.compareAndSet(false, true)) {
            Log.w(TAG, "Sync operation already in progress. Ignoring duplicate trigger.");
            return;
        }

        updateStatus(SyncStatus.INITIALIZING, 0, callback);

        executor.submit(() -> {
            try {
                // Phase 1: Check remote manifest
                updateStatus(SyncStatus.CHECKING_MANIFEST, 20, callback);
                Thread.sleep(150); // Simulate network latency

                ApiClient.Request manifestReq = new ApiClient.Request.Builder()
                        .endpoint("/manifest/latest")
                        .method(ApiClient.HttpMethod.GET)
                        .build();

                ApiClient.getInstance().execute(manifestReq, new ApiClient.Callback() {
                    @Override
                    public void onSuccess(ApiClient.Response response) {
                        handleManifestResponse(response, callback);
                    }

                    @Override
                    public void onFailure(Throwable error) {
                        Log.w(TAG, "Manifest fetch failed, running local cache fallback", error);
                        finalizeSync(true, 0, callback);
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Fatal exception during sync cycle", e);
                updateStatus(SyncStatus.FAILED, 0, callback);
                finalizeSync(false, 0, callback);
            }
        });
    }

    private void handleManifestResponse(ApiClient.Response response, SyncCallback callback) {
        try {
            updateStatus(SyncStatus.DOWNLOADING_DELTA, 50, callback);
            Thread.sleep(100);

            updateStatus(SyncStatus.APPLYING_UPDATES, 80, callback);

            // Reconcile database state
            ArchiveDatabaseHelper db = ArchiveDatabaseHelper.getInstance(context);
            db.setSyncState("last_manifest_sync", String.valueOf(System.currentTimeMillis()));
            db.logAuditEvent("SYNC_COMPLETED", "Status code: " + response.getStatusCode(), "SYSTEM_WORKER");

            updateStatus(SyncStatus.COMPLETED, 100, callback);
            finalizeSync(true, 3, callback);

        } catch (Exception e) {
            Log.e(TAG, "Error applying delta updates", e);
            finalizeSync(false, 0, callback);
        }
    }

    private void updateStatus(SyncStatus status, int percent, SyncCallback callback) {
        this.currentStatus = status;
        if (callback != null) {
            mainHandler.post(() -> callback.onProgress(status, percent));
        }
    }

    private void finalizeSync(boolean success, int recordsUpdated, SyncCallback callback) {
        isSyncing.set(false);
        this.currentStatus = success ? SyncStatus.COMPLETED : SyncStatus.FAILED;
        if (callback != null) {
            mainHandler.post(() -> callback.onComplete(success, recordsUpdated));
        }
    }

    public void cancelSync() {
        if (isSyncing.get()) {
            isSyncing.set(false);
            this.currentStatus = SyncStatus.IDLE;
            Log.i(TAG, "Sync operation cancelled by caller.");
        }
    }
}
