package com.hcl.notes.mcp.model;

import java.time.Instant;
import java.util.List;

public record MailMessage(
        String unid,
        String from,
        List<String> to,
        String subject,
        String body,
        Instant date
) {}
