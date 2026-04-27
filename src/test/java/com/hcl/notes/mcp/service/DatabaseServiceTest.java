package com.hcl.notes.mcp.service;

import com.hcl.notes.mcp.adapter.DatabaseAdapter;
import com.hcl.notes.mcp.model.NotesDocument;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DatabaseServiceTest {

    private final DatabaseAdapter adapter = mock(DatabaseAdapter.class);
    private final DatabaseService service = new DatabaseService(adapter, true);

    @Test
    void getViewEntries_returnsPagedResult() {
        var doc = new NotesDocument("U1", Instant.now(), Instant.now(), Map.of("Name", "Test"));
        var pagedResult = new DatabaseAdapter.PagedViewResult(List.of(doc), 1);
        when(adapter.getViewEntriesWithCount("srv!!db.nsf", "All", null, 50, 0)).thenReturn(pagedResult);

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

    @Test
    void searchDocuments_capsMaxCountDocsAt5000() {
        // limit=1000 → limit*10=10000, capped at 5000
        var searchResult = new DatabaseAdapter.PagedSearchResult(List.of(), 4200L);
        when(adapter.searchDocumentsWithCount("srv!!db.nsf", "query", 1000, 0, 5000))
                .thenReturn(searchResult);

        var result = service.searchDocuments("srv!!db.nsf", "query", 1000, 0);

        assertThat(result.total()).isEqualTo(4200L);
        verify(adapter).searchDocumentsWithCount("srv!!db.nsf", "query", 1000, 0, 5000);
    }

    @Test
    void searchDocuments_usesMinimumCapOf500ForSmallLimits() {
        // limit=10 → limit*10=100, floor at 500
        var searchResult = new DatabaseAdapter.PagedSearchResult(List.of(), 12L);
        when(adapter.searchDocumentsWithCount("srv!!db.nsf", "q", 10, 0, 500))
                .thenReturn(searchResult);

        service.searchDocuments("srv!!db.nsf", "q", 10, 0);

        verify(adapter).searchDocumentsWithCount("srv!!db.nsf", "q", 10, 0, 500);
    }

    @Test
    void searchDocuments_propagatesEmptyResult() {
        var searchResult = new DatabaseAdapter.PagedSearchResult(List.of(), 0L);
        when(adapter.searchDocumentsWithCount(anyString(), anyString(), anyInt(), anyInt(), anyInt()))
                .thenReturn(searchResult);

        var result = service.searchDocuments("srv!!db.nsf", "nothing", 50, 0);
        assertThat(result.entries()).isEmpty();
        assertThat(result.total()).isEqualTo(0L);
    }
}
