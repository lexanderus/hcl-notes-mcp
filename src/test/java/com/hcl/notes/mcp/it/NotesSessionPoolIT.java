package com.hcl.notes.mcp.it;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies Notes JNI thread-affinity contract after Phase 1 refactor (B1).
 */
class NotesSessionPoolIT extends AbstractSandboxIT {

    @Test
    void sessionInitializesSuccessfully() {
        String userName = pool.withSession(s -> s.getUserName());
        assertThat(userName).isNotBlank();
        System.out.println("Notes session user: " + userName);
    }

    @Test
    void allOperationsRunOnNotesJniThread() {
        AtomicReference<String> threadName = new AtomicReference<>();

        pool.withSession(s -> {
            threadName.set(Thread.currentThread().getName());
            return null;
        });

        // All Notes work must happen on the dedicated executor thread
        assertThat(threadName.get()).isEqualTo("notes-jni");
    }

    @Test
    void multipleCallsUseSameThread() {
        AtomicReference<Long> firstThreadId  = new AtomicReference<>();
        AtomicReference<Long> secondThreadId = new AtomicReference<>();

        pool.withSession(s -> { firstThreadId.set(Thread.currentThread().getId());  return null; });
        pool.withSession(s -> { secondThreadId.set(Thread.currentThread().getId()); return null; });

        // Single-thread executor → always the same thread
        assertThat(firstThreadId.get()).isEqualTo(secondThreadId.get());
    }

    @Test
    void sessionEnvironmentStringReturnsMailFile() {
        String mailFile = pool.withSession(s -> s.getEnvironmentString("MailFile", true));
        // In sandbox notes.ini, MailFile should be set; if blank — sandbox setup incomplete
        assertThat(mailFile)
                .as("MailFile in sandbox notes.ini must be set — re-run tools/init-sandbox.cmd")
                .isNotBlank();
    }
}
