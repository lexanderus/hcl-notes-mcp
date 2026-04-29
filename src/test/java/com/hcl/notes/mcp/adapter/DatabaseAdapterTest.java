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
        session  = mock(Session.class);
        database = mock(Database.class);
        pool     = mock(NotesSessionPool.class);
        when(pool.withSession(any())).thenAnswer(inv ->
                inv.getArgument(0, NotesSessionPool.SessionCallback.class).execute(session));
        when(session.getDatabase("domino.host", "mail/jdoe.nsf")).thenReturn(database);
        when(database.isOpen()).thenReturn(true);
        adapter = new DatabaseAdapter(pool);
    }

    // --- openDatabase ---

    @Test
    void openDatabase_returnsTitle() throws Exception {
        when(database.getTitle()).thenReturn("John's Mail");

        var result = adapter.openDatabase("domino.host!!mail/jdoe.nsf");

        assertThat(result.title()).isEqualTo("John's Mail");
        assertThat(result.databasePath()).isEqualTo("domino.host!!mail/jdoe.nsf");
    }

    @Test
    void openDatabase_recyclesDatabaseAfterUse() throws Exception {
        when(database.getTitle()).thenReturn("Mail");

        adapter.openDatabase("domino.host!!mail/jdoe.nsf");

        verify(database).recycle();
    }

    @Test
    void openDatabase_throwsWhenNotOpen() throws Exception {
        when(database.isOpen()).thenReturn(false);

        assertThatThrownBy(() -> adapter.openDatabase("domino.host!!mail/jdoe.nsf"))
                .isInstanceOf(NotesOperationException.class);
    }

    // --- getDocument ---

    @Test
    void getDocument_returnsDocument() throws Exception {
        Document doc = mock(Document.class);
        when(database.getDocumentByUNID("UNID1")).thenReturn(doc);
        when(doc.getUniversalID()).thenReturn("UNID1");
        when(doc.getCreated()).thenReturn(mock(DateTime.class));
        when(doc.getLastModified()).thenReturn(mock(DateTime.class));
        when(doc.getItems()).thenReturn(new Vector<>());

        NotesDocument result = adapter.getDocument("domino.host!!mail/jdoe.nsf", "UNID1");

        assertThat(result.unid()).isEqualTo("UNID1");
    }

    @Test
    void getDocument_recyclesDatabaseAfterUse() throws Exception {
        Document doc = mock(Document.class);
        when(database.getDocumentByUNID("UNID1")).thenReturn(doc);
        when(doc.getUniversalID()).thenReturn("UNID1");
        when(doc.getCreated()).thenReturn(mock(DateTime.class));
        when(doc.getLastModified()).thenReturn(mock(DateTime.class));
        when(doc.getItems()).thenReturn(new Vector<>());

        adapter.getDocument("domino.host!!mail/jdoe.nsf", "UNID1");

        verify(database).recycle();
        verify(doc).recycle();
    }

    @Test
    void getDocument_returnsNullWhenNotFound() throws Exception {
        when(database.getDocumentByUNID("MISSING")).thenReturn(null);

        NotesDocument result = adapter.getDocument("domino.host!!mail/jdoe.nsf", "MISSING");

        assertThat(result).isNull();
        verify(database).recycle();
    }

    // --- parsePath ---

    @Test
    void parsePath_splitsOnDoubleExclamation() {
        String[] parts = DatabaseAdapter.parsePath("server.host!!path/db.nsf");
        assertThat(parts[0]).isEqualTo("server.host");
        assertThat(parts[1]).isEqualTo("path/db.nsf");
    }

    @Test
    void parsePath_allowsEmptyServer() {
        String[] parts = DatabaseAdapter.parsePath("!!local.nsf");
        assertThat(parts[0]).isEqualTo("");
        assertThat(parts[1]).isEqualTo("local.nsf");
    }

    @Test
    void parsePath_throwsOnMissingDoubleExclamation() {
        assertThatThrownBy(() -> DatabaseAdapter.parsePath("nodoublebang"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parsePath_throwsOnNull() {
        assertThatThrownBy(() -> DatabaseAdapter.parsePath(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parsePath_throwsOnEmptyPathPart() {
        assertThatThrownBy(() -> DatabaseAdapter.parsePath("server!!"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("path part is empty");
    }

    @Test
    void parsePath_throwsOnBlankPathPart() {
        assertThatThrownBy(() -> DatabaseAdapter.parsePath("server!!   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("path part is empty");
    }
}
