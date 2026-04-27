package com.hcl.notes.mcp.connection;

import jakarta.annotation.PreDestroy;
import lotus.domino.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Real connection pool for Notes sessions.
 * Sessions are reused across calls. New sessions are created on demand when pool is empty.
 * Timed-out borrows throw an exception instead of blocking forever.
 */
public class NotesSessionPool {

    private static final Logger log = LoggerFactory.getLogger(NotesSessionPool.class);

    private final ArrayBlockingQueue<Session> pool;
    private final Supplier<Session> sessionFactory;
    private final int maxSize;
    private final long timeoutMs;
    private final AtomicInteger totalCreated = new AtomicInteger(0);

    public NotesSessionPool(Supplier<Session> sessionFactory, int maxSize, long timeoutMs) {
        this.sessionFactory = sessionFactory;
        this.maxSize = maxSize;
        this.timeoutMs = timeoutMs;
        this.pool = new ArrayBlockingQueue<>(maxSize);

        log.info("NotesSessionPool created: max={}, timeout={}ms", maxSize, timeoutMs);
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down NotesSessionPool: {} sessions in pool, {} total created",
                pool.size(), totalCreated.get());

        Session session;
        while ((session = pool.poll()) != null) {
            try { session.recycle(); } catch (Exception ignore) {}
        }
    }

    public <T> T withSession(SessionCallback<T> callback) {
        Session session = borrow();
        try {
            T result = callback.execute(session);
            return result;
        } catch (NotesOperationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Notes operation failed: {}", e.getMessage(), e);
            throw new NotesOperationException("Notes operation failed", e);
        } finally {
            returnSession(session);
        }
    }

    public Session borrow() {
        Session existing = pool.poll();
        if (existing != null) {
            return existing;
        }

        Session newSession = createWithTimeout();
        totalCreated.incrementAndGet();
        log.debug("Created new session (total created: {}, pool size: {})",
                totalCreated.get(), pool.size());
        return newSession;
    }

    private Session createWithTimeout() {
        try {
            return CompletableFuture.supplyAsync(sessionFactory)
                    .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .get();
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof java.util.concurrent.TimeoutException) {
                throw new NotesOperationException(
                        "Timed out waiting for Notes session after " + timeoutMs + "ms", cause);
            }
            throw new NotesOperationException("Failed to create Notes session",
                    cause != null ? cause : e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NotesOperationException("Interrupted while creating Notes session", e);
        }
    }

    public void returnSession(Session session) {
        if (session == null) return;
        if (!pool.offer(session)) {
            // Pool is full or closed — recycle immediately
            try { session.recycle(); } catch (Exception ignore) {}
        }
    }

    public int availableSessions() {
        return pool.size();
    }

    public int totalCreated() {
        return totalCreated.get();
    }

    @FunctionalInterface
    public interface SessionCallback<T> {
        T execute(Session session) throws Exception;
    }
}
