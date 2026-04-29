package com.hcl.notes.mcp.connection;

import lotus.domino.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotesSessionPoolTest {

    private NotesSessionPool pool;

    @AfterEach
    void tearDown() {
        if (pool != null) pool.shutdown();
    }

    @Test
    void withSessionExecutesCallbackAndReturnsResult() {
        Session mockSession = mock(Session.class);
        pool = new NotesSessionPool(() -> mockSession, 5000, () -> {}, () -> {});

        String result = pool.withSession(s -> "done");

        assertThat(result).isEqualTo("done");
    }

    @Test
    void withSessionReceivesTheSameSessionInstance() {
        Session mockSession = mock(Session.class);
        pool = new NotesSessionPool(() -> mockSession, 5000, () -> {}, () -> {});

        pool.withSession(s -> {
            assertThat(s).isSameAs(mockSession);
            return null;
        });
    }

    @Test
    void withSessionWrapsRuntimeExceptionAsNotesOperationException() {
        Session mockSession = mock(Session.class);
        pool = new NotesSessionPool(() -> mockSession, 5000, () -> {}, () -> {});

        assertThatThrownBy(() -> pool.withSession(s -> {
            throw new RuntimeException("callback fail");
        })).isInstanceOf(NotesOperationException.class)
                .hasMessageContaining("callback fail");
    }

    @Test
    void withSessionUsesTheSameSessionForMultipleCalls() {
        Session mockSession = mock(Session.class);
        AtomicInteger createCount = new AtomicInteger(0);
        pool = new NotesSessionPool(() -> {
            createCount.incrementAndGet();
            return mockSession;
        }, 5000, () -> {}, () -> {});

        pool.withSession(s -> "a");
        pool.withSession(s -> "b");
        pool.withSession(s -> "c");

        // Session created once at init, never again
        assertThat(createCount.get()).isEqualTo(1);
    }

    @Test
    void withSessionExecutesCallbacksSerially() throws InterruptedException {
        Session mockSession = mock(Session.class);
        pool = new NotesSessionPool(() -> mockSession, 5000, () -> {}, () -> {});
        AtomicInteger concurrentCount = new AtomicInteger(0);
        AtomicInteger maxConcurrent   = new AtomicInteger(0);

        Runnable task = () -> pool.withSession(s -> {
            int current = concurrentCount.incrementAndGet();
            maxConcurrent.updateAndGet(m -> Math.max(m, current));
            Thread.sleep(10);
            concurrentCount.decrementAndGet();
            return null;
        });

        Thread t1 = new Thread(task);
        Thread t2 = new Thread(task);
        t1.start();
        t2.start();
        t1.join(2000);
        t2.join(2000);

        // Single executor thread → max concurrency is always 1
        assertThat(maxConcurrent.get()).isEqualTo(1);
    }
}
