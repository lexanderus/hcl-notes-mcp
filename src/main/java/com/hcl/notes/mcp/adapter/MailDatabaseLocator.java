package com.hcl.notes.mcp.adapter;

import com.hcl.notes.mcp.config.NotesConnectionConfig;
import com.hcl.notes.mcp.connection.NotesOperationException;
import lotus.domino.Database;
import lotus.domino.NotesException;
import lotus.domino.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import static com.hcl.notes.mcp.adapter.NotesUtils.recycle;

/**
 * Resolves and opens the user's mail database.
 *
 * Two modes:
 *   1. Local replica (preferred): notes.connection.mail-local-db is set →
 *      opens getDatabase("", path) from Notes Data dir. No server connection needed.
 *   2. Server mode: reads MailServer/MailFile from notes.ini →
 *      opens getDatabase(server, file). Requires Notes router (Notes.exe running).
 *
 * MailFile/MailServer are cached after first read.
 * All access happens on the notes-jni thread (inside withSession callbacks).
 */
@Component
public class MailDatabaseLocator {

    private static final Logger log = LoggerFactory.getLogger(MailDatabaseLocator.class);

    private final NotesConnectionConfig config;
    private volatile String cachedMailFile;
    private volatile String cachedMailServer;

    public MailDatabaseLocator(NotesConnectionConfig config) {
        this.config = config;
    }

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

        // Mode 1: local replica configured explicitly — no server connection needed
        String localDb = config.getMailLocalDb();
        if (localDb != null && !localDb.isBlank()) {
            return openDb(session, "", localDb, label);
        }

        // Mode 2: read MailServer/MailFile from notes.ini (server access)
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
            log.debug("Resolved mail database: server='{}' file='{}'", cachedMailServer, cachedMailFile);
        }

        return openDb(session, cachedMailServer, cachedMailFile, label);
    }

    private Database openDb(Session session, String server, String filePath, String label)
            throws NotesException {
        Database db = session.getDatabase(server, filePath);
        if (db == null) {
            throw new NotesOperationException(
                    "Cannot get " + label + " database: "
                            + (server.isEmpty() ? "(local)" : server) + "!!" + filePath, null);
        }
        if (!db.isOpen()) db.open();
        if (!db.isOpen()) {
            recycle(db);
            throw new NotesOperationException(
                    "Cannot open " + label + " database: "
                            + (server.isEmpty() ? "(local)" : server) + "!!" + filePath, null);
        }
        return db;
    }
}
