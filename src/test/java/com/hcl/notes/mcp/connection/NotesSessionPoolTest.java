package com.hcl.notes.mcp.connection;

import lotus.domino.Session;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotesSessionPoolTest {

    @Test
    void withSessionExecutesCallbackAndReturnsSession() throws Exception {
        Session mockSession = mock(Session.class);
        NotesSessionPool pool = new NotesSessionPool(() -> mockSession, 2, 5000);

        String result = pool.withSession(s -> "done");
        assertThat(result).isEqualTo("done");

        pool.shutdown();
    }

    @Test
    void withSessionWrapsExceptionAsNotesOperationException() throws Exception {
        Session mockSession = mock(Session.class);
        NotesSessionPool pool = new NotesSessionPool(() -> mockSession, 2, 5000);

        assertThatThrownBy(() -> pool.withSession(s -> { throw new RuntimeException("fail"); }))
                .isInstanceOf(NotesOperationException.class);

        pool.shutdown();
    }

    @Test
    void borrowCreatesSessionOnEmptyPool() throws Exception {
        Session mockSession = mock(Session.class);
        AtomicInteger createCount = new AtomicInteger(0);
        NotesSessionPool pool = new NotesSessionPool(
                () -> { createCount.incrementAndGet(); return mockSession; },
                5, 5000);

        Session s1 = pool.borrow();
        assertThat(s1).isSameAs(mockSession);
        assertThat(createCount.get()).isEqualTo(1);

        pool.returnSession(s1);

        Session s2 = pool.borrow();
        assertThat(createCount.get()).isEqualTo(1); // reused, not created again

        pool.shutdown();
    }
}
