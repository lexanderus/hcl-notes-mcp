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
            return switch (config.getMode()) {
                case REMOTE -> NotesFactory.createSession(
                        config.getServer(), config.getUsername(), config.getPassword());
                case LOCAL -> {
                    NotesThread.sinitThread();
                    yield NotesFactory.createSession();
                }
            };
        } catch (NotesException e) {
            if (config.getMode() == ConnectionMode.LOCAL) {
                NotesThread.stermThread();
            }
            throw new NotesOperationException("Failed to create Notes session", e);
        }
    }
}
