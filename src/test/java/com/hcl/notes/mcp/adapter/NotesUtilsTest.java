package com.hcl.notes.mcp.adapter;

import lotus.domino.Base;
import lotus.domino.NotesException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

class NotesUtilsTest {

    @Test
    void recycleNull_isNoOp() {
        assertThatCode(() -> NotesUtils.recycle(null)).doesNotThrowAnyException();
    }

    @Test
    void recycleValidObject_callsRecycle() throws Exception {
        Base obj = mock(Base.class);
        NotesUtils.recycle(obj);
        verify(obj).recycle();
    }

    @Test
    void recycleThrowingObject_swallowsException() throws Exception {
        Base obj = mock(Base.class);
        doThrow(new NotesException(0, "already recycled")).when(obj).recycle();
        assertThatCode(() -> NotesUtils.recycle(obj)).doesNotThrowAnyException();
    }
}
