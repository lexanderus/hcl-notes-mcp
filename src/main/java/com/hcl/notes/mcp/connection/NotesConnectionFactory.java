package com.hcl.notes.mcp.connection;

import com.hcl.notes.mcp.config.NotesConnectionConfig;
import lotus.domino.*;
import org.springframework.stereotype.Component;

@Component
public class NotesConnectionFactory {

    private final NotesConnectionConfig config;

    public NotesConnectionFactory(NotesConnectionConfig config) {
        this.config = config;
    }

    public NotesSessionPool createPool() {
        Runnable cleanup = config.getMode() == ConnectionMode.LOCAL
                ? () -> { try { NotesThread.stermThread(); } catch (Exception ignore) {} }
                : () -> {};
        return new NotesSessionPool(this::createSession, cleanup,
                config.getPoolSize(), config.getTimeoutMs());
    }

    private Session createSession() {
        try {
            if (config.getMode() == ConnectionMode.REMOTE) {
                return NotesFactory.createSession(
                        config.getServer(), config.getUsername(), config.getPassword());
            } else {
                // LOCAL mode — opens user.id with password (Notes client must be CLOSED)
                NotesThread.sinitThread();
                String pwd = config.getPassword();
                if (pwd != null && !pwd.isEmpty()) {
                    return NotesFactory.createSession((String) null, (String) null, pwd);
                }
                return NotesFactory.createSession();
            }
        } catch (NotesException e) {
            if (config.getMode() == ConnectionMode.LOCAL) {
                NotesThread.stermThread();
            }
            throw new NotesOperationException(
                "Failed to create Notes session [id=" + e.id + ", text=" + e.text
                + ", mode=" + config.getMode()
                + ", server=" + config.getServer()
                + ", idFile=" + config.getIdFile()
                + ", user=" + config.getUsername() + "]", e);
        }
    }
}
