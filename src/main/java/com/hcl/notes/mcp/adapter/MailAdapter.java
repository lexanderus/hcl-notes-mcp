package com.hcl.notes.mcp.adapter;

import com.hcl.notes.mcp.connection.NotesSessionPool;
import com.hcl.notes.mcp.model.MailMessage;
import lotus.domino.*;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.*;

import static com.hcl.notes.mcp.adapter.NotesUtils.recycle;

@Component
public class MailAdapter {

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
                    DocumentCollection col = db.FTSearch(query, limit);
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
                recycle(db);
            }
        });
    }

    public boolean moveToFolder(String unid, String folder) {
        return pool.withSession(session -> {
            Database db = mailDb.openMailDatabase(session, "mail");
            try {
                Document doc = db.getDocumentByUNID(unid);
                if (doc == null) return false;
                try {
                    doc.putInFolder(folder);
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
