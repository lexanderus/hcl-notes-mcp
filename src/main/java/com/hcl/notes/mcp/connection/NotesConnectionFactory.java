package com.hcl.notes.mcp.connection;

import com.hcl.notes.mcp.config.NotesConnectionConfig;
import lotus.domino.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Creates LOCAL JNI Notes sessions.
 * REMOTE/CORBA/DIIOP removed per ADR-2 — only LOCAL JNI is supported.
 *
 * NOTE: sinitThread/stermThread lifecycle is managed by NotesExecutor (Phase 1 refactor).
 * This factory is only responsible for creating a Session on the calling thread.
 */
@Component
public class NotesConnectionFactory {

    private static final Logger log = LoggerFactory.getLogger(NotesConnectionFactory.class);

    private final NotesConnectionConfig config;

    public NotesConnectionFactory(NotesConnectionConfig config) {
        this.config = config;
    }

    /**
     * Creates a Notes LOCAL JNI session on the calling thread.
     * The caller is responsible for having called NotesThread.sinitThread() beforehand
     * and calling NotesThread.stermThread() after the session is recycled.
     */
    public Session createSession() {
        log.debug("Creating LOCAL Notes session");
        try {
            String pwd = config.getPassword();
            if (pwd != null && !pwd.isEmpty()) {
                return NotesFactory.createSession((String) null, (String) null, pwd);
            }
            return NotesFactory.createSession();
        } catch (NotesException e) {
            throw new NotesOperationException(
                "Failed to create Notes LOCAL session [id=" + e.id + ", text=" + e.text + "]", e);
        }
    }
}
