package com.hcl.notes.mcp.config;

import com.hcl.notes.mcp.connection.ConnectionMode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "notes.connection")
public class NotesConnectionConfig {
    private ConnectionMode mode = ConnectionMode.REMOTE;
    private String server;
    private String username;
    private String password;
    private String idFile;
    private int poolSize = 5;
    private long timeoutMs = 30_000;

    public ConnectionMode getMode() { return mode; }
    public void setMode(ConnectionMode mode) { this.mode = mode; }
    public String getServer() { return server; }
    public void setServer(String server) { this.server = server; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getIdFile() { return idFile; }
    public void setIdFile(String idFile) { this.idFile = idFile; }
    public int getPoolSize() { return poolSize; }
    public void setPoolSize(int poolSize) { this.poolSize = poolSize; }
    public long getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }
}
