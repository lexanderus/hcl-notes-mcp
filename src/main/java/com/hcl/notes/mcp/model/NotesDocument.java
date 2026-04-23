package com.hcl.notes.mcp.model;

import java.time.Instant;
import java.util.Map;

public record NotesDocument(
        String unid,
        Instant created,
        Instant modified,
        Map<String, Object> fields
) {}
