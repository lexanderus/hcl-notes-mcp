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
 *   3. SandboxFixtureBuilder     — populates test NSFs with fixture data
 *
 * Run with: mvn verify -P sandbox-it
 *
 * NEVER run against production Notes databases (ADR-3).
 * NEVER call doc.send() in these tests (ADR-3).
 *
 * IMPORTANT: A single NotesSessionPool is shared across ALL IT test classes.
 * Notes JNI sinitThread/stermThread must only be called ONCE per process;
 * multiple cycles in the same JVM cause hangs. The shared pool is shut down
 * via a JVM shutdown hook when the test JVM exits.
 */
@Tag("sandbox-it")
abstract class AbstractSandboxIT {

    protected static NotesSessionPool pool;
    protected static MailDatabaseLocator mailDbLocator;

    /** Absolute path to tests/sandbox/ — set by failsafe via -Dsandbox.dir */
    protected static String sandboxDir;

    // Shared singletons — one Notes session pool for the entire IT test run.
    private static volatile NotesSessionPool sharedPool;
    private static volatile MailDatabaseLocator sharedMailLocator;
    private static volatile String sharedSandboxDir;

    @BeforeAll
    static void setUpNotesPool() {
        // Skip if not running inside the sandbox-it profile (no Notes JVM / no notes.ini)
        String notesIni = System.getProperty("notes.ini");
        assumeTrue(notesIni != null && !notesIni.isBlank(),
                "Skipped: not running under sandbox-it profile (notes.ini system property not set). "
                + "Run: mvn verify -P sandbox-it");

        // Create the shared pool exactly ONCE for the entire JVM process.
        // Notes JNI sinitThread/stermThread must not be called more than once per process
        // — repeated cycles in the same JVM cause the executor thread to hang.
        synchronized (AbstractSandboxIT.class) {
            if (sharedPool == null) {
                sharedSandboxDir = System.getProperty("sandbox.dir",
                        System.getProperty("user.dir") + "/tests/sandbox");

                NotesConnectionConfig config = new NotesConnectionConfig();
                // Use NOTES_PASSWORD env var (production user.id reused; no separate sandbox.id).
                // Also check JVM system property as fallback (set via -DNOTES_PASSWORD in Maven argLine).
                String pwd = System.getenv("NOTES_PASSWORD");
                if (pwd == null || pwd.isEmpty()) pwd = System.getProperty("NOTES_PASSWORD", "");
                config.setPassword(pwd);
                config.setTimeoutMs(30_000);

                // CRITICAL: set mailLocalDb to absolute sandbox path so MailDatabaseLocator
                // opens test-mail.nsf directly instead of reading MailServer/MailFile from
                // notes.ini. Notes JNI on Windows uses the production notes.ini from the registry,
                // not the -Dnotes.ini JVM property — session.getEnvironmentString("MailServer")
                // would return "eumail/iba" and hang trying to connect to the server.
                config.setMailLocalDb("!!" + sharedSandboxDir + "/Data/test-mail.nsf");

                NotesConnectionFactory factory = new NotesConnectionFactory(config);
                sharedPool = new NotesSessionPool(factory::createSession, config.getTimeoutMs());
                sharedMailLocator = new MailDatabaseLocator(config);

                // Shut down on JVM exit (after all IT tests complete)
                final NotesSessionPool poolToClose = sharedPool;
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    try { poolToClose.shutdown(); } catch (Exception ignored) {}
                }, "notes-jni-shutdown"));
            }
        }

        pool = sharedPool;
        mailDbLocator = sharedMailLocator;
        sandboxDir = sharedSandboxDir;
    }

    @AfterAll
    static void tearDownNotesPool() {
        // Do NOT shut down the shared pool here — it is reused by subsequent IT classes.
        // Shutdown happens via the JVM shutdown hook registered in setUpNotesPool().
        pool = null;
        mailDbLocator = null;
    }

    /**
     * Returns the database path for a sandbox NSF file using an absolute path.
     * Format: "!!<absolutePath>" — server="" means local, path is absolute.
     * Using absolute paths avoids dependency on Notes Data directory resolution,
     * which may differ between the sandbox and production Notes installations.
     */
    protected static String sandboxDb(String filename) {
        return "!!" + sandboxDir + "/Data/" + filename;
    }
}
