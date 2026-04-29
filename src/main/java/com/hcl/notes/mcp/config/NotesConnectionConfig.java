package com.hcl.notes.mcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Notes LOCAL JNI connection settings. REMOTE/CORBA removed per ADR-2. */
@Component
@ConfigurationProperties(prefix = "notes.connection")
public class NotesConnectionConfig {
    /** Notes ID password. Empty string if ID has no password (sandbox). */
    private String password;

    /**
     * Timeout in milliseconds for Notes operations and session initialization.
     * Notes JNI cannot be interrupted — on timeout the operation continues running
     * until completion; subsequent calls queue on the notes-jni thread.
     */
    private long timeoutMs = 30_000;

    /**
     * Optional: path to locally replicated mail database, relative to Notes Data dir.
     * When set, MailDatabaseLocator opens this path with an empty server (local access)
     * instead of using MailServer/MailFile from notes.ini.
     * Use this when the Notes router is not running (no Notes.exe) but a local replica exists.
     * Example: "mail/ashevele.nsf"
     */
    private String mailLocalDb;

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public long getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }
    public String getMailLocalDb() { return mailLocalDb; }
    public void setMailLocalDb(String mailLocalDb) { this.mailLocalDb = mailLocalDb; }
}
