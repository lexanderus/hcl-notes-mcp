package com.hcl.notes.mcp.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.Instant;
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
}
