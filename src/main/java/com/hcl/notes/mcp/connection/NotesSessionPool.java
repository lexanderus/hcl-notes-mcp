package com.hcl.notes.mcp.connection;

import lotus.domino.Session;
import java.util.function.Supplier;

public class NotesSessionPool {

    private final Supplier<Session> sessionFactory;
    private final Runnable sessionCleanup;

    public NotesSessionPool(Supplier<Session> sessionFactory, Runnable sessionCleanup,
                            int size, long timeoutMs) {
        this.sessionFactory = sessionFactory;
        this.sessionCleanup = sessionCleanup;
        // size and timeoutMs kept for API compatibility; sessions are created per-call
    }

    public <T> T withSession(SessionCallback<T> callback) {
        Session session = null;
        try {
            session = sessionFactory.get();
            return callback.execute(session);
        } catch (NotesOperationException e) {
            throw e;
        } catch (Exception e) {
            throw new NotesOperationException("Notes operation failed", e);
        } finally {
            if (session != null) {
                try { session.recycle(); } catch (Exception ignore) {}
            }
            sessionCleanup.run();
        }
    }

    @FunctionalInterface
    public interface SessionCallback<T> {
        T execute(Session session) throws Exception;
    }
}
