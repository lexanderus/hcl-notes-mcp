package com.hcl.notes.mcp.adapter;

import com.hcl.notes.mcp.connection.NotesOperationException;
import com.hcl.notes.mcp.connection.NotesSessionPool;
import com.hcl.notes.mcp.model.NotesDocument;
import lotus.domino.*;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.Base64;
import java.util.Set;

import static com.hcl.notes.mcp.adapter.NotesUtils.recycle;

@Component
public class DatabaseAdapter {

    private final NotesSessionPool pool;

    public DatabaseAdapter(NotesSessionPool pool) {
        this.pool = pool;
    }

    public record OpenResult(String databasePath, String title) {}

    public OpenResult openDatabase(String databasePath) {
        String[] parts = parsePath(databasePath);
        return pool.withSession(session -> {
            Database db = session.getDatabase(parts[0], parts[1], false);
            if (db == null || !db.isOpen()) {
                if (db != null) recycle(db);
                throw new NotesOperationException("Database not found: " + databasePath, null);
            }
            try {
                return new OpenResult(databasePath, db.getTitle());
            } finally {
                recycle(db);
            }
        });
    }

    public List<Map<String, Object>> listViews(String databasePath) {
        String[] parts = parsePath(databasePath);
        return pool.withSession(session -> {
            Database db = openDb(session, parts);
            try {
                Vector<?> views = db.getViews();
                List<Map<String, Object>> result = new ArrayList<>();
                for (Object v : views) {
                    View view = (View) v;
                    try {
                        result.add(Map.of("name", view.getName()));
                    } catch (Exception e) {
                        // skip problematic views
                    } finally {
                        recycle(view);
                    }
                }
                return result;
            } finally {
                recycle(db);
            }
        });
    }

    public List<NotesDocument> getViewEntries(String databasePath, String viewName,
                                               String filter, int limit, int offset) {
        String[] parts = parsePath(databasePath);
        return pool.withSession(session -> {
            Database db = openDb(session, parts);
            try {
                View view = db.getView(viewName);
                if (view == null) throw new NotesOperationException("View not found: " + viewName, null);
                List<NotesDocument> docs = new ArrayList<>();
                try {
                    if (filter != null) {
                        ViewEntryCollection col = view.getAllEntriesByKey(filter, false);
                        try {
                            ViewEntry ve = col.getFirstEntry();
                            int skipped = 0;
                            while (ve != null && skipped < offset) {
                                ViewEntry current = ve;
                                ve = col.getNextEntry(ve);
                                if (current.isDocument()) skipped++;
                                recycle(current);
                            }
                            int count = 0;
                            while (ve != null && count < limit) {
                                ViewEntry current = ve;
                                ve = col.getNextEntry(ve);
                                if (current.isDocument()) {
                                    Document doc = current.getDocument();
                                    if (doc != null) {
                                        try { docs.add(toModel(doc)); count++; }
                                        finally { recycle(doc); }
                                    }
                                }
                                recycle(current);
                            }
                            if (ve != null) recycle(ve);
                        } finally {
                            recycle(col);
                        }
                    } else {
                        Document doc = view.getFirstDocument();
                        int skipped = 0;
                        while (doc != null && skipped < offset) {
                            Document next = view.getNextDocument(doc);
                            recycle(doc);
                            doc = next;
                            skipped++;
                        }
                        int count = 0;
                        while (doc != null && count < limit) {
                            docs.add(toModel(doc));
                            Document next = view.getNextDocument(doc);
                            recycle(doc);
                            doc = next;
                            count++;
                        }
                        if (doc != null) recycle(doc);
                    }
                } finally {
                    recycle(view);
                }
                return docs;
            } finally {
                recycle(db);
            }
        });
    }

    public long countViewEntries(String databasePath, String viewName, String filter) {
        String[] parts = parsePath(databasePath);
        return pool.withSession(session -> {
            Database db = openDb(session, parts);
            try {
                View view = db.getView(viewName);
                if (view == null) throw new NotesOperationException("View not found: " + viewName, null);
                try {
                    if (filter != null) {
                        ViewEntryCollection col = view.getAllEntriesByKey(filter, false);
                        try {
                            return (long) col.getCount();
                        } finally {
                            recycle(col);
                        }
                    }
                    return (long) view.getEntryCount();
                } finally {
                    recycle(view);
                }
            } finally {
                recycle(db);
            }
        });
    }

    public NotesDocument getDocument(String databasePath, String unid) {
        String[] parts = parsePath(databasePath);
        return pool.withSession(session -> {
            Database db = openDb(session, parts);
            try {
                Document doc;
                try {
                    doc = db.getDocumentByUNID(unid);
                } catch (NotesException e) {
                    // id=4091: "Invalid universal id" — UNID is malformed or document was deleted
                    if (e.id == 4091) return null;
                    throw e;
                }
                if (doc == null) return null;
                try {
                    return toModel(doc);
                } finally {
                    recycle(doc);
                }
            } finally {
                recycle(db);
            }
        });
    }

    /** Combined get + count in one session — avoids 2x session roundtrip. */
    public record PagedViewResult(List<NotesDocument> entries, long total) {}

    public PagedViewResult getViewEntriesWithCount(String databasePath, String viewName,
                                                    String filter, int limit, int offset) {
        String[] parts = parsePath(databasePath);
        return pool.withSession(session -> {
            Database db = openDb(session, parts);
            try {
                View view = db.getView(viewName);
                if (view == null) throw new NotesOperationException("View not found: " + viewName, null);
                List<NotesDocument> docs = new ArrayList<>();
                long total = 0;
                try {
                    if (filter != null) {
                        ViewEntryCollection col = view.getAllEntriesByKey(filter, false);
                        try {
                            total = (long) col.getCount();
                            ViewEntry ve = col.getFirstEntry();
                            int skipped = 0;
                            while (ve != null && skipped < offset) {
                                ViewEntry current = ve;
                                ve = col.getNextEntry(ve);
                                if (current.isDocument()) skipped++;
                                recycle(current);
                            }
                            int count = 0;
                            while (ve != null && count < limit) {
                                ViewEntry current = ve;
                                ve = col.getNextEntry(ve);
                                if (current.isDocument()) {
                                    Document doc = current.getDocument();
                                    if (doc != null) {
                                        try { docs.add(toModel(doc)); count++; }
                                        finally { recycle(doc); }
                                    }
                                }
                                recycle(current);
                            }
                            if (ve != null) recycle(ve);
                        } finally {
                            recycle(col);
                        }
                    } else {
                        total = (long) view.getEntryCount();
                        Document doc = view.getFirstDocument();
                        int skipped = 0;
                        while (doc != null && skipped < offset) {
                            Document next = view.getNextDocument(doc);
                            recycle(doc);
                            doc = next;
                            skipped++;
                        }
                        int count = 0;
                        while (doc != null && count < limit) {
                            docs.add(toModel(doc));
                            Document next = view.getNextDocument(doc);
                            recycle(doc);
                            doc = next;
                            count++;
                        }
                        if (doc != null) recycle(doc);
                    }
                } finally {
                    recycle(view);
                }
                return new PagedViewResult(docs, total);
            } finally {
                recycle(db);
            }
        });
    }

    /** Combined search + count in one session — avoids 2x session roundtrip. */
    public record PagedSearchResult(List<NotesDocument> entries, long total) {}

    public PagedSearchResult searchDocumentsWithCount(String databasePath, String query,
                                                       int limit, int offset, int maxCountDocs) {
        String[] parts = parsePath(databasePath);
        return pool.withSession(session -> {
            Database db = openDb(session, parts);
            try {
                // Single scan: fetch up to maxCountDocs, count + page in one pass
                DocumentCollection col = db.FTSearch(query, maxCountDocs);
                try {
                    long total = (long) col.getCount();
                    if (total == 0) return new PagedSearchResult(List.of(), 0);

                    List<NotesDocument> docs = new ArrayList<>();
                    Document doc = col.getNthDocument(offset + 1);
                    int count = 0;
                    while (doc != null && count < limit) {
                        docs.add(toModel(doc));
                        Document next = col.getNextDocument(doc);
                        recycle(doc);
                        doc = next;
                        count++;
                    }
                    if (doc != null) recycle(doc);
                    return new PagedSearchResult(docs, total);
                } finally {
                    recycle(col);
                }
            } finally {
                recycle(db);
            }
        });
    }

    public List<NotesDocument> searchDocuments(String databasePath, String query,
                                                int limit, int offset) {
        String[] parts = parsePath(databasePath);
        return pool.withSession(session -> {
            Database db = openDb(session, parts);
            try {
                DocumentCollection col = db.FTSearch(query, limit + offset);
                List<NotesDocument> docs = new ArrayList<>();
                try {
                    Document doc = col.getNthDocument(offset + 1);
                    int count = 0;
                    while (doc != null && count < limit) {
                        docs.add(toModel(doc));
                        Document next = col.getNextDocument(doc);
                        recycle(doc);
                        doc = next;
                        count++;
                    }
                    if (doc != null) recycle(doc);
                } finally {
                    recycle(col);
                }
                return docs;
            } finally {
                recycle(db);
            }
        });
    }

    public long countSearchResults(String databasePath, String query, int maxDocs) {
        String[] parts = parsePath(databasePath);
        return pool.withSession(session -> {
            Database db = openDb(session, parts);
            try {
                DocumentCollection col = db.FTSearch(query, maxDocs);
                try {
                    return (long) col.getCount();
                } finally {
                    recycle(col);
                }
            } finally {
                recycle(db);
            }
        });
    }

    /** Notes formula language search — works on databases without a permanent FT index. */
    public PagedSearchResult formulaSearchDocuments(String databasePath, String formula,
                                                     int limit, int offset) {
        String[] parts = parsePath(databasePath);
        return pool.withSession(session -> {
            Database db = openDb(session, parts);
            try {
                DocumentCollection col = db.search(formula, null, 0);
                try {
                    long total = (long) col.getCount();
                    if (total == 0) return new PagedSearchResult(List.of(), 0);
                    List<NotesDocument> docs = new ArrayList<>();
                    Document doc = col.getNthDocument(offset + 1);
                    int count = 0;
                    while (doc != null && count < limit) {
                        docs.add(toModel(doc));
                        Document next = col.getNextDocument(doc);
                        recycle(doc);
                        doc = next;
                        count++;
                    }
                    if (doc != null) recycle(doc);
                    return new PagedSearchResult(docs, total);
                } finally {
                    recycle(col);
                }
            } finally {
                recycle(db);
            }
        });
    }

    public String createDocument(String databasePath, Map<String, Object> fields) {
        String[] parts = parsePath(databasePath);
        return pool.withSession(session -> {
            Database db = openDb(session, parts);
            try {
                Document doc = db.createDocument();
                try {
                    setFields(doc, fields);
                    doc.save();
                    return doc.getUniversalID();
                } finally {
                    recycle(doc);
                }
            } finally {
                recycle(db);
            }
        });
    }

    public String updateDocument(String databasePath, String unid, Map<String, Object> fields) {
        String[] parts = parsePath(databasePath);
        return pool.withSession(session -> {
            Database db = openDb(session, parts);
            try {
                Document doc = db.getDocumentByUNID(unid);
                if (doc == null) throw new NotesOperationException("Document not found: " + unid, null);
                try {
                    setFields(doc, fields);
                    doc.save();
                    return doc.getUniversalID();
                } finally {
                    recycle(doc);
                }
            } finally {
                recycle(db);
            }
        });
    }

    /** Text file extensions returned as UTF-8 plain text; everything else as Base64. */
    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            "txt", "html", "htm", "xml", "csv", "json", "log", "md", "properties", "yaml", "yml");

    public record AttachmentResult(String fileName, long size, String encoding, String content) {}

    /**
     * Extracts a named attachment from a Notes document and returns its content.
     *
     * @param databasePath  server!!path
     * @param unid          document UNID
     * @param fileName      attachment file name (as returned in $FILE field)
     * @param maxSizeBytes  maximum allowed size; throws if attachment is larger
     */
    public AttachmentResult getAttachment(String databasePath, String unid,
                                          String fileName, long maxSizeBytes) {
        String[] parts = parsePath(databasePath);
        return pool.withSession(session -> {
            Database db = openDb(session, parts);
            try {
                Document doc;
                try {
                    doc = db.getDocumentByUNID(unid);
                } catch (NotesException e) {
                    if (e.id == 4091) throw new NotesOperationException(
                            "Document not found: " + unid, e);
                    throw e;
                }
                if (doc == null) throw new NotesOperationException(
                        "Document not found: " + unid, null);
                try {
                    EmbeddedObject eo = doc.getAttachment(fileName);
                    if (eo == null) throw new NotesOperationException(
                            "Attachment not found: " + fileName, null);
                    try {
                        long size = eo.getFileSize();
                        if (size > maxSizeBytes) {
                            throw new NotesOperationException(
                                    "Attachment too large: " + size + " bytes (limit " + maxSizeBytes
                                    + "). Use maxSizeKb parameter to increase the limit.", null);
                        }
                        // Extract to temp file on the JNI thread
                        Path tempDir = Path.of(System.getProperty("java.io.tmpdir"));
                        // Use UNID prefix to avoid collisions
                        Path tempFile = tempDir.resolve(unid.substring(0, 8) + "_" + fileName);
                        try {
                            eo.extractFile(tempFile.toString());
                            byte[] bytes = Files.readAllBytes(tempFile);
                            String ext = getExtension(fileName).toLowerCase();
                            if (TEXT_EXTENSIONS.contains(ext)) {
                                return new AttachmentResult(fileName, size, "utf-8",
                                        new String(bytes, StandardCharsets.UTF_8));
                            } else {
                                return new AttachmentResult(fileName, size, "base64",
                                        Base64.getEncoder().encodeToString(bytes));
                            }
                        } catch (IOException e) {
                            throw new NotesOperationException(
                                    "Failed to read extracted attachment: " + e.getMessage(), e);
                        } finally {
                            try { Files.deleteIfExists(tempFile); } catch (IOException ignored) {}
                        }
                    } finally {
                        recycle(eo);
                    }
                } finally {
                    recycle(doc);
                }
            } finally {
                recycle(db);
            }
        });
    }

    private static String getExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return (dot >= 0 && dot < fileName.length() - 1) ? fileName.substring(dot + 1) : "";
    }

    public boolean deleteDocument(String databasePath, String unid) {
        String[] parts = parsePath(databasePath);
        return pool.withSession(session -> {
            Database db = openDb(session, parts);
            try {
                Document doc;
                try {
                    doc = db.getDocumentByUNID(unid);
                } catch (NotesException e) {
                    // id=4091: "Invalid universal id" — UNID is malformed or document was deleted
                    if (e.id == 4091) return false;
                    throw e;
                }
                if (doc == null) return false;
                try {
                    doc.remove(true);
                    return true;
                } finally {
                    recycle(doc);
                }
            } finally {
                recycle(db);
            }
        });
    }

    public static String[] parsePath(String databasePath) {
        if (databasePath == null || !databasePath.contains("!!")) {
            throw new IllegalArgumentException(
                    "Invalid databasePath — expected 'server!!path', got: " + databasePath);
        }
        String[] parts = databasePath.split("!!", 2);
        if (parts[1] == null || parts[1].isBlank()) {
            throw new IllegalArgumentException(
                    "Invalid databasePath — path part is empty in: " + databasePath);
        }
        return parts;
    }

    private Database openDb(Session session, String[] parts) throws NotesException {
        Database db = session.getDatabase(parts[0], parts[1], false);
        if (db == null) {
            throw new NotesOperationException(
                    "Database not found: " + parts[0] + "!!" + parts[1], null);
        }
        if (!db.isOpen()) {
            db.open();
        }
        if (!db.isOpen()) {
            recycle(db);
            throw new NotesOperationException(
                    "Database not accessible: " + parts[0] + "!!" + parts[1], null);
        }
        return db;
    }

    private void setFields(Document doc, Map<String, Object> fields) throws NotesException {
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            doc.replaceItemValue(entry.getKey(), entry.getValue());
        }
    }

    @SuppressWarnings("unchecked")
    private NotesDocument toModel(Document doc) throws NotesException {
        Map<String, Object> fields = new LinkedHashMap<>();
        Vector<Item> items = doc.getItems();
        for (Item item : items) {
            try {
                String itemName = item.getName();
                Vector<?> values = item.getValues();
                if (values == null) continue;
                List<Object> converted = new ArrayList<>(values.size());
                for (Object v : values) {
                    if (v instanceof DateTime dt) {
                        try { converted.add(dt.toJavaDate().toInstant().toString()); }
                        catch (Exception ignored) { converted.add(v.toString()); }
                        // DateTime from getValues() is a copy — recycle it
                        recycle(dt);
                    } else {
                        converted.add(v);
                    }
                }
                fields.put(itemName, converted.size() == 1 ? converted.get(0) : converted);
            } finally {
                recycle(item);
            }
        }

        DateTime created = doc.getCreated();
        DateTime modified = doc.getLastModified();
        try {
            java.util.Date createdDate  = (created  != null) ? created.toJavaDate()  : null;
            java.util.Date modifiedDate = (modified != null) ? modified.toJavaDate() : null;
            return new NotesDocument(
                    doc.getUniversalID(),
                    createdDate  != null ? Instant.ofEpochMilli(createdDate.getTime())  : null,
                    modifiedDate != null ? Instant.ofEpochMilli(modifiedDate.getTime()) : null,
                    fields
            );
        } finally {
            recycle(created);
            recycle(modified);
        }
    }

}
