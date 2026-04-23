package com.hcl.notes.mcp.adapter;

import com.hcl.notes.mcp.connection.NotesOperationException;
import com.hcl.notes.mcp.connection.NotesSessionPool;
import com.hcl.notes.mcp.model.NotesDocument;
import lotus.domino.*;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.*;

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
            Database db = session.getDatabase(parts[0], parts[1]);
            if (db == null || !db.isOpen()) {
                throw new NotesOperationException("Database not found: " + databasePath, null);
            }
            return new OpenResult(databasePath, db.getTitle());
        });
    }

    public List<Map<String, Object>> listViews(String databasePath) {
        String[] parts = parsePath(databasePath);
        return pool.withSession(session -> {
            Database db = openDb(session, parts);
            Vector<?> views = db.getViews();
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object v : views) {
                View view = (View) v;
                result.add(Map.of("name", view.getName(), "entryCount", view.getEntryCount()));
            }
            return result;
        });
    }

    public List<NotesDocument> getViewEntries(String databasePath, String viewName,
                                               String filter, int limit, int offset) {
        String[] parts = parsePath(databasePath);
        return pool.withSession(session -> {
            Database db = openDb(session, parts);
            View view = db.getView(viewName);
            if (view == null) throw new NotesOperationException("View not found: " + viewName, null);
            ViewEntryCollection col = filter != null
                    ? view.getAllEntriesByKey(filter, true)
                    : view.getAllEntries();
            List<NotesDocument> docs = new ArrayList<>();
            ViewEntry entry = col.getNthEntry(offset + 1);
            int count = 0;
            while (entry != null && count < limit) {
                Document doc = entry.getDocument();
                docs.add(toModel(doc));
                entry = col.getNextEntry(entry);
                count++;
            }
            return docs;
        });
    }

    public long countViewEntries(String databasePath, String viewName, String filter) {
        String[] parts = parsePath(databasePath);
        return pool.withSession(session -> {
            Database db = openDb(session, parts);
            View view = db.getView(viewName);
            if (view == null) throw new NotesOperationException("View not found: " + viewName, null);
            return filter != null
                    ? (long) view.getAllEntriesByKey(filter, true).getCount()
                    : (long) view.getAllEntries().getCount();
        });
    }

    public NotesDocument getDocument(String databasePath, String unid) {
        String[] parts = parsePath(databasePath);
        return pool.withSession(session -> {
            Database db = openDb(session, parts);
            Document doc = db.getDocumentByUNID(unid);
            if (doc == null) return null;
            return toModel(doc);
        });
    }

    public List<NotesDocument> searchDocuments(String databasePath, String query,
                                                int limit, int offset) {
        String[] parts = parsePath(databasePath);
        return pool.withSession(session -> {
            Database db = openDb(session, parts);
            DocumentCollection col = db.search(query, null, limit + offset);
            List<NotesDocument> docs = new ArrayList<>();
            Document doc = col.getNthDocument(offset + 1);
            int count = 0;
            while (doc != null && count < limit) {
                docs.add(toModel(doc));
                doc = col.getNextDocument(doc);
                count++;
            }
            return docs;
        });
    }

    public int countSearchResults(String databasePath, String query) {
        String[] parts = parsePath(databasePath);
        return pool.withSession(session -> {
            Database db = openDb(session, parts);
            return db.search(query, null, 0).getCount();
        });
    }

    public String createDocument(String databasePath, Map<String, Object> fields) {
        String[] parts = parsePath(databasePath);
        return pool.withSession(session -> {
            Database db = openDb(session, parts);
            Document doc = db.createDocument();
            setFields(doc, fields);
            doc.save();
            return doc.getUniversalID();
        });
    }

    public String updateDocument(String databasePath, String unid, Map<String, Object> fields) {
        String[] parts = parsePath(databasePath);
        return pool.withSession(session -> {
            Database db = openDb(session, parts);
            Document doc = db.getDocumentByUNID(unid);
            if (doc == null) throw new NotesOperationException("Document not found: " + unid, null);
            setFields(doc, fields);
            doc.save();
            return doc.getUniversalID();
        });
    }

    public boolean deleteDocument(String databasePath, String unid) {
        String[] parts = parsePath(databasePath);
        return pool.withSession(session -> {
            Database db = openDb(session, parts);
            Document doc = db.getDocumentByUNID(unid);
            if (doc == null) return false;
            doc.remove(true);
            return true;
        });
    }

    public static String[] parsePath(String databasePath) {
        if (!databasePath.contains("!!")) {
            throw new IllegalArgumentException(
                    "Invalid databasePath format. Expected 'server!!path', got: " + databasePath);
        }
        return databasePath.split("!!", 2);
    }

    private Database openDb(Session session, String[] parts) throws NotesException {
        Database db = session.getDatabase(parts[0], parts[1]);
        if (db == null || !db.isOpen()) {
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
            fields.put(item.getName(), item.getValues().size() == 1
                    ? item.getValues().get(0) : item.getValues());
        }
        DateTime created = doc.getCreated();
        DateTime modified = doc.getLastModified();
        java.util.Date createdDate = (created != null) ? created.toJavaDate() : null;
        java.util.Date modifiedDate = (modified != null) ? modified.toJavaDate() : null;
        return new NotesDocument(
                doc.getUniversalID(),
                createdDate != null ? Instant.ofEpochMilli(createdDate.getTime()) : null,
                modifiedDate != null ? Instant.ofEpochMilli(modifiedDate.getTime()) : null,
                fields
        );
    }
}
