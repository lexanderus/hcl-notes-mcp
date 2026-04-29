package com.hcl.notes.mcp.it;

import com.hcl.notes.mcp.adapter.CalendarAdapter;
import com.hcl.notes.mcp.model.CalendarEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for CalendarAdapter against sandbox test-mail.nsf.
 * SandboxFixtureBuilder must be run first.
 *
 * Fixture volumes (ADR-3): 20 calendar events spread ±30 days from today.
 */
class CalendarAdapterIT extends AbstractSandboxIT {

    private static CalendarAdapter adapter;

    @BeforeAll
    static void setUpAdapter() {
        adapter = new CalendarAdapter(pool, mailDbLocator);
    }

    @Test
    void getEvents_returnsEventsInRange() {
        Instant start = Instant.now().minus(31, ChronoUnit.DAYS);
        Instant end   = Instant.now().plus(31, ChronoUnit.DAYS);

        List<CalendarEvent> events = adapter.getEvents(start, end);

        // SandboxFixtureBuilder creates 20 events in ±30-day window
        assertThat(events).isNotEmpty();
    }

    @Test
    void getEvents_eachEventHasTitle() {
        Instant start = Instant.now().minus(31, ChronoUnit.DAYS);
        Instant end   = Instant.now().plus(31, ChronoUnit.DAYS);

        List<CalendarEvent> events = adapter.getEvents(start, end);

        events.forEach(e -> assertThat(e.title()).isNotBlank());
    }

    @Test
    void getEvents_emptyRangeInDistantFuture() {
        Instant start = Instant.now().plus(365, ChronoUnit.DAYS);
        Instant end   = Instant.now().plus(366, ChronoUnit.DAYS);

        List<CalendarEvent> events = adapter.getEvents(start, end);

        // No fixtures in distant future
        assertThat(events).isEmpty();
    }

    @Test
    void createEvent_savesAndIsRetrievable() {
        Instant start = Instant.now().plus(60, ChronoUnit.DAYS);
        Instant end   = start.plus(1, ChronoUnit.HOURS);

        String unid = adapter.createEvent("IT-Test-Event", start, end, "Test Room", null);

        assertThat(unid).isNotBlank();

        // Verify it appears in the range query
        List<CalendarEvent> events = adapter.getEvents(
                start.minus(1, ChronoUnit.HOURS),
                end.plus(1, ChronoUnit.HOURS));

        assertThat(events).anyMatch(e -> e.unid().equals(unid));
    }
}
