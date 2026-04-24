package com.hcl.notes.mcp;

import com.hcl.notes.mcp.connection.NotesSessionPool;
import com.hcl.notes.mcp.tools.CalendarTools;
import com.hcl.notes.mcp.tools.DatabaseTools;
import com.hcl.notes.mcp.tools.MailTools;
import com.hcl.notes.mcp.tools.TaskTools;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class McpContextTest {

    @MockBean
    NotesSessionPool notesSessionPool;

    @Autowired DatabaseTools databaseTools;
    @Autowired MailTools mailTools;
    @Autowired CalendarTools calendarTools;
    @Autowired TaskTools taskTools;

    @Test
    void contextLoads() {
        assertThat(databaseTools).isNotNull();
        assertThat(mailTools).isNotNull();
        assertThat(calendarTools).isNotNull();
        assertThat(taskTools).isNotNull();
    }
}
