package com.hcl.notes.mcp.config;

import com.hcl.notes.mcp.config.NotesConnectionConfig;
import com.hcl.notes.mcp.connection.NotesConnectionFactory;
import com.hcl.notes.mcp.connection.NotesSessionPool;
import com.hcl.notes.mcp.tools.CalendarTools;
import com.hcl.notes.mcp.tools.DatabaseTools;
import com.hcl.notes.mcp.tools.MailTools;
import com.hcl.notes.mcp.tools.TaskTools;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration
public class McpServerConfig {

    @Bean
    public NotesSessionPool notesSessionPool(NotesConnectionFactory factory, NotesConnectionConfig config) {
        return new NotesSessionPool(factory::createSession, config.getTimeoutMs());
    }

    @Bean
    public MethodToolCallbackProvider notesToolCallbacks(DatabaseTools dbTools, MailTools mailTools,
                                                          CalendarTools calendarTools, TaskTools taskTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(dbTools, mailTools, calendarTools, taskTools)
                .build();
    }
}
