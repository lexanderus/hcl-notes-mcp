package com.hcl.notes.mcp.tools;

import com.hcl.notes.mcp.service.DatabaseService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class DatabaseTools {

    private final DatabaseService service;

    public DatabaseTools(DatabaseService service) {
        this.service = service;
    }

    @Tool(description = "Open and validate a Notes database. Returns databasePath for use in subsequent calls.")
    public Map<String, Object> notesOpenDatabase(
            @ToolParam(description = "Domino server hostname, or empty string for local") String server,
            @ToolParam(description = "Database path on server, e.g. mail/jdoe.nsf") String path) {
        var result = service.openDatabase(server, path);
        return Map.of("databasePath", result.databasePath(), "title", result.title());
    }

    @Tool(description = "List all views in a Notes database.")
    public Map<String, Object> notesListViews(
            @ToolParam(description = "databasePath in format server!!path") String databasePath) {
        return Map.of("views", service.listViews(databasePath));
    }

    @Tool(description = "Get paginated documents from a Notes view.")
    public Map<String, Object> notesGetViewEntries(
            @ToolParam(description = "databasePath in format server!!path") String databasePath,
            @ToolParam(description = "View name") String view,
            @ToolParam(description = "Optional key filter", required = false) String filter,
            @ToolParam(description = "Max results (default 50)", required = false) Integer limit,
            @ToolParam(description = "Offset for pagination (default 0)", required = false) Integer offset) {
        var result = service.getViewEntries(databasePath, view, filter,
                limit != null ? limit : 50, offset != null ? offset : 0);
        return Map.of("entries", result.entries(), "total", result.total());
    }

    @Tool(description = "Get a specific Notes document by UNID.")
    public Map<String, Object> notesGetDocument(
            @ToolParam(description = "databasePath in format server!!path") String databasePath,
            @ToolParam(description = "Document UNID") String unid) {
        var doc = service.getDocument(databasePath, unid);
        if (doc == null) return Map.of("error", "Document not found: " + unid);
        return Map.of("unid", doc.unid(), "created", String.valueOf(doc.created()),
                "modified", String.valueOf(doc.modified()), "fields", doc.fields());
    }

    @Tool(description = "Full-text search in a Notes database.")
    public Map<String, Object> notesSearchDocuments(
            @ToolParam(description = "databasePath in format server!!path") String databasePath,
            @ToolParam(description = "Search query") String query,
            @ToolParam(description = "Max results (default 50)", required = false) Integer limit,
            @ToolParam(description = "Offset (default 0)", required = false) Integer offset) {
        var result = service.searchDocuments(databasePath, query,
                limit != null ? limit : 50, offset != null ? offset : 0);
        return Map.of("entries", result.entries(), "total", result.total());
    }

    @Tool(description = "Create a new document in a Notes database.")
    public Map<String, Object> notesCreateDocument(
            @ToolParam(description = "databasePath in format server!!path") String databasePath,
            @ToolParam(description = "Fields as key-value object") Map<String, Object> fields) {
        return Map.of("unid", service.createDocument(databasePath, fields));
    }

    @Tool(description = "Update fields of an existing Notes document (partial update).")
    public Map<String, Object> notesUpdateDocument(
            @ToolParam(description = "databasePath in format server!!path") String databasePath,
            @ToolParam(description = "Document UNID") String unid,
            @ToolParam(description = "Fields to update as key-value object") Map<String, Object> fields) {
        return Map.of("unid", service.updateDocument(databasePath, unid, fields));
    }

    @Tool(description = "Delete a Notes document by UNID.")
    public Map<String, Object> notesDeleteDocument(
            @ToolParam(description = "databasePath in format server!!path") String databasePath,
            @ToolParam(description = "Document UNID to delete") String unid) {
        return Map.of("success", service.deleteDocument(databasePath, unid));
    }
}
