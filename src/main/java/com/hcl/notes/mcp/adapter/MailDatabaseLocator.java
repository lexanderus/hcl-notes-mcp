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

        // Mode 1: local replica configured explicitly — no server connection needed.
        // Priority: JVM system property > Spring config > env var.
        // JVM property (-Dnotes.mail.local.db=...) is the most reliable — always visible
        // via System.getProperty() regardless of Spring Boot launcher or env passthrough.
        String localDb = System.getProperty("notes.mail.local.db");
        if (localDb == null || localDb.isBlank()) {
            localDb = config.getMailLocalDb();
        }
        if (localDb == null || localDb.isBlank()) {
            localDb = System.getenv("NOTES_MAIL_LOCAL_DB");
        }
        if (localDb != null && !localDb.isBlank()) {
            // Strip "!!" absolute-path prefix if present — that notation is for the combined
            // "server!!path" string; when calling 3-arg getDatabase(server, path, create)
            // separately, the path must not include the "!!" prefix.
            String localPath = localDb.startsWith("!!") ? localDb.substring(2) : localDb;
            log.debug("Opening local mail replica: '{}'", localPath);
            return openDb(session, "", localPath, label);
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
        Database db = session.getDatabase(server, filePath, false);
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
