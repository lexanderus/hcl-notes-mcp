package com.hcl.notes.mcp.connection;

import lotus.domino.NotesException;
import lotus.domino.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * A single JNI execution channel: one dedicated thread with sinitThread / stermThread
 * lifecycle, one Session, fully serialized execution.
 *
 * <p><b>Thread-affinity rule</b>: Notes JNI Session and all Domino objects (Database, View,
 * Document, …) must be created AND used on the SAME thread that called sinitThread(). The
 * callback passed to {@link #execute} runs on that thread; never let Domino objects escape
 * the callback.
 *
 * <p><b>Recovery</b>: when a hung JNI call (e.g. getViewEntries on a large DB) causes a
 * {@link TimeoutException}, the caller should {@link #abandon()} this channel and create a
 * fresh one. The hung task continues running on the old thread in the background — Notes JNI
 * cannot be interrupted — but it no longer blocks new requests.
 */
class NotesJniChannel {

    private static final Logger log = LoggerFactory.getLogger(NotesJniChannel.class);

    private final String name;
    private final Supplier<Session> sessionFactory;
    private final Runnable threadTerm;
    private final ExecutorService executor;
    private volatile Session session;
    /** Non-null while the init task is still in progress; set to null once complete. */
    private volatile Future<?> initFuture;

    /**
     * Creates a channel and immediately (but asynchronously) runs sinitThread + createSession
     * on the dedicated JNI thread. The constructor does NOT block.
     */
    NotesJniChannel(String name, Supplier<Session> sessionFactory,
                    Runnable threadInit, Runnable threadTerm) {
        this.name = name;
        this.sessionFactory = sessionFactory;
        this.threadTerm = threadTerm;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(false);
            return t;
        });
        this.initFuture = executor.submit(() -> {
            threadInit.run();
            session = sessionFactory.get();
            try {
                log.info("Notes session initialized on '{}': user={}",
                        name, session.getUserName());
            } catch (NotesException e) {
                log.info("Notes session initialized on '{}'", name);
            }
        });
    }

    /** Logical name of this channel (e.g. "notes-jni-1"). */
    String name() { return name; }

    /**
     * Waits (up to {@code timeoutMs}) for the initialization task to complete.
     * Called once before the first {@link #execute} on this channel.
     *
     * @throws NotesOperationException if initialization timed out, failed, or was interrupted.
     */
    void awaitInit(long timeoutMs) {
        Future<?> f = initFuture;
        if (f == null) return; // already done
        try {
            f.get(timeoutMs, TimeUnit.MILLISECONDS);
            initFuture = null; // mark complete; subsequent calls skip this branch
        } catch (TimeoutException e) {
            throw new NotesOperationException(
                    "Notes session init timed out after " + timeoutMs + "ms on '" + name + "'", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new NotesOperationException(
                    "Notes session init failed on '" + name + "': " + cause.getMessage(), cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NotesOperationException(
                    "Notes session init interrupted on '" + name + "'", e);
        }
    }

    /**
     * Submits {@code callback} to the JNI thread and blocks until completion or timeout.
     *
     * <p>On session-dead errors the session is automatically recreated and the callback
     * retried once.
     *
     * @throws TimeoutException         if the operation does not complete within {@code timeoutMs}.
     *                                  The caller should {@link #abandon()} this channel and
     *                                  create a fresh one — the hung task continues in background.
     * @throws NotesOperationException  for Notes API errors or other runtime failures.
     */
    <T> T execute(NotesSessionPool.SessionCallback<T> callback, long timeoutMs)
            throws TimeoutException {
        Future<T> future = executor.submit(() -> {
            try {
                return callback.execute(session);
            } catch (NotesException e) {
                if (isSessionDead(e)) {
                    log.warn("Notes session dead on '{}' (id={}, text={}), recreating...",
                            name, e.id, e.text);
                    recreateSessionOnJniThread();
                    return callback.execute(session);
                }
                throw new NotesOperationException(
                        "Notes API error [id=" + e.id + ", text=" + e.text + "]", e);
            } catch (NotesOperationException e) {
                throw e;
            } catch (Exception e) {
                throw new NotesOperationException(
                        "Notes operation failed: " + e.getMessage(), e);
            }
        });

        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
            // TimeoutException propagates as-is (checked) — caller decides whether to recover
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NotesOperationException noe) throw noe;
            throw new NotesOperationException("Notes operation failed",
                    cause != null ? cause : e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NotesOperationException("Notes operation interrupted", e);
        }
    }

    /**
     * Abandons this channel after a hang-induced timeout.
     *
     * <p>Shuts down the executor so no new tasks are accepted. The stuck JNI task continues
     * running in the background until Notes eventually unblocks. stermThread() may or may not
     * be called — Notes JNI cannot be forcibly interrupted, so we accept this as a leak for
     * the rare hang case (the JVM exit handler cleans up).
     */
    void abandon() {
        log.warn("Abandoning stuck channel '{}' — hung task will complete in background", name);
        executor.shutdown();
    }

    /**
     * Gracefully shuts down this channel: recycles the session and calls stermThread() on
     * the JNI thread, then shuts the executor down.
     */
    void shutdownGracefully(long timeoutMs) {
        log.info("Shutting down Notes JNI channel '{}'...", name);
        Future<?> cleanup = executor.submit(() -> {
            try {
                if (session != null) {
                    session.recycle();
                    session = null;
                }
            } catch (Exception e) {
                log.warn("Error recycling session on '{}': {}", name, e.getMessage());
            } finally {
                try { threadTerm.run(); } catch (Exception ignored) {}
            }
        });
        executor.shutdown();
        try {
            cleanup.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("Channel '{}' cleanup did not finish cleanly: {}", name, e.getMessage());
        }
        log.info("Notes JNI channel '{}' shutdown complete.", name);
    }

    // ─── internal ────────────────────────────────────────────────────────────

    /** Called on the JNI thread: sinitThread() already in effect, just replace session. */
    private void recreateSessionOnJniThread() {
        try {
            if (session != null) {
                try { session.recycle(); } catch (Exception ignore) {}
                session = null;
            }
            session = sessionFactory.get();
            log.info("Notes session recreated on '{}'", name);
        } catch (Exception e) {
            throw new NotesOperationException("Failed to recreate Notes session on '" + name + "'", e);
        }
    }

    private static boolean isSessionDead(NotesException e) {
        // 4063 = Object has been removed or recycled
        // 4376 = Notes API not initialized
        return e.id == 4063 || e.id == 4376;
    }
}
