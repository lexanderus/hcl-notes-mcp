package com.hcl.notes.mcp.tools;

import com.hcl.notes.mcp.service.CalendarService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
public class CalendarTools {

    private final CalendarService service;

    public CalendarTools(CalendarService service) {
        this.service = service;
    }

    @Tool(description = "Get Notes calendar events in a date range.")
    public Map<String, Object> notesGetCalendarEvents(
            @ToolParam(description = "Start date in ISO-8601 format, e.g. 2026-04-01T00:00:00Z") String startDate,
            @ToolParam(description = "End date in ISO-8601 format, e.g. 2026-04-30T23:59:59Z") String endDate) {
        return Map.of("events", service.getEvents(Instant.parse(startDate), Instant.parse(endDate)));
    }

    @Tool(description = "Create a new calendar event in HCL Notes.")
    public Map<String, Object> notesCreateEvent(
            @ToolParam(description = "Event title") String title,
            @ToolParam(description = "Start datetime in ISO-8601") String start,
            @ToolParam(description = "End datetime in ISO-8601") String end,
            @ToolParam(description = "Location", required = false) String location,
            @ToolParam(description = "Attendee email addresses", required = false) List<String> attendees) {
        return Map.of("unid", service.createEvent(title,
                Instant.parse(start), Instant.parse(end), location, attendees));
    }
}
