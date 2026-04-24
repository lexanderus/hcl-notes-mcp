package com.hcl.notes.mcp.service;

import com.hcl.notes.mcp.adapter.DatabaseAdapter;
import com.hcl.notes.mcp.model.NotesDocument;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DatabaseServiceTest {

    private final DatabaseAdapter adapter = mock(DatabaseAdapter.class);
    private final DatabaseService service = new DatabaseService(adapter, true);

    @Test
    void getViewEntries_returnsPagedResult() {
        var doc = new NotesDocument("U1", Instant.now(), Instant.now(), Map.of("Name", "Test"));
        when(adapter.getViewEntries("srv!!db.nsf", "All", null, 50, 0)).thenReturn(List.of(doc));
        when(adapter.countViewEntries("srv!!db.nsf", "All", null)).thenReturn(1L);

        var result = service.getViewEntries("srv!!db.nsf", "All", null, 50, 0);
        assertThat(result.entries()).hasSize(1);
        assertThat(result.total()).isEqualTo(1);
    }

    @Test
    void deleteDocument_throwsWhenDeleteDisabled() {
        var svc = new DatabaseService(adapter, false);
        assertThatThrownBy(() -> svc.deleteDocument("srv!!db.nsf", "U1"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void openDatabase_buildsDatabasePath() {
        var openResult = new DatabaseAdapter.OpenResult("srv!!mail.nsf", "Mail DB");
        when(adapter.openDatabase("srv!!mail.nsf")).thenReturn(openResult);

        var result = service.openDatabase("srv", "mail.nsf");
        assertThat(result.databasePath()).isEqualTo("srv!!mail.nsf");
        assertThat(result.title()).isEqualTo("Mail DB");
    }
}
