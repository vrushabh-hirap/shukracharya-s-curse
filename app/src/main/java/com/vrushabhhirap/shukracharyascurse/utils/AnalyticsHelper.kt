package com.vrushabhhirap.shukracharyascurse.utils

import android.util.Log
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Lightweight local telemetry and diagnostics dispatcher.
 *
 * Operates in fully offline mode — all events are buffered to an in-memory ring buffer.
 * Remote transmission is gated behind [isRemoteEnabled] which defaults to false.
 * Designed for forward compatibility with a Sanjivani Analytics backend.
 */
object AnalyticsHelper {

    private const val TAG = "TelemetryDispatcher"
    private const val MAX_QUEUE_SIZE = 100
    private const val FLUSH_INTERVAL_MS = 30_000L

    /** Telemetry status identifier for offline/disconnected mode. */
    const val TELEMETRY_STATUS_OFFLINE = "telemetry_mode:offline"

    enum class EventType(val key: String) {
        LIFECYCLE("lifecycle"),
        SESSION_START("session.start"),
        SESSION_RESUME("session.resume"),
        SESSION_PAUSE("session.pause"),
        SESSION_END("session.end"),
        ARCHIVE_OPEN("archive.open"),
        ARCHIVE_CLOSE("archive.close"),
        AUTH_ATTEMPT("auth.attempt"),
        DECODE_ATTEMPT("decode.attempt"),
        DECODE_SUCCESS("decode.success"),
        DECODE_FAILURE("decode.failure"),
        UI_INTERACTION("ui.interaction"),
        NAVIGATION("navigation"),
        ERROR_CAUGHT("error.caught"),
        PERFORMANCE_MARK("perf.mark"),
        DIAGNOSTIC("diagnostic"),
    }

    data class TelemetryEvent(
        val type: EventType,
        val name: String,
        val params: Map<String, String>,
        val timestampMs: Long = System.currentTimeMillis(),
        val sessionId: String = currentSessionId,
    ) {
        fun serialize(): String {
            val paramStr = params.entries.joinToString(",") { "${it.key}=${it.value}" }
            return "evt=${name};type=${type.key};ts=$timestampMs;sid=$sessionId;$paramStr"
        }
    }

    private val localEventQueue: ConcurrentLinkedQueue<TelemetryEvent> = ConcurrentLinkedQueue()
    private val totalEventsLogged = AtomicLong(0L)
    private val isRemoteEnabled = AtomicBoolean(false)
    private val isFlushing = AtomicBoolean(false)

    private var currentSessionId: String = generateSessionId()
    private var sessionStartMs: Long = System.currentTimeMillis()

    private val flushExecutor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "telemetry-flush").also { it.isDaemon = true }
    }

    init {
        flushExecutor.scheduleAtFixedRate(
            { flushIfNeeded() },
            FLUSH_INTERVAL_MS,
            FLUSH_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        )
    }

    /**
     * Start a new telemetry session.
     */
    @JvmStatic
    fun startSession(tag: String = "DEFAULT") {
        currentSessionId = generateSessionId()
        sessionStartMs = System.currentTimeMillis()
        logTypedEvent(EventType.SESSION_START, "session.start", mapOf("sid" to currentSessionId, "tag" to tag))
    }

    /**
     * Log a telemetry event with name and detail message.
     */
    @JvmStatic
    fun logEvent(type: EventType, name: String, detail: String) {
        logTypedEvent(type, name, mapOf("detail" to detail))
    }

    @JvmStatic
    fun logEvent(name: String, detail: String) {
        logTypedEvent(EventType.UI_INTERACTION, name, mapOf("detail" to detail))
    }

    /**
     * Log a typed telemetry event with optional key/value parameters.
     */
    @JvmStatic
    fun logEvent(eventName: String, params: Map<String, String> = emptyMap()) {
        logTypedEvent(EventType.UI_INTERACTION, eventName, params)
    }

    @JvmStatic
    fun logTypedEvent(type: EventType, name: String, params: Map<String, String> = emptyMap()) {
        val event = TelemetryEvent(type, name, params)
        enqueue(event)
        Log.v(TAG, "Queued: ${event.serialize()}")
    }

    @JvmStatic
    fun logSessionStart() {
        startSession("SESSION_START")
    }

    @JvmStatic
    fun logSessionEnd() {
        val durationMs = System.currentTimeMillis() - sessionStartMs
        logTypedEvent(EventType.SESSION_END, "session.end", mapOf("duration_ms" to durationMs.toString()))
    }

    @JvmStatic
    fun logNavigation(from: String, to: String) {
        logTypedEvent(EventType.NAVIGATION, "nav", mapOf("from" to from, "to" to to))
    }

    @JvmStatic
    fun logDecodeAttempt(resultTag: String) {
        logTypedEvent(EventType.DECODE_ATTEMPT, "decode.attempt", mapOf("result" to resultTag))
    }

    @JvmStatic
    fun logPerformanceMark(label: String, durationMs: Long) {
        logTypedEvent(
            EventType.PERFORMANCE_MARK, "perf",
            mapOf("label" to label, "ms" to durationMs.toString())
        )
    }

    @JvmStatic
    fun logError(errorClass: String, message: String?) {
        logTypedEvent(
            EventType.ERROR_CAUGHT, "error",
            mapOf("class" to errorClass, "msg" to (message ?: "null"))
        )
    }

    private fun enqueue(event: TelemetryEvent) {
        while (localEventQueue.size >= MAX_QUEUE_SIZE) {
            localEventQueue.poll()
        }
        localEventQueue.add(event)
        totalEventsLogged.incrementAndGet()
    }

    @JvmStatic
    fun getQueuedCount(): Int = localEventQueue.size

    @JvmStatic
    fun getTotalLogged(): Long = totalEventsLogged.get()

    @JvmStatic
    fun flush() {
        flushQueue()
    }

    @JvmStatic
    fun flushQueue() {
        localEventQueue.clear()
    }

    private fun flushIfNeeded() {
        if (!isRemoteEnabled.get() || isFlushing.getAndSet(true)) return
        try {
            // Remote delivery not enabled in this build — drain queue silently
            val drained = localEventQueue.size
            localEventQueue.clear()
            if (drained > 0) {
                Log.v(TAG, "Flushed $drained events (offline mode)")
            }
        } finally {
            isFlushing.set(false)
        }
    }

    @JvmStatic
    fun getRecentEvents(limit: Int = 10): List<TelemetryEvent> {
        return localEventQueue.toList().takeLast(limit)
    }

    @JvmStatic
    fun getDiagnosticSummary(): String {
        return "queued=${localEventQueue.size};total=${totalEventsLogged.get()};" +
               "session=${currentSessionId};remote=${isRemoteEnabled.get()}"
    }

    private fun generateSessionId(): String {
        val ts = System.currentTimeMillis()
        val rand = (Math.random() * 0xFFFFFF).toLong()
        return "%08x%06x".format(ts and 0xFFFFFFFFL, rand)
    }
}
