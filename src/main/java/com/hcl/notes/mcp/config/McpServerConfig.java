package com.hcl.notes.mcp.config;

import com.hcl.notes.mcp.connection.NotesConnectionFactory;
import com.hcl.notes.mcp.connection.NotesSessionPool;
import com.hcl.notes.mcp.tools.DatabaseTools;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration
public class McpServerConfig {

    @Bean
    @Lazy
    public NotesSessionPool notesSessionPool(NotesConnectionFactory factory) {
        return factory.createPool();
    }

    @Bean
    public MethodToolCallbackProvider notesToolCallbacks(DatabaseTools dbTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(dbTools)
                .build();
    }
}
