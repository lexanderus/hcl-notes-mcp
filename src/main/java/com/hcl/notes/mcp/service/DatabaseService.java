package com.hcl.notes.mcp.service;

import com.hcl.notes.mcp.adapter.DatabaseAdapter;
import com.hcl.notes.mcp.model.NotesDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

@Service
public class DatabaseService {

    private final DatabaseAdapter adapter;
    private final boolean deleteEnabled;

    public DatabaseService(DatabaseAdapter adapter,
                           @Value("${notes.tools.delete-enabled:true}") boolean deleteEnabled) {
        this.adapter = adapter;
        this.deleteEnabled = deleteEnabled;
    }

    public record PagedResult(List<NotesDocument> entries, long total) {}

    public DatabaseAdapter.OpenResult openDatabase(String server, String path) {
        return adapter.openDatabase(server + "!!" + path);
    }

    public List<Map<String, Object>> listViews(String databasePath) {
        return adapter.listViews(databasePath);
    }

    public PagedResult getViewEntries(String databasePath, String view,
                                      String filter, int limit, int offset) {
        var result = adapter.getViewEntriesWithCount(databasePath, view, filter, limit, offset);
        return new PagedResult(result.entries(), result.total());
    }

    public NotesDocument getDocument(String databasePath, String unid) {
        return adapter.getDocument(databasePath, unid);
    }

    public PagedResult searchDocuments(String databasePath, String query, int limit, int offset) {
        int maxCountDocs = Math.min(Math.max(limit * 10, 500), 5000);
        var result = adapter.searchDocumentsWithCount(databasePath, query, limit, offset, maxCountDocs);
        return new PagedResult(result.entries(), result.total());
    }

    public PagedResult formulaSearchDocuments(String databasePath, String formula,
                                               int limit, int offset) {
        var result = adapter.formulaSearchDocuments(databasePath, formula, limit, offset);
        return new PagedResult(result.entries(), result.total());
    }

    public String createDocument(String databasePath, Map<String, Object> fields) {
        return adapter.createDocument(databasePath, fields);
    }

    public String updateDocument(String databasePath, String unid, Map<String, Object> fields) {
        return adapter.updateDocument(databasePath, unid, fields);
    }

    public boolean deleteDocument(String databasePath, String unid) {
        if (!deleteEnabled) {
            throw new UnsupportedOperationException(
                    "Delete is disabled via notes.tools.delete-enabled=false");
        }
        return adapter.deleteDocument(databasePath, unid);
    }

    public DatabaseAdapter.AttachmentResult getAttachment(String databasePath, String unid,
                                                           String fileName, int maxSizeKb) {
        long maxBytes = (long) maxSizeKb * 1024;
        return adapter.getAttachment(databasePath, unid, fileName, maxBytes);
    }
}
