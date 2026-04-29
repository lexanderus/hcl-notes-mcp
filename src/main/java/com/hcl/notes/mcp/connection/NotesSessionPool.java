package com.hcl.notes.mcp.connection;

import jakarta.annotation.PreDestroy;
import lotus.domino.NotesException;
import lotus.domino.NotesThread;
import lotus.domino.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * Executes all Notes JNI operations on a single dedicated thread ("notes-jni").
 *
 * Notes JNI is thread-affine: Session and all Domino objects (Database, View, Document…)
 * must be created and used on the SAME thread that called sinitThread().
 * A thread pool (ForkJoinPool/cached) violates this contract and causes crashes/corruption.
 *
 * Lifecycle on the notes-jni thread:
 *   startup  : sinitThread() → createSession()  (async — does NOT block Spring Boot startup)
 *   per-call : callback.execute(session) — fully serialized, no other threads involved
 *   shutdown : session.recycle() → stermThread()
 *
 * Lazy initialization: the Notes JNI init task is submitted immediately at construction
 * but the constructor does NOT block waiting for it. The first actual withSession() call
 * will wait for initialization to complete (up to timeoutMs). This lets Spring Boot
 * finish starting and respond to the MCP handshake before Notes is fully ready.
 */
public class NotesSessionPool {

    private static final Logger log = LoggerFactory.getLogger(NotesSessionPool.class);

    private final ExecutorService executor;
    private final Supplier<Session> sessionFactory;
    private final long timeoutMs;
    private final Runnable threadInit;
    private final Runnable threadTerm;
    private volatile Session session;
    /** Non-null while init is in progress; null once init has completed (successfully or not). */
    private volatile Future<?> initFuture;

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
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "notes-jni");
            t.setDaemon(false);
            return t;
        });
        // Submit init task immediately but do NOT block — Spring Boot can finish
        // its startup and respond to MCP handshake while Notes JNI initializes.
        this.initFuture = executor.submit(this::doInitOnJniThread);
    }

    /** Runs on the notes-jni thread. */
    private void doInitOnJniThread() {
        threadInit.run();
        session = sessionFactory.get();
        try {
            log.info("Notes session initialized on thread '{}': user={}",
                    Thread.currentThread().getName(), session.getUserName());
        } catch (NotesException e) {
            log.info("Notes session initialized on thread '{}'",
                    Thread.currentThread().getName());
        }
    }

    /**
     * If initialization is still in progress, waits for it to complete (up to timeoutMs).
     * Called from withSession() before submitting the user callback.
     */
    private void awaitInit() {
        Future<?> f = initFuture;
        if (f == null) return; // already done
        try {
            f.get(timeoutMs, TimeUnit.MILLISECONDS);
            initFuture = null; // mark complete so subsequent calls skip this
        } catch (TimeoutException e) {
            throw new NotesOperationException(
                    "Notes session init timed out after " + timeoutMs + "ms", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new NotesOperationException("Notes session init failed: " + cause.getMessage(), cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NotesOperationException("Notes session init interrupted", e);
        }
    }

    /**
     * Executes {@code callback} on the notes-jni thread with the shared Session.
     * Blocks the calling thread until the operation completes or times out.
     *
     * If Notes JNI initialization is still in progress (lazy startup), this method
     * waits for it to complete before submitting the callback.
     *
     * Thread safety: all Notes objects created inside the callback are owned by notes-jni.
     * Never leak Session or any Domino object out of the callback.
     */
    public <T> T withSession(SessionCallback<T> callback) {
        awaitInit();
        Future<T> future = executor.submit(() -> {
            try {
                return callback.execute(session);
            } catch (NotesException e) {
                if (isSessionDead(e)) {
                    log.warn("Notes session appears dead (id={}, text={}), recreating...", e.id, e.text);
                    recreateSessionOnExecutorThread();
                    // Retry once after recreation
                    return callback.execute(session);
                }
                throw new NotesOperationException(
                        "Notes API error [id=" + e.id + ", text=" + e.text + "]", e);
            } catch (NotesOperationException e) {
                throw e;
            } catch (Exception e) {
                throw new NotesOperationException("Notes operation failed: " + e.getMessage(), e);
            }
        });

        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            // Notes JNI cannot be interrupted — the task is still running.
            // Subsequent calls will queue on the executor thread.
            log.error("Notes operation timed out after {}ms — Notes JNI cannot be interrupted; "
                    + "executor may be stalled until current operation completes", timeoutMs);
            throw new NotesOperationException(
                    "Notes operation timed out after " + timeoutMs + "ms", e);
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

    /** Called on the notes-jni thread only — sinitThread() already done, just replace session. */
    private void recreateSessionOnExecutorThread() {
        try {
            if (session != null) {
                try { session.recycle(); } catch (Exception ignore) {}
                session = null;
            }
            session = sessionFactory.get();
            log.info("Notes session recreated on thread '{}'", Thread.currentThread().getName());
        } catch (Exception e) {
            throw new NotesOperationException("Failed to recreate Notes session", e);
        }
    }

    private static boolean isSessionDead(NotesException e) {
        // 4063 = Object has been removed or recycled
        // 4376 = Notes API not initialized
        return e.id == 4063 || e.id == 4376;
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down NotesSessionPool (notes-jni thread)...");
        // Drain remaining tasks first, then recycle
        Future<?> cleanup = executor.submit(() -> {
            try {
                if (session != null) {
                    session.recycle();
                    session = null;
                }
            } catch (Exception e) {
                log.warn("Error recycling session on shutdown: {}", e.getMessage());
            } finally {
                try { threadTerm.run(); } catch (Exception ignore) {}
            }
        });
        executor.shutdown();
        try {
            cleanup.get(15, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Notes session cleanup did not finish cleanly: {}", e.getMessage());
        }
        log.info("NotesSessionPool shutdown complete.");
    }

    @FunctionalInterface
    public interface SessionCallback<T> {
        T execute(Session session) throws Exception;
    }
}
