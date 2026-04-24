package com.hcl.notes.mcp.service;

import com.hcl.notes.mcp.adapter.MailAdapter;
import com.hcl.notes.mcp.model.MailMessage;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MailServiceTest {

    private final MailAdapter adapter = mock(MailAdapter.class);
    private final MailService service = new MailService(adapter);

    @Test
    void getInbox_returnsMessages() {
        var msg = new MailMessage("U1", "alice@x.com", List.of("bob@x.com"),
                "Hello", "Body", Instant.now());
        when(adapter.getInboxMessages(20)).thenReturn(List.of(msg));

        var result = service.getInbox(20);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).subject()).isEqualTo("Hello");
    }

    @Test
    void sendMail_delegatesToAdapter() {
        when(adapter.sendMail(any(), any(), any(), any())).thenReturn(true);
        boolean result = service.sendMail(List.of("bob@x.com"), "Hi", "Body", null);
        assertThat(result).isTrue();
        verify(adapter).sendMail(List.of("bob@x.com"), "Hi", "Body", null);
    }

    @Test
    void searchMail_delegatesToAdapter() {
        when(adapter.searchMail("query", null, 50)).thenReturn(List.of());
        var result = service.searchMail("query", null, 50);
        assertThat(result).isEmpty();
        verify(adapter).searchMail("query", null, 50);
    }
}
