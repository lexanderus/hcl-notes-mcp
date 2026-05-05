package com.hcl.notes.mcp.connection;

import jakarta.annotation.PreDestroy;
import lotus.domino.NotesThread;
import lotus.domino.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Manages a single active {@link NotesJniChannel} and provides the
 * {@link #withSession(SessionCallback)} entry point for all Notes operations.
 *
 * <h3>Thread-affinity</h3>
 * Notes JNI is thread-affine: Session and all Domino objects must be created and used on the
 * SAME thread that called {@code sinitThread()}. All execution is serialised on one dedicated
 * "notes-jni-N" thread inside the active channel.
 *
 * <h3>Hang recovery</h3>
 * When {@code notesGetViewEntries} (or similar) hangs on a large production database, Notes JNI
 * cannot be interrupted. The hung task keeps the single executor thread busy, causing every
 * subsequent request to queue behind it for potentially 120+ seconds.
 *
 * <p>Strategy on {@link TimeoutException}:
 * <ol>
 *   <li>Atomically replace the active channel with a fresh one (CAS on {@code activeChannel}).
 *   <li>Abandon the stuck channel — its executor is shut down (rejects new tasks); the hung
 *       JNI task continues in background until Notes unblocks.</li>
 *   <li>Throw {@link NotesOperationException} to the caller immediately.</li>
 *   <li>All subsequent requests use the new channel.</li>
 * </ol>
 *
 * <h3>Lazy initialisation</h3>
 * The init task (sinitThread + createSession) is submitted at construction but does NOT block
 * the constructor. Spring Boot can finish starting and respond to the MCP handshake before Notes
 * JNI is ready. The first actual {@code withSession()} call waits for init to complete.
 */
public class NotesSessionPool {

    private static final Logger log = LoggerFactory.getLogger(NotesSessionPool.class);

    private final Supplier<Session> sessionFactory;
    private final long timeoutMs;
    private final Runnable threadInit;
    private final Runnable threadTerm;

    private final AtomicInteger channelSeq = new AtomicInteger(0);
    private final AtomicReference<NotesJniChannel> activeChannel = new AtomicReference<>();

    public NotesSessionPool(Supplier<Session> sessionFactory, long timeoutMs) {
        this(sessionFactory, timeoutMs, NotesThread::sinitThread, NotesThread::stermThread);
    }

    /** Package-private: allows unit tests to inject no-op thread init/term. */
    NotesSessionPool(Supplier<Session> sessionFactory, long timeoutMs,
                     Runnable threadInit, Runnable threadTerm) {
        this.sessionFactory = sessionFactory;
        this.timeoutMs = timeoutMs;
        this.threadInit = threadInit;
        this.threadTerm = threadTerm;
        activeChannel.set(createChannel());
    }

    /**
     * Executes {@code callback} on the active JNI thread with the shared Session.
     *
     * <p>If Notes JNI initialization is still in progress (lazy startup), this method waits
     * for it to complete before submitting the callback.
     *
     * <p>On timeout: the hung channel is replaced atomically with a fresh one; the caller
     * receives a {@link NotesOperationException} immediately and subsequent calls use the
     * new channel.
     *
     * <p><b>Contract:</b> never let Session or any Domino object escape the callback.
     * All Notes objects are owned by the notes-jni-N thread.
     */
    public <T> T withSession(SessionCallback<T> callback) {
        NotesJniChannel ch = activeChannel.get();
        ch.awaitInit(timeoutMs);

        try {
            return ch.execute(callback, timeoutMs);
        } catch (TimeoutException e) {
            log.error("Notes operation timed out after {}ms on '{}' — abandoning hung channel, "
                    + "creating replacement", timeoutMs, ch.name());
            recoverFromHang(ch);
            throw new NotesOperationException(
                    "Notes operation timed out after " + timeoutMs + "ms", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        NotesJniChannel ch = activeChannel.get();
        if (ch != null) {
            ch.shutdownGracefully(15_000);
        }
    }

    // ─── internal ────────────────────────────────────────────────────────────

    private NotesJniChannel createChannel() {
        String name = "notes-jni-" + channelSeq.incrementAndGet();
        return new NotesJniChannel(name, sessionFactory, threadInit, threadTerm);
    }

    /**
     * Atomically replaces the stuck channel with a fresh one.
     * If another concurrent timeout already did the swap, this is a no-op.
     */
    private void recoverFromHang(NotesJniChannel stuckChannel) {
        NotesJniChannel fresh = createChannel();
        if (activeChannel.compareAndSet(stuckChannel, fresh)) {
            log.info("Channel replaced: '{}' → '{}'", stuckChannel.name(), fresh.name());
            stuckChannel.abandon();
        } else {
            // Another concurrent caller already recovered; discard the channel we just created.
            log.info("Recovery already completed by concurrent caller; discarding redundant channel '{}'",
                    fresh.name());
            fresh.abandon(); // shut down executor immediately — nothing queued on it yet
        }
    }

    @FunctionalInterface
    public interface SessionCallback<T> {
        T execute(Session session) throws Exception;
    }
}
