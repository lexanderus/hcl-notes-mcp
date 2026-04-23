package com.hcl.notes.mcp.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class ModelSerializationTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void notesDocumentRoundTrip() throws Exception {
        var doc = new NotesDocument("ABC123", Instant.now(), Instant.now(),
                Map.of("Subject", "Hello"));
        String json = mapper.writeValueAsString(doc);
        var result = mapper.readValue(json, NotesDocument.class);
        assertThat(result.unid()).isEqualTo("ABC123");
        assertThat(result.fields()).containsKey("Subject");
    }

    @Test
    void mailMessageRoundTrip() throws Exception {
        var msg = new MailMessage("U1", "alice@x.com", List.of("bob@x.com"),
                "Hi", "Body text", Instant.now());
        String json = mapper.writeValueAsString(msg);
        var result = mapper.readValue(json, MailMessage.class);
        assertThat(result.subject()).isEqualTo("Hi");
    }

    @Test
    void calendarEventRoundTrip() throws Exception {
        var event = new CalendarEvent("E1", "Standup", Instant.now(), Instant.now(),
                "Room 1", List.of("bob@x.com"));
        String json = mapper.writeValueAsString(event);
        var result = mapper.readValue(json, CalendarEvent.class);
        assertThat(result.title()).isEqualTo("Standup");
        assertThat(result.attendees()).containsExactly("bob@x.com");
    }

    @Test
    void notesTaskRoundTrip() throws Exception {
        var task = new NotesTask("T1", "Fix bug", LocalDate.of(2026, 5, 1),
                false, NotesTask.Priority.HIGH);
        String json = mapper.writeValueAsString(task);
        var result = mapper.readValue(json, NotesTask.class);
        assertThat(result.subject()).isEqualTo("Fix bug");
        assertThat(result.priority()).isEqualTo(NotesTask.Priority.HIGH);
        assertThat(result.dueDate()).isEqualTo(LocalDate.of(2026, 5, 1));
    }
}
