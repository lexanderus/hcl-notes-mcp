package com.hcl.notes.mcp.adapter;

import com.hcl.notes.mcp.config.NotesConnectionConfig;
import com.hcl.notes.mcp.connection.NotesOperationException;
import lotus.domino.Database;
import lotus.domino.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MailDatabaseLocatorTest {

    private Session session;
    private Database db;
    private MailDatabaseLocator locator;

    @BeforeEach
    void setUp() throws Exception {
        session = mock(Session.class);
        db = mock(Database.class);
        locator = new MailDatabaseLocator(new NotesConnectionConfig());
        when(db.isOpen()).thenReturn(true);
    }

    @Test
    void openMailDatabase_opensCorrectDatabase() throws Exception {
        when(session.getEnvironmentString("MailFile",   true)).thenReturn("mail/user.nsf");
        when(session.getEnvironmentString("MailServer", true)).thenReturn("mailsrv");
        when(session.getDatabase("mailsrv", "mail/user.nsf")).thenReturn(db);

        Database result = locator.openMailDatabase(session);

        assertThat(result).isSameAs(db);
        verify(session).getDatabase("mailsrv", "mail/user.nsf");
    }

    @Test
    void openMailDatabase_cachesMailFileAndServer() throws Exception {
        when(session.getEnvironmentString("MailFile",   true)).thenReturn("mail/user.nsf");
        when(session.getEnvironmentString("MailServer", true)).thenReturn("mailsrv");
        when(session.getDatabase("mailsrv", "mail/user.nsf")).thenReturn(db);

        locator.openMailDatabase(session);
        locator.openMailDatabase(session);

        // getEnvironmentString called only once — result is cached
        verify(session, times(1)).getEnvironmentString("MailFile",   true);
        verify(session, times(1)).getEnvironmentString("MailServer", true);
    }

    @Test
    void openMailDatabase_throwsWhenMailFileBlank() throws Exception {
        when(session.getEnvironmentString("MailFile",   true)).thenReturn("");
        when(session.getEnvironmentString("MailServer", true)).thenReturn("");

        assertThatThrownBy(() -> locator.openMailDatabase(session))
                .isInstanceOf(NotesOperationException.class)
                .hasMessageContaining("MailFile not set");
    }

    @Test
    void openMailDatabase_throwsWhenDatabaseNotOpen() throws Exception {
        when(session.getEnvironmentString("MailFile",   true)).thenReturn("mail/user.nsf");
        when(session.getEnvironmentString("MailServer", true)).thenReturn("mailsrv");
        when(session.getDatabase("mailsrv", "mail/user.nsf")).thenReturn(db);
        when(db.isOpen()).thenReturn(false);

        assertThatThrownBy(() -> locator.openMailDatabase(session))
                .isInstanceOf(NotesOperationException.class)
                .hasMessageContaining("Cannot open");
    }

    @Test
    void openMailDatabase_contexLabelAppearsInErrorMessage() throws Exception {
        when(session.getEnvironmentString("MailFile",   true)).thenReturn("mail/user.nsf");
        when(session.getEnvironmentString("MailServer", true)).thenReturn("mailsrv");
        when(session.getDatabase("mailsrv", "mail/user.nsf")).thenReturn(db);
        when(db.isOpen()).thenReturn(false);

        assertThatThrownBy(() -> locator.openMailDatabase(session, "calendar"))
                .isInstanceOf(NotesOperationException.class)
                .hasMessageContaining("calendar");
    }
}
