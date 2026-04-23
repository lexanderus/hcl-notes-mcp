package com.hcl.notes.mcp.model;

import java.time.LocalDate;

public record NotesTask(
        String unid,
        String subject,
        LocalDate dueDate,
        boolean completed,
        Priority priority
) {
    public enum Priority { HIGH, MEDIUM, LOW, NONE }
}
