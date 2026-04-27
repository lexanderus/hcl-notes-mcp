package com.hcl.notes.mcp.adapter;

import com.hcl.notes.mcp.connection.NotesOperationException;
import com.hcl.notes.mcp.connection.NotesSessionPool;
import com.hcl.notes.mcp.model.MailMessage;
import lotus.domino.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.*;

import static com.hcl.notes.mcp.adapter.DatabaseAdapter.recycle;

@Component
public class MailAdapter {

    private static final Logger log = LoggerFactory.getLogger(MailAdapter.class);

    private final NotesSessionPool pool;

    public MailAdapter(NotesSessionPool pool) {
        this.pool = pool;
    }

    public List<MailMessage> getInboxMessages(int count) {
        return pool.withSession(session -> {
            Database mailDb = getMailDatabase(session);
            try {
                View inbox = mailDb.getView("($Inbox)");
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
                recycle(mailDb);
            }
        });
    }

    public boolean sendMail(List<String> to, String subject, String body, List<String> cc) {
        return pool.withSession(session -> {
            Database mailDb = getMailDatabase(session);
            try {
                Document doc = mailDb.createDocument();
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
                recycle(mailDb);
            }
        });
    }

    public List<MailMessage> searchMail(String query, String folder, int limit) {
        return pool.withSession(session -> {
            Database mailDb = getMailDatabase(session);
            try {
                String viewName = folder != null ? folder : "($Inbox)";
                View view = mailDb.getView(viewName);
                List<MailMessage> messages = new ArrayList<>();
                if (view != null) {
                    try {
                        view.FTSearch(query, limit);
                        Document doc = view.getFirstDocument();
                        while (doc != null && messages.size() < limit) {
                            messages.add(toMailMessage(doc));
                            Document next = view.getNextDocument(doc);
                            recycle(doc);
                            doc = next;
                        }
                        if (doc != null) recycle(doc);
                    } finally {
                        recycle(view);
                    }
                } else {
                    // Fallback: formula search (no FT index on this folder)
                    DocumentCollection col = mailDb.FTSearch(query, limit);
                    try {
                        Document doc = col.getFirstDocument();
                        while (doc != null && messages.size() < limit) {
                            messages.add(toMailMessage(doc));
                            Document next = col.getNextDocument(doc);
                            recycle(doc);
                            doc = next;
                        }
                        if (doc != null) recycle(doc);
                    } finally {
                        recycle(col);
                    }
                }
                return messages;
            } finally {
                recycle(mailDb);
            }
        });
    }

    public boolean moveToFolder(String unid, String folder) {
        return pool.withSession(session -> {
            Database mailDb = getMailDatabase(session);
            try {
                Document doc = mailDb.getDocumentByUNID(unid);
                if (doc == null) return false;
                try {
                    doc.putInFolder(folder);
                    // Only remove from inbox if we're moving to a different folder
                    if (!"($Inbox)".equals(folder)) {
                        doc.removeFromFolder("($Inbox)");
                    }
                    return true;
                } finally {
                    recycle(doc);
                }
            } finally {
                recycle(mailDb);
            }
        });
    }

    private Database getMailDatabase(Session session) throws NotesException {
        String mailFile   = session.getEnvironmentString("MailFile", true);
        String mailServer = session.getEnvironmentString("MailServer", true);
        Database db = session.getDatabase(mailServer, mailFile);
        if (db == null) {
            throw new NotesOperationException("Cannot get mail database", null);
        }
        if (!db.isOpen()) {
            db.open();
        }
        if (!db.isOpen()) {
            recycle(db);
            throw new NotesOperationException(
                    "Cannot open mail database: " + mailServer + "!!" + mailFile, null);
        }
        return db;
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
