package com.hcl.notes.mcp.adapter;

import com.hcl.notes.mcp.connection.NotesOperationException;
import com.hcl.notes.mcp.connection.NotesSessionPool;
import com.hcl.notes.mcp.model.MailMessage;
import lotus.domino.*;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.*;

@Component
public class MailAdapter {

    private final NotesSessionPool pool;

    public MailAdapter(@Lazy NotesSessionPool pool) {
        this.pool = pool;
    }

    public List<MailMessage> getInboxMessages(int count) {
        return pool.withSession(session -> {
            Database mailDb = getMailDatabase(session);
            View inbox = mailDb.getView("($Inbox)");
            List<MailMessage> messages = new ArrayList<>();
            Document doc = inbox.getLastDocument();
            int collected = 0;
            while (doc != null && collected < count) {
                messages.add(toMailMessage(doc));
                doc = inbox.getPrevDocument(doc);
                collected++;
            }
            return messages;
        });
    }

    public boolean sendMail(List<String> to, String subject, String body, List<String> cc) {
        return pool.withSession(session -> {
            Database mailDb = getMailDatabase(session);
            Document doc = mailDb.createDocument();
            doc.replaceItemValue("Form", "Memo");
            doc.replaceItemValue("SendTo", new Vector<>(to));
            doc.replaceItemValue("Subject", subject);
            doc.replaceItemValue("Body", body);
            if (cc != null && !cc.isEmpty()) {
                doc.replaceItemValue("CopyTo", new Vector<>(cc));
            }
            doc.send(false);
            return true;
        });
    }

    public List<MailMessage> searchMail(String query, String folder, int limit) {
        return pool.withSession(session -> {
            Database mailDb = getMailDatabase(session);
            String viewName = folder != null ? folder : "($Inbox)";
            View view = mailDb.getView(viewName);
            DocumentCollection col = view != null
                    ? view.FTSearch(query, limit)
                    : mailDb.search(query, null, limit);
            List<MailMessage> messages = new ArrayList<>();
            Document doc = col.getFirstDocument();
            while (doc != null && messages.size() < limit) {
                messages.add(toMailMessage(doc));
                doc = col.getNextDocument(doc);
            }
            return messages;
        });
    }

    public boolean moveToFolder(String unid, String folder) {
        return pool.withSession(session -> {
            Database mailDb = getMailDatabase(session);
            Document doc = mailDb.getDocumentByUNID(unid);
            if (doc == null) return false;
            doc.putInFolder(folder);
            doc.removeFromFolder("($Inbox)");
            return true;
        });
    }

    private Database getMailDatabase(Session session) throws NotesException {
        String mailFile = session.getEnvironmentString("MailFile", true);
        String mailServer = session.getEnvironmentString("MailServer", true);
        Database db = session.getDatabase(mailServer, mailFile);
        if (db == null || !db.isOpen()) {
            throw new NotesOperationException("Cannot open mail database", null);
        }
        return db;
    }

    private MailMessage toMailMessage(Document doc) throws NotesException {
        DateTime date = doc.getCreated();
        return new MailMessage(
                doc.getUniversalID(),
                doc.getItemValueString("From"),
                getSendTo(doc),
                doc.getItemValueString("Subject"),
                doc.getItemValueString("Body"),
                date != null ? Instant.ofEpochMilli(date.toJavaDate().getTime()) : null
        );
    }

    @SuppressWarnings("unchecked")
    private List<String> getSendTo(Document doc) throws NotesException {
        Vector<String> v = doc.getItemValue("SendTo");
        return v != null ? new ArrayList<>(v) : List.of();
    }
}
