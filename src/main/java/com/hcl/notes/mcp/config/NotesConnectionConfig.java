package com.hcl.notes.mcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Notes LOCAL JNI connection settings. REMOTE/CORBA removed per ADR-2. */
@Component
@ConfigurationProperties(prefix = "notes.connection")
public class NotesConnectionConfig {
    private String password;
    private String idFile;
    private long timeoutMs = 30_000;

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getIdFile() { return idFile; }
    public void setIdFile(String idFile) { this.idFile = idFile; }
    public long getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }
}
