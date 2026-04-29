package com.hcl.notes.mcp.adapter;

import com.hcl.notes.mcp.connection.NotesSessionPool;
import com.hcl.notes.mcp.model.MailMessage;
import lotus.domino.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Vector;

import static com.hcl.notes.mcp.adapter.NotesUtils.recycle;

@Component
public class MailAdapter {

    private static final Logger log = LoggerFactory.getLogger(MailAdapter.class);

    /** Notes error id=4000: FT index required but DB too large for temp index. */
    private static final int ERR_FT_TOO_LARGE = 4000;

    private final NotesSessionPool pool;
    private final MailDatabaseLocator mailDb;

    public MailAdapter(NotesSessionPool pool, MailDatabaseLocator mailDb) {
        this.pool   = pool;
        this.mailDb = mailDb;
    }

    public List<MailMessage> getInboxMessages(int count) {
        return pool.withSession(session -> {
            Database db = mailDb.openMailDatabase(session, "mail");
            try {
                View inbox = db.getView("($Inbox)");
                if (inbox == null) return Collections.emptyList();
                List<MailMessage> messages = new ArrayList<>();
                try {
                    Document doc = inbox.getLastDocument();
                    int collected = 0;
                    while (doc != null && collected < count) {
                        messages.add(toMailMessage(doc));
                        Document prev = inbox.getPrevDocument(doc);
                        recycle(doc);
                        doc = prev;
                        collected++;
                    }
                    if (doc != null) recycle(doc);
                } finally {
                    recycle(inbox);
                }
                return messages;
            } finally {
                recycle(db);
            }
        });
    }

    public boolean sendMail(List<String> to, String subject, String body, List<String> cc) {
        return pool.withSession(session -> {
            Database db = mailDb.openMailDatabase(session, "mail");
            try {
                Document doc = db.createDocument();
                try {
                    doc.replaceItemValue("Form", "Memo");
                    doc.replaceItemValue("SendTo", new Vector<>(to));
                    doc.replaceItemValue("Subject", subject);
                    doc.replaceItemValue("Body", body);
                    if (cc != null && !cc.isEmpty()) {
                        doc.replaceItemValue("CopyTo", new Vector<>(cc));
                    }
                    doc.send(false);
                    return true;
                } finally {
                    recycle(doc);
                }
            } finally {
                recycle(db);
            }
        });
    }

    public List<MailMessage> searchMail(String query, String folder, int limit) {
        return pool.withSession(session -> {
            Database db = mailDb.openMailDatabase(session, "mail");
            try {
                String viewName = folder != null ? folder : "($Inbox)";
                View view = db.getView(viewName);
                List<MailMessage> messages = new ArrayList<>();
                if (view != null) {
                    try {
                        messages = searchInView(view, db, query, limit);
                    } finally {
                        recycle(view);
                    }
                } else {
                    // No specific view — search full DB
                    messages = searchInDb(db, query, limit);
                }
                return messages;
            } finally {
                recycle(db);
            }
        });
    }

    /**
     * Search within a view: try FTSearch first; fall back to native formula search
     * via db.search() if the database has no pre-built FT index (error id=4000).
     *
     * Formula search (db.search) is executed by Notes' native C engine — far faster
     * than iterating documents one-by-one via JNI (~50 ms overhead per JNI call).
     * We pass db so the formula fallback can scan the full database rather than
     * doing an expensive per-document JNI iteration inside the view.
     */
    private List<MailMessage> searchInView(View view, Database db, String query, int limit)
            throws NotesException {
        try {
            view.FTSearch(query, limit);
            return collectFromView(view, limit);
        } catch (NotesException e) {
            if (e.id == ERR_FT_TOO_LARGE) {
                log.debug("FTSearch unavailable on view (id={}), falling back to formula search for '{}'",
                        e.id, query);
                return searchByFormula(db, query, limit);
            }
            throw e;
        }
    }

    /**
     * Search full DB via FTSearch; fall back to formula search if no FT index.
     */
    private List<MailMessage> searchInDb(Database db, String query, int limit)
            throws NotesException {
        try {
            DocumentCollection col = db.FTSearch(query, limit);
            try {
                return collectFromCollection(col, limit);
            } finally {
                recycle(col);
            }
        } catch (NotesException e) {
            if (e.id == ERR_FT_TOO_LARGE) {
                log.debug("FTSearch unavailable on DB (id={}), falling back to formula search for '{}'",
                        e.id, query);
                return searchByFormula(db, query, limit);
            }
            throw e;
        }
    }

    /**
     * Native formula search on the full database — executes in Notes' C engine,
     * does not require a pre-built FT index, and is much faster than per-document
     * JNI iteration.  Searches Subject field case-insensitively.
     */
    private List<MailMessage> searchByFormula(Database db, String query, int limit)
            throws NotesException {
        String escaped = query.replace("\"", "\\\"");
        String formula = "@Contains(@LowerCase(Subject); @LowerCase(\"" + escaped + "\"))";
        log.debug("Formula search: query='{}', limit={}", query, limit);
        DocumentCollection col = db.search(formula, null, limit);
        try {
            return collectFromCollection(col, limit);
        } finally {
            recycle(col);
        }
    }

    private List<MailMessage> collectFromView(View view, int limit) throws NotesException {
        List<MailMessage> results = new ArrayList<>();
        Document doc = view.getFirstDocument();
        while (doc != null && results.size() < limit) {
            results.add(toMailMessage(doc));
            Document next = view.getNextDocument(doc);
            recycle(doc);
            doc = next;
        }
        if (doc != null) recycle(doc);
        return results;
    }

    private List<MailMessage> collectFromCollection(DocumentCollection col, int limit)
            throws NotesException {
        List<MailMessage> results = new ArrayList<>();
        Document doc = col.getFirstDocument();
        while (doc != null && results.size() < limit) {
            results.add(toMailMessage(doc));
            Document next = col.getNextDocument(doc);
            recycle(doc);
            doc = next;
        }
        if (doc != null) recycle(doc);
        return results;
    }

    public boolean moveToFolder(String unid, String folder) {
        return pool.withSession(session -> {
            Database db = mailDb.openMailDatabase(session, "mail");
            try {
                Document doc = db.getDocumentByUNID(unid);
                if (doc == null) return false;
                try {
                    // putInFolder(name, createOnFail): false = use existing folder/view, do NOT create.
                    // System views like ($Inbox) must use createOnFail=false — passing true throws
                    // error 4005 "A folder or view with this name already exists".
                    boolean createIfMissing = !folder.startsWith("($");
                    doc.putInFolder(folder, createIfMissing);
                    if (!"($Inbox)".equals(folder)) {
                        doc.removeFromFolder("($Inbox)");
                    }
                    return true;
                } finally {
                    recycle(doc);
                }
            } finally {
                recycle(db);
            }
        });
    }

    public boolean removeFromFolder(String unid, String folder) {
        return pool.withSession(session -> {
            Database db = mailDb.openMailDatabase(session, "mail");
            try {
                Document doc = db.getDocumentByUNID(unid);
                if (doc == null) return false;
                try {
                    doc.removeFromFolder(folder);
                    return true;
                } finally {
                    recycle(doc);
                }
            } finally {
                recycle(db);
            }
        });
    }

    private MailMessage toMailMessage(Document doc) throws NotesException {
        DateTime date = doc.getCreated();
        try {
            return new MailMessage(
                    doc.getUniversalID(),
                    doc.getItemValueString("From"),
                    getSendTo(doc),
                    doc.getItemValueString("Subject"),
                    doc.getItemValueString("Body"),
                    date != null ? Instant.ofEpochMilli(date.toJavaDate().getTime()) : null
            );
        } finally {
            recycle(date);
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> getSendTo(Document doc) throws NotesException {
        Vector<String> v = doc.getItemValue("SendTo");
        return v != null ? new ArrayList<>(v) : List.of();
    }
}
