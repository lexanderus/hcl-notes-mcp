package com.hcl.notes.mcp.adapter;

import com.hcl.notes.mcp.connection.NotesOperationException;
import lotus.domino.Database;
import lotus.domino.NotesException;
import lotus.domino.Session;
import org.springframework.stereotype.Component;

import static com.hcl.notes.mcp.adapter.NotesUtils.recycle;

/**
 * Resolves and opens the user's mail database.
 *
 * MailFile and MailServer are read once from notes.ini via getEnvironmentString()
 * and cached — the values don't change for the lifetime of the Notes session.
 * All access happens on the notes-jni thread (inside withSession callbacks).
 */
@Component
public class MailDatabaseLocator {

    private volatile String cachedMailFile;
    private volatile String cachedMailServer;

    /**
     * Opens and returns the user's mail database.
     * Caller is responsible for recycling the returned Database object.
     */
    public Database openMailDatabase(Session session) throws NotesException {
        return openMailDatabase(session, null);
    }

    /**
     * Opens and returns the user's mail database.
     * @param contextLabel used only in error messages (e.g. "mail", "calendar", "tasks")
     */
    public Database openMailDatabase(Session session, String contextLabel) throws NotesException {
        String label = contextLabel != null ? contextLabel : "mail";

        String mailFile   = cachedMailFile;
        String mailServer = cachedMailServer;

        if (mailFile == null) {
            mailFile   = session.getEnvironmentString("MailFile",   true);
            mailServer = session.getEnvironmentString("MailServer", true);
            if (mailFile == null || mailFile.isBlank()) {
                throw new NotesOperationException(
                        "MailFile not set in notes.ini — cannot open " + label + " database", null);
            }
            cachedMailFile   = mailFile;
            cachedMailServer = mailServer != null ? mailServer : "";
        }

        Database db = session.getDatabase(cachedMailServer, cachedMailFile);
        if (db == null) {
            throw new NotesOperationException(
                    "Cannot get " + label + " database: " + cachedMailServer + "!!" + cachedMailFile, null);
        }
        if (!db.isOpen()) db.open();
        if (!db.isOpen()) {
            recycle(db);
            throw new NotesOperationException(
                    "Cannot open " + label + " database: " + cachedMailServer + "!!" + cachedMailFile, null);
        }
        return db;
    }
}
