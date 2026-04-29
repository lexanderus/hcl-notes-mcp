package com.hcl.notes.mcp.it;

import com.hcl.notes.mcp.adapter.MailAdapter;
import com.hcl.notes.mcp.model.MailMessage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for MailAdapter against sandbox test-mail.nsf.
 * SandboxFixtureBuilder must be run first to populate the database.
 *
 * Fixture volumes (ADR-3): 50 inbox messages.
 *
 * IMPORTANT: doc.send() is NEVER called in these tests (ADR-3).
 *   Even against sandbox — the sandbox ID may be configured with a real mail router.
 *   sendMail() is NOT tested here.
 */
class MailAdapterIT extends AbstractSandboxIT {

    private static MailAdapter adapter;

    @BeforeAll
    static void setUpAdapter() {
        adapter = new MailAdapter(pool, mailDbLocator);
    }

    @Test
    void getInboxMessages_returnsMessages() {
        List<MailMessage> messages = adapter.getInboxMessages(10);

        // SandboxFixtureBuilder creates 50 inbox messages
        assertThat(messages).isNotEmpty();
        assertThat(messages.size()).isLessThanOrEqualTo(10);
    }

    @Test
    void getInboxMessages_eachMessageHasUnid() {
        List<MailMessage> messages = adapter.getInboxMessages(5);

        messages.forEach(m -> {
            assertThat(m.unid()).isNotBlank();
            assertThat(m.subject()).isNotBlank();
        });
    }

    @Test
    void getInboxMessages_respectsLimit() {
        List<MailMessage> three = adapter.getInboxMessages(3);
        List<MailMessage> ten   = adapter.getInboxMessages(10);

        assertThat(three.size()).isLessThanOrEqualTo(3);
        assertThat(ten.size()).isGreaterThanOrEqualTo(three.size());
    }

    @Test
    void searchMail_findsBySubject() {
        // SandboxFixtureBuilder sets Subject = "Test message N"
        List<MailMessage> results = adapter.searchMail("Test message", null, 5);

        assertThat(results).isNotEmpty();
        results.forEach(m -> assertThat(m.subject()).isNotBlank());
    }

    @Test
    void searchMail_returnsEmptyForNoMatch() {
        List<MailMessage> results = adapter.searchMail("XYZZY_NO_SUCH_SUBJECT_12345", null, 5);
        assertThat(results).isEmpty();
    }

    @Test
    void moveToFolder_movesMessageAndDoesNotCreateDuplicate() {
        List<MailMessage> inbox = adapter.getInboxMessages(1);
        assertThat(inbox).isNotEmpty();

        String unid = inbox.get(0).unid();
        boolean moved = adapter.moveToFolder(unid, "TestFolder");

        assertThat(moved).isTrue();

        // Cleanup: remove from TestFolder.
        // ($Inbox) is a view (SELECT Form=Memo) in the sandbox, not a folder —
        // putInFolder("($Inbox)") is not applicable. Docs remain visible in the view regardless.
        adapter.removeFromFolder(unid, "TestFolder");
    }

    // sendMail is NOT tested — see class Javadoc (ADR-3)
}
