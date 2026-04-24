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
        List<NotesDocument> entries = adapter.getViewEntries(databasePath, view, filter, limit, offset);
        long total = adapter.countViewEntries(databasePath, view, filter);
        return new PagedResult(entries, total);
    }

    public NotesDocument getDocument(String databasePath, String unid) {
        return adapter.getDocument(databasePath, unid);
    }

    public PagedResult searchDocuments(String databasePath, String query, int limit, int offset) {
        List<NotesDocument> entries = adapter.searchDocuments(databasePath, query, limit, offset);
        long total = adapter.countSearchResults(databasePath, query);
        return new PagedResult(entries, total);
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
}
