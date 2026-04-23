package com.hcl.notes.mcp.model;

import java.time.Instant;
import java.util.List;

public record CalendarEvent(
        String unid,
        String title,
        Instant start,
        Instant end,
        String location,
        List<String> attendees
) {}
