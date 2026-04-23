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
        return new NotesSessionPool(this::createSession,
                config.getPoolSize(), config.getTimeoutMs());
    }

    private Session createSession() {
        try {
            NotesThread.sinitThread();
            return switch (config.getMode()) {
                case REMOTE -> NotesFactory.createSession(
                        config.getServer(), config.getUsername(), config.getPassword());
                case LOCAL -> NotesFactory.createSession();
            };
        } catch (NotesException e) {
            NotesThread.stermThread();
            throw new NotesOperationException("Failed to create Notes session", e);
        }
    }
}
