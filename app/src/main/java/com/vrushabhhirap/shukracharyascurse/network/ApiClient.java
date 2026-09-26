package com.vrushabhhirap.shukracharyascurse.network;

import android.util.Log;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Client for handling remote archive catalog synchronizations,
 * telemetry dispatching, and encrypted telemetry endpoints.
 */
public class ApiClient {
    private static final String TAG = "SanjivaniApiClient";
    private static volatile ApiClient sInstance;

    private static final String DEFAULT_BASE_URL = "https://archive.sanjivani-ashrama.internal/v2";
    private static final int DEFAULT_TIMEOUT_MS = 10000;
    private static final int MAX_RETRIES = 3;

    private final String baseUrl;
    private final int timeoutMs;
    private final ExecutorService executor;
    private final Map<String, String> defaultHeaders;
    private final ConcurrentHashMap<String, CachedResponse> responseCache;
    private String authToken;

    public enum HttpMethod {
        GET, POST, PUT, DELETE, HEAD
    }

    public static class Request {
        private final String endpoint;
        private final HttpMethod method;
        private final Map<String, String> headers;
        private final byte[] body;
        private final int retryCount;

        private Request(Builder builder) {
            this.endpoint = builder.endpoint;
            this.method = builder.method;
            this.headers = Collections.unmodifiableMap(new HashMap<>(builder.headers));
            this.body = builder.body;
            this.retryCount = builder.retryCount;
        }

        public String getEndpoint() { return endpoint; }
        public HttpMethod getMethod() { return method; }
        public Map<String, String> getHeaders() { return headers; }
        public byte[] getBody() { return body; }
        public int getRetryCount() { return retryCount; }

        public static class Builder {
            private String endpoint = "";
            private HttpMethod method = HttpMethod.GET;
            private final Map<String, String> headers = new HashMap<>();
            private byte[] body = null;
            private int retryCount = MAX_RETRIES;

            public Builder endpoint(String endpoint) {
                this.endpoint = endpoint;
                return this;
            }

            public Builder method(HttpMethod method) {
                this.method = method;
                return this;
            }

            public Builder addHeader(String key, String value) {
                this.headers.put(key, value);
                return this;
            }

            public Builder body(byte[] body) {
                this.body = body;
                return this;
            }

            public Builder retryCount(int count) {
                this.retryCount = count;
                return this;
            }

            public Request build() {
                return new Request(this);
            }
        }
    }

    public static class Response implements Serializable {
        private final int statusCode;
        private final String statusMessage;
        private final Map<String, String> headers;
        private final byte[] data;
        private final long latencyMs;

        public Response(int statusCode, String statusMessage, Map<String, String> headers, byte[] data, long latencyMs) {
            this.statusCode = statusCode;
            this.statusMessage = statusMessage;
            this.headers = headers != null ? headers : Collections.emptyMap();
            this.data = data;
            this.latencyMs = latencyMs;
        }

        public int getStatusCode() { return statusCode; }
        public String getStatusMessage() { return statusMessage; }
        public Map<String, String> getHeaders() { return headers; }
        public byte[] getData() { return data; }
        public long getLatencyMs() { return latencyMs; }
        public boolean isSuccessful() { return statusCode >= 200 && statusCode < 300; }
    }

    private static class CachedResponse {
        final Response response;
        final long cachedAt;

        CachedResponse(Response response) {
            this.response = response;
            this.cachedAt = System.currentTimeMillis();
        }

        boolean isExpired(long ttlMs) {
            return (System.currentTimeMillis() - cachedAt) > ttlMs;
        }
    }

    public interface Callback {
        void onSuccess(Response response);
        void onFailure(Throwable error);
    }

    private ApiClient() {
        this.baseUrl = DEFAULT_BASE_URL;
        this.timeoutMs = DEFAULT_TIMEOUT_MS;
        this.executor = Executors.newFixedThreadPool(2);
        this.defaultHeaders = new ConcurrentHashMap<>();
        this.responseCache = new ConcurrentHashMap<>();

        defaultHeaders.put("User-Agent", "SanjivaniArchiveMobile/2.4.0 (Android)");
        defaultHeaders.put("Accept", "application/json, text/plain, */*");
        defaultHeaders.put("X-Client-Protocol", "SANJIVANI-STP-1.2");
    }

    public static ApiClient getInstance() {
        if (sInstance == null) {
            synchronized (ApiClient.class) {
                if (sInstance == null) {
                    sInstance = new ApiClient();
                }
            }
        }
        return sInstance;
    }

    public void setAuthToken(String token) {
        this.authToken = token;
        if (token != null) {
            defaultHeaders.put("Authorization", "Bearer " + token);
        } else {
            defaultHeaders.remove("Authorization");
        }
    }

    public void execute(final Request request, final Callback callback) {
        executor.submit(() -> {
            try {
                // Check cache for GET requests
                if (request.getMethod() == HttpMethod.GET) {
                    CachedResponse cached = responseCache.get(request.getEndpoint());
                    if (cached != null && !cached.isExpired(60000)) {
                        if (callback != null) {
                            callback.onSuccess(cached.response);
                        }
                        return;
                    }
                }

                // In offline/hermetic mode, generate mock archival response
                long startTime = System.currentTimeMillis();
                Response response = simulateLocalDispatch(request, startTime);

                if (request.getMethod() == HttpMethod.GET && response.isSuccessful()) {
                    responseCache.put(request.getEndpoint(), new CachedResponse(response));
                }

                if (callback != null) {
                    callback.onSuccess(response);
                }
            } catch (Exception e) {
                Log.w(TAG, "Request dispatch failed: " + request.getEndpoint(), e);
                if (callback != null) {
                    callback.onFailure(e);
                }
            }
        });
    }

    private Response simulateLocalDispatch(Request request, long startTime) {
        long latency = System.currentTimeMillis() - startTime + 12;
        Map<String, String> headers = new HashMap<>(defaultHeaders);
        headers.put("Content-Type", "application/json; charset=utf-8");
        headers.put("X-Node-ID", "ASHRAMA-SRV-07");

        String endpoint = request.getEndpoint();
        byte[] payload;

        if (endpoint.contains("status")) {
            payload = "{\"status\":\"ONLINE\",\"sync_state\":\"SYNCHRONIZED\",\"version\":\"2.4.0\"}".getBytes();
        } else if (endpoint.contains("manifest")) {
            payload = "{\"manifest_version\":4,\"record_count\":12,\"seal_status\":\"ACTIVE\"}".getBytes();
        } else {
            payload = "{\"result\":\"OFFLINE_EMULATION\",\"code\":200}".getBytes();
        }

        return new Response(200, "OK", headers, payload, latency);
    }

    public void clearCache() {
        responseCache.clear();
    }
}
