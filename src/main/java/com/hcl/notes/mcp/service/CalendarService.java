package com.hcl.notes.mcp.service;

import com.hcl.notes.mcp.adapter.CalendarAdapter;
import com.hcl.notes.mcp.model.CalendarEvent;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.List;

@Service
public class CalendarService {

    private final CalendarAdapter adapter;

    public CalendarService(CalendarAdapter adapter) {
        this.adapter = adapter;
    }

    public List<CalendarEvent> getEvents(Instant start, Instant end) {
        return adapter.getEvents(start, end);
    }

    public String createEvent(String title, Instant start, Instant end,
                               String location, List<String> attendees) {
        return adapter.createEvent(title, start, end, location, attendees);
    }
}
