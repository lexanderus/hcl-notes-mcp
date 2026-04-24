package com.hcl.notes.mcp.service;

import com.hcl.notes.mcp.adapter.MailAdapter;
import com.hcl.notes.mcp.model.MailMessage;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class MailService {

    private final MailAdapter adapter;

    public MailService(MailAdapter adapter) {
        this.adapter = adapter;
    }

    public List<MailMessage> getInbox(int count) {
        return adapter.getInboxMessages(count);
    }

    public boolean sendMail(List<String> to, String subject, String body, List<String> cc) {
        return adapter.sendMail(to, subject, body, cc);
    }

    public List<MailMessage> searchMail(String query, String folder, int limit) {
        return adapter.searchMail(query, folder, limit);
    }

    public boolean moveToFolder(String unid, String folder) {
        return adapter.moveToFolder(unid, folder);
    }
}
