package com.hcl.notes.mcp.it;

import com.hcl.notes.mcp.adapter.MailDatabaseLocator;
import com.hcl.notes.mcp.config.NotesConnectionConfig;
import com.hcl.notes.mcp.connection.NotesConnectionFactory;
import com.hcl.notes.mcp.connection.NotesSessionPool;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Base class for sandbox integration tests (ADR-3).
 *
 * Prerequisites (run before the test suite):
 *   1. tools\setup-deps.cmd      — installs Notes.jar into local Maven repo
 *   2. tools\init-sandbox.cmd    — creates tests/sandbox/ structure and notes.ini
 *   3. tests/sandbox/id/sandbox.id — password-free Notes ID (see ADR-3)
 *   4. SandboxFixtureBuilder     — populates test NSFs with fixture data
 *
 * Run with: mvn verify -P sandbox-it
 *
 * NEVER run against production Notes databases (ADR-3).
 * NEVER call doc.send() in these tests (ADR-3).
 */
@Tag("sandbox-it")
abstract class AbstractSandboxIT {

    protected static NotesSessionPool pool;
    protected static MailDatabaseLocator mailDbLocator;

    /** Absolute path to tests/sandbox/ — set by failsafe via -Dsandbox.dir */
    protected static String sandboxDir;

    @BeforeAll
    static void setUpNotesPool() {
        // Skip if not running inside the sandbox-it profile (no Notes JVM / no notes.ini)
        String notesIni = System.getProperty("notes.ini");
        assumeTrue(notesIni != null && !notesIni.isBlank(),
                "Skipped: not running under sandbox-it profile (notes.ini system property not set). "
                + "Run: mvn verify -P sandbox-it");

        sandboxDir = System.getProperty("sandbox.dir",
                System.getProperty("user.dir") + "/tests/sandbox");

        NotesConnectionConfig config = new NotesConnectionConfig();
        // Sandbox ID has no password (ADR-3)
        config.setPassword("");
        config.setTimeoutMs(30_000);

        NotesConnectionFactory factory = new NotesConnectionFactory(config);
        pool = new NotesSessionPool(factory::createSession, config.getTimeoutMs());
        mailDbLocator = new MailDatabaseLocator();
    }

    @AfterAll
    static void tearDownNotesPool() {
        if (pool != null) {
            pool.shutdown();
            pool = null;
        }
        // After shutdown, kill stale java.exe to release Notes ID lock (see CLAUDE.md)
    }

    /** Returns the sandbox-relative database path: "!!<filename>" for local NSF. */
    protected static String sandboxDb(String filename) {
        return "!!" + filename;
    }
}
