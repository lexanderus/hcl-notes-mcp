package com.hcl.notes.mcp.adapter;

import com.hcl.notes.mcp.connection.NotesOperationException;
import com.hcl.notes.mcp.connection.NotesSessionPool;
import com.hcl.notes.mcp.model.NotesDocument;
import lotus.domino.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Vector;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DatabaseAdapterTest {

    private NotesSessionPool pool;
    private Session session;
    private Database database;
    private DatabaseAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        session = mock(Session.class);
        database = mock(Database.class);
        pool = mock(NotesSessionPool.class);
        when(pool.withSession(any())).thenAnswer(inv ->
                inv.getArgument(0, NotesSessionPool.SessionCallback.class).execute(session));
        when(session.getDatabase("domino.host", "mail/jdoe.nsf")).thenReturn(database);
        adapter = new DatabaseAdapter(pool);
    }

    @Test
    void openDatabase_returnsTitle() throws Exception {
        when(database.isOpen()).thenReturn(true);
        when(database.getTitle()).thenReturn("John's Mail");

        var result = adapter.openDatabase("domino.host!!mail/jdoe.nsf");
        assertThat(result.title()).isEqualTo("John's Mail");
        assertThat(result.databasePath()).isEqualTo("domino.host!!mail/jdoe.nsf");
    }

    @Test
    void openDatabase_throwsWhenNotFound() throws Exception {
        when(session.getDatabase("domino.host", "bad/path.nsf")).thenReturn(database);
        when(database.isOpen()).thenReturn(false);
        assertThatThrownBy(() -> adapter.openDatabase("domino.host!!bad/path.nsf"))
                .isInstanceOf(NotesOperationException.class);
    }

    @Test
    void getDocument_returnsDocument() throws Exception {
        Document doc = mock(Document.class);
        when(database.isOpen()).thenReturn(true);
        when(database.getDocumentByUNID("UNID1")).thenReturn(doc);
        when(doc.getUniversalID()).thenReturn("UNID1");
        when(doc.getCreated()).thenReturn(mock(DateTime.class));
        when(doc.getLastModified()).thenReturn(mock(DateTime.class));
        when(doc.getItems()).thenReturn(new Vector<>());

        NotesDocument result = adapter.getDocument("domino.host!!mail/jdoe.nsf", "UNID1");
        assertThat(result.unid()).isEqualTo("UNID1");
    }

    @Test
    void parsePath_splitsCorrectly() {
        String[] parts = DatabaseAdapter.parsePath("server.host!!path/db.nsf");
        assertThat(parts[0]).isEqualTo("server.host");
        assertThat(parts[1]).isEqualTo("path/db.nsf");
    }

    @Test
    void parsePath_throwsOnInvalidFormat() {
        assertThatThrownBy(() -> DatabaseAdapter.parsePath("nodoublebang"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
