package com.hcl.notes.mcp.service;

import com.hcl.notes.mcp.adapter.CalendarAdapter;
import com.hcl.notes.mcp.model.CalendarEvent;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CalendarServiceTest {

    private final CalendarAdapter adapter = mock(CalendarAdapter.class);
    private final CalendarService service = new CalendarService(adapter);

    @Test
    void getEvents_delegatesToAdapter() {
        Instant start = Instant.parse("2026-04-01T00:00:00Z");
        Instant end = Instant.parse("2026-04-30T23:59:59Z");
        var event = new CalendarEvent("U1", "Team Meeting", start, end, "Room 1", List.of());
        when(adapter.getEvents(start, end)).thenReturn(List.of(event));

        var result = service.getEvents(start, end);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("Team Meeting");
    }

    @Test
    void createEvent_delegatesToAdapter() {
        Instant start = Instant.parse("2026-05-01T10:00:00Z");
        Instant end = Instant.parse("2026-05-01T11:00:00Z");
        when(adapter.createEvent("Standup", start, end, null, null)).thenReturn("UNID-1");

        String unid = service.createEvent("Standup", start, end, null, null);
        assertThat(unid).isEqualTo("UNID-1");
    }
}
