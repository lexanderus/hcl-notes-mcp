package com.hcl.notes.mcp.connection;

import lotus.domino.Session;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class NotesSessionPool {

    private final ArrayBlockingQueue<Session> pool;
    private final long timeoutMs;

    public NotesSessionPool(Supplier<Session> factory, int size, long timeoutMs) {
        this.timeoutMs = timeoutMs;
        this.pool = new ArrayBlockingQueue<>(size);
        for (int i = 0; i < size; i++) {
            pool.offer(factory.get());
        }
    }

    public Session borrow() {
        try {
            Session session = pool.poll(timeoutMs, TimeUnit.MILLISECONDS);
            if (session == null) {
                throw new IllegalStateException("Session pool timed out after " + timeoutMs + "ms");
            }
            return session;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for Notes session", e);
        }
    }

    public void returnSession(Session session) {
        pool.offer(session);
    }

    public <T> T withSession(SessionCallback<T> callback) {
        Session session = borrow();
        try {
            return callback.execute(session);
        } catch (Exception e) {
            throw new NotesOperationException("Notes operation failed", e);
        } finally {
            returnSession(session);
        }
    }

    @FunctionalInterface
    public interface SessionCallback<T> {
        T execute(Session session) throws Exception;
    }
}
