package com.hcl.notes.mcp.it;

import com.hcl.notes.mcp.adapter.DatabaseAdapter;
import com.hcl.notes.mcp.model.NotesDocument;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for DatabaseAdapter against sandbox test-app.nsf and test-hr.nsf.
 * SandboxFixtureBuilder must be run first to populate the databases.
 *
 * Fixture volumes (ADR-3):
 *   test-app.nsf — 200 documents in 5 categories (Alpha/Beta/Gamma/Delta/Epsilon)
 *   test-hr.nsf  — 600 employees, FT-indexed
 *
 * IMPORTANT: Never open production NSF paths in these tests (ADR-3).
 */
class DatabaseAdapterIT extends AbstractSandboxIT {

    private static DatabaseAdapter adapter;

    @BeforeAll
    static void setUpAdapter() {
        adapter = new DatabaseAdapter(pool);
    }

    // --- openDatabase ---

    @Test
    void openDatabase_opensSandboxAppDb() {
        var result = adapter.openDatabase(sandboxDb("test-app.nsf"));
        assertThat(result).isNotNull();
        assertThat(result.databasePath()).isEqualTo(sandboxDb("test-app.nsf"));
    }

    // --- listViews ---

    @Test
    void listViews_returnsViewsFromAppDb() {
        List<Map<String, Object>> views = adapter.listViews(sandboxDb("test-app.nsf"));
        assertThat(views).isNotEmpty();
        // Every entry must have a name
        views.forEach(v -> assertThat(v.get("name")).isNotNull());
    }

    // --- getViewEntries ---

    @Test
    void getViewEntries_returnsDocumentsFromAppDb() {
        List<NotesDocument> docs = adapter.getViewEntries(
                sandboxDb("test-app.nsf"), "All Documents", null, 10, 0);

        // SandboxFixtureBuilder created 200 documents
        assertThat(docs).isNotEmpty();
        assertThat(docs.size()).isLessThanOrEqualTo(10);
    }

    @Test
    void getViewEntries_respectsLimit() {
        List<NotesDocument> docs5  = adapter.getViewEntries(sandboxDb("test-app.nsf"), "All Documents", null, 5,  0);
        List<NotesDocument> docs20 = adapter.getViewEntries(sandboxDb("test-app.nsf"), "All Documents", null, 20, 0);

        assertThat(docs5.size()).isLessThanOrEqualTo(5);
        assertThat(docs20.size()).isGreaterThan(docs5.size());
    }

    @Test
    void getViewEntries_offsetSkipsDocuments() {
        List<NotesDocument> page1 = adapter.getViewEntries(sandboxDb("test-app.nsf"), "All Documents", null, 5, 0);
        List<NotesDocument> page2 = adapter.getViewEntries(sandboxDb("test-app.nsf"), "All Documents", null, 5, 5);

        // Pages should not overlap
        List<String> unids1 = page1.stream().map(NotesDocument::unid).toList();
        List<String> unids2 = page2.stream().map(NotesDocument::unid).toList();
        assertThat(unids1).doesNotContainAnyElementsOf(unids2);
    }

    @Test
    void getViewEntriesWithCount_returnsTotalMatchingFilter() {
        // Category "Alpha" — 200 docs / 5 categories ≈ 40 docs
        var result = adapter.getViewEntriesWithCount(
                sandboxDb("test-app.nsf"), "All Documents", "Alpha", 10, 0);

        assertThat(result.total()).isGreaterThan(0);
        assertThat(result.entries().size()).isLessThanOrEqualTo(10);
    }

    // --- getDocument ---

    @Test
    void getDocument_returnsDocumentByUnid() {
        List<NotesDocument> docs = adapter.getViewEntries(
                sandboxDb("test-app.nsf"), "All Documents", null, 1, 0);
        assertThat(docs).isNotEmpty();

        String unid = docs.get(0).unid();
        NotesDocument doc = adapter.getDocument(sandboxDb("test-app.nsf"), unid);

        assertThat(doc).isNotNull();
        assertThat(doc.unid()).isEqualTo(unid);
    }

    @Test
    void getDocument_returnsNullForUnknownUnid() {
        NotesDocument doc = adapter.getDocument(sandboxDb("test-app.nsf"),
                "AAAABBBBCCCCDDDDEEEE1111222233334444");
        assertThat(doc).isNull();
    }

    // --- searchDocumentsWithCount (FTSearch) ---

    @Test
    void searchDocumentsWithCount_findsDocumentsInHrDb() {
        // test-hr.nsf has 600 employees with names like "Firstname1", "Lastname42"
        var result = adapter.searchDocumentsWithCount(sandboxDb("test-hr.nsf"), "Firstname", 10, 0, 500);

        assertThat(result.total()).isGreaterThan(0);
        assertThat(result.entries()).isNotEmpty();
    }

    @Test
    void searchDocumentsWithCount_returnsEmptyForNoMatch() {
        var result = adapter.searchDocumentsWithCount(
                sandboxDb("test-hr.nsf"), "XYZZY_NO_SUCH_TERM_12345", 10, 0, 100);

        assertThat(result.total()).isEqualTo(0);
        assertThat(result.entries()).isEmpty();
    }

    // --- createDocument / updateDocument / deleteDocument ---

    @Test
    void createDocument_savesAndRetrievesDocument() {
        String unid = adapter.createDocument(sandboxDb("test-app.nsf"),
                Map.of("Form", "Document", "Title", "IT-Test-Create", "Category", "TestCategory"));

        assertThat(unid).isNotBlank();

        NotesDocument doc = adapter.getDocument(sandboxDb("test-app.nsf"), unid);
        assertThat(doc).isNotNull();
        assertThat(doc.fields().get("Title")).isEqualTo("IT-Test-Create");

        // Cleanup
        adapter.deleteDocument(sandboxDb("test-app.nsf"), unid);
    }

    @Test
    void updateDocument_changesFieldValue() {
        String unid = adapter.createDocument(sandboxDb("test-app.nsf"),
                Map.of("Form", "Document", "Title", "IT-Test-Update-Before", "Category", "TestCategory"));

        adapter.updateDocument(sandboxDb("test-app.nsf"), unid,
                Map.of("Title", "IT-Test-Update-After"));

        NotesDocument updated = adapter.getDocument(sandboxDb("test-app.nsf"), unid);
        assertThat(updated.fields().get("Title")).isEqualTo("IT-Test-Update-After");

        // Cleanup
        adapter.deleteDocument(sandboxDb("test-app.nsf"), unid);
    }

    @Test
    void deleteDocument_removesDocument() {
        String unid = adapter.createDocument(sandboxDb("test-app.nsf"),
                Map.of("Form", "Document", "Title", "IT-Test-Delete"));

        boolean deleted = adapter.deleteDocument(sandboxDb("test-app.nsf"), unid);

        assertThat(deleted).isTrue();
        assertThat(adapter.getDocument(sandboxDb("test-app.nsf"), unid)).isNull();
    }

    // --- parsePath edge case ---

    @Test
    void parsePath_throwsOnEmptyPathPart() {
        assertThatThrownBy(() -> adapter.openDatabase("server!!"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
