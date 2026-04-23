package com.hcl.notes.mcp.connection;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import lotus.domino.Session;

class NotesSessionPoolTest {

    @Test
    void borrowAndReturnSession() throws Exception {
        Session mockSession = mock(Session.class);
        NotesSessionPool pool = new NotesSessionPool(() -> mockSession, 2, 1000);

        Session borrowed = pool.borrow();
        assertThat(borrowed).isSameAs(mockSession);

        pool.returnSession(borrowed);
        Session borrowedAgain = pool.borrow();
        assertThat(borrowedAgain).isSameAs(mockSession);
    }

    @Test
    void borrowTimesOutWhenPoolExhausted() {
        Session mockSession = mock(Session.class);
        NotesSessionPool pool = new NotesSessionPool(() -> mockSession, 1, 100);

        pool.borrow();
        assertThatThrownBy(pool::borrow)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("timed out");
    }
}
