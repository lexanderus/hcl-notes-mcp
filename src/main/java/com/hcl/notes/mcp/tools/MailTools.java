package com.hcl.notes.mcp.tools;

import com.hcl.notes.mcp.service.MailService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;

@Component
public class MailTools {

    private final MailService service;

    public MailTools(MailService service) {
        this.service = service;
    }

    @Tool(description = "Get the N most recent messages from the Notes inbox.")
    public Map<String, Object> notesGetInbox(
            @ToolParam(description = "Number of messages to return (default 20)", required = false) Integer count) {
        return Map.of("messages", service.getInbox(count != null ? count : 20));
    }

    @Tool(description = "Send an email via HCL Notes.")
    public Map<String, Object> notesSendMail(
            @ToolParam(description = "List of recipient email addresses") List<String> to,
            @ToolParam(description = "Email subject") String subject,
            @ToolParam(description = "Email body (plain text)") String body,
            @ToolParam(description = "CC recipients", required = false) List<String> cc) {
        return Map.of("success", service.sendMail(to, subject, body, cc));
    }

    @Tool(description = "Search mail messages in Notes.")
    public Map<String, Object> notesSearchMail(
            @ToolParam(description = "Search query") String query,
            @ToolParam(description = "Folder to search, default ($Inbox)", required = false) String folder,
            @ToolParam(description = "Max results (default 50)", required = false) Integer limit) {
        return Map.of("messages", service.searchMail(query, folder, limit != null ? limit : 50));
    }

    @Tool(description = "Move a mail message to a specified folder.")
    public Map<String, Object> notesMoveToFolder(
            @ToolParam(description = "Document UNID of the mail message") String unid,
            @ToolParam(description = "Target folder name") String folder) {
        return Map.of("success", service.moveToFolder(unid, folder));
    }
}
