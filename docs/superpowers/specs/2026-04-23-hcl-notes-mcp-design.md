# HCL Notes MCP Server — Design Specification

**Date:** 2026-04-23  
**Status:** Approved  
**Author:** Claude (brainstorming session)

---

## Overview

An MCP (Model Context Protocol) server that exposes HCL Notes / Domino data to Claude AI. Built in Java with Spring Boot, it connects to a Domino server via NRPC and provides Claude with tools to work with Notes databases, mail, calendar, and tasks.

This is the **first publicly available** MCP server for HCL Notes — no existing solution was found in the MCP registry or public ecosystem.

---

## Goals

- Enable Claude to read, create, update, and delete Notes documents
- Support mail operations: inbox, send, search, folder management
- Support calendar and task operations
- Connect to Domino server without requiring Notes client to be running
- Ship as a single executable JAR

---

## Non-Goals

- GUI or web interface
- Support for Notes formula language execution
- Replication management
- Full Domino administration API

---

## Architecture

**Layered architecture with 4 layers:**

```
MCP Tools Layer     → tool definitions exposed to Claude
Service Layer       → business logic per domain
Notes Adapter Layer → abstraction over lotus.domino.*
Connection Pool     → managed NRPC sessions to Domino
```

Each layer communicates only with the layer directly below it. The `lotus.domino.*` API is used exclusively in the Adapter layer, keeping business logic testable with mocks.

---

## Project Structure

```
hcl-notes-mcp/
├── src/main/java/com/hcl/notes/mcp/
│   ├── McpServerApplication.java
│   ├── config/
│   │   ├── NotesConnectionConfig.java
│   │   └── McpServerConfig.java
│   ├── connection/
│   │   ├── NotesSessionPool.java
│   │   ├── NotesConnectionFactory.java
│   │   └── ConnectionMode.java          # enum: LOCAL | REMOTE
│   ├── adapter/
│   │   ├── DatabaseAdapter.java
│   │   ├── MailAdapter.java
│   │   ├── CalendarAdapter.java
│   │   └── TaskAdapter.java
│   ├── service/
│   │   ├── DatabaseService.java
│   │   ├── MailService.java
│   │   ├── CalendarService.java
│   │   └── TaskService.java
│   ├── tools/
│   │   ├── DatabaseTools.java
│   │   ├── MailTools.java
│   │   ├── CalendarTools.java
│   │   └── TaskTools.java
│   └── model/
│       ├── NotesDocument.java
│       ├── MailMessage.java
│       ├── CalendarEvent.java
│       └── NotesTask.java
├── src/main/resources/
│   └── application.yml
└── pom.xml
```

---

## Database Handle Convention

Tools that reference a database use a **`databasePath` string** in the format `"server!!path"` (e.g., `"domino.company.com!!mail/jdoe.nsf"`). For local databases the server part is empty: `"!!mail/jdoe.nsf"`. `notes_open_database` validates that the database exists and returns this canonical path string for use in subsequent calls. No opaque session handles are exposed to Claude.

---

## MCP Tools

### Databases (Priority 1)

| Tool | Parameters (type) | Returns | Description |
|---|---|---|---|
| `notes_open_database` | `server: string`, `path: string` | `{ databasePath: string, title: string }` | Validate and open a Notes database |
| `notes_list_views` | `databasePath: string` | `{ views: [{name, entryCount}] }` | List all views in a database |
| `notes_get_view_entries` | `databasePath: string`, `view: string`, `filter?: string`, `limit?: int (default 50)`, `offset?: int (default 0)` | `{ entries: [NotesDocument], total: int }` | Get paginated documents from a view |
| `notes_get_document` | `databasePath: string`, `unid: string` | `NotesDocument \| null` | Get document by UNID |
| `notes_search_documents` | `databasePath: string`, `query: string`, `limit?: int (default 50)`, `offset?: int (default 0)` | `{ entries: [NotesDocument], total: int }` | Full-text search with pagination |
| `notes_create_document` | `databasePath: string`, `fields: object (key→value map)` | `{ unid: string }` | Create a new document |
| `notes_update_document` | `databasePath: string`, `unid: string`, `fields: object (key→value map)` | `{ unid: string }` | Update document fields (partial update) |
| `notes_delete_document` | `databasePath: string`, `unid: string` | `{ success: boolean }` | Delete a document |

**`NotesDocument` shape:**
```json
{
  "unid": "string",
  "created": "ISO-8601 datetime",
  "modified": "ISO-8601 datetime",
  "fields": { "FieldName": "value | [value]" }
}
```

### Mail (Priority 2)

| Tool | Parameters (type) | Returns | Description |
|---|---|---|---|
| `notes_get_inbox` | `count?: int (default 20)` | `{ messages: [MailMessage] }` | Get N latest inbox messages |
| `notes_send_mail` | `to: string[]`, `subject: string`, `body: string`, `cc?: string[]` | `{ success: boolean }` | Send an email |
| `notes_search_mail` | `query: string`, `folder?: string (default "($Inbox)")`, `limit?: int (default 50)` | `{ messages: [MailMessage] }` | Search mail |
| `notes_move_to_folder` | `unid: string`, `folder: string` | `{ success: boolean }` | Move mail to a folder |

**`MailMessage` shape:**
```json
{
  "unid": "string",
  "from": "string",
  "to": ["string"],
  "subject": "string",
  "body": "string (plain text)",
  "date": "ISO-8601 datetime"
}
```

### Calendar & Tasks (Priority 3)

| Tool | Parameters (type) | Returns | Description |
|---|---|---|---|
| `notes_get_calendar_events` | `startDate: string (ISO-8601)`, `endDate: string (ISO-8601)` | `{ events: [CalendarEvent] }` | Get events in date range |
| `notes_create_event` | `title: string`, `start: string (ISO-8601)`, `end: string (ISO-8601)`, `location?: string`, `attendees?: string[]` | `{ unid: string }` | Create a calendar event |
| `notes_get_tasks` | `completed?: boolean (default false)`, `limit?: int (default 50)` | `{ tasks: [NotesTask] }` | Get task list |

**`CalendarEvent` shape:**
```json
{
  "unid": "string",
  "title": "string",
  "start": "ISO-8601 datetime",
  "end": "ISO-8601 datetime",
  "location": "string | null",
  "attendees": ["string"]
}
```

**`NotesTask` shape:**
```json
{
  "unid": "string",
  "subject": "string",
  "dueDate": "ISO-8601 date | null",
  "completed": "boolean",
  "priority": "HIGH | MEDIUM | LOW | NONE"
}
```

**Total: 15 tools**

---

## Configuration

### `application.yml`

```yaml
notes:
  connection:
    mode: REMOTE          # LOCAL | REMOTE
    server: domino.company.com
    username: user@company.com
    password: ${NOTES_PASSWORD}
    pool-size: 5
    timeout-ms: 30000

server:
  port: 8080

mcp:
  name: hcl-notes-mcp
  version: 1.0.0
```

### Claude Desktop integration (`claude_desktop_config.json`)

```json
{
  "mcpServers": {
    "hcl-notes": {
      "url": "http://localhost:8080/sse"
    }
  }
}
```

### Launch

```bash
NOTES_PASSWORD=secret java -jar hcl-notes-mcp.jar
```

---

## Technology Stack

| Component | Technology |
|---|---|
| Language | Java 17+ |
| Framework | Spring Boot 3.x |
| MCP transport | SSE/HTTP (embedded Netty) |
| MCP SDK | `io.modelcontextprotocol:mcp-spring-webflux` |
| Notes API | `lotus.domino.*` via `Notes.jar` / `NCSO.jar` |
| Build | Maven |
| Auth | Username/password (NRPC to Domino) |
| Connection | Pooled NRPC sessions |

---

## Connection Modes

| Mode | Notes client required | Domino server required | Notes runtime required |
|---|---|---|---|
| LOCAL | Running | No | Yes (`Notes.jar` + JNI DLLs) |
| REMOTE | No | Yes | Yes (`NCSO.jar` only) |

`ConnectionMode` is set in `application.yml` and controls which factory method `NotesConnectionFactory` uses.

### LOCAL mode setup (Windows only)

LOCAL mode uses JNI and requires the Notes client to be installed. The JVM must be able to locate the native libraries:

1. Notes client installed at e.g. `C:\Program Files\HCL\Notes`
2. Add Notes install directory to system `PATH` before launching the JAR
3. Add `-Djava.library.path="C:\Program Files\HCL\Notes"` to the JVM arguments
4. Use `Notes.jar` from the Notes installation as a local Maven dependency

LOCAL mode is **Windows-only** (Notes client is Windows-only). REMOTE mode via `NCSO.jar` works on any OS.

---

## Security

- **MCP endpoint:** The SSE endpoint on port 8080 is unauthenticated by default. It is intended for localhost use only. Do not expose port 8080 externally without adding authentication (Spring Security basic auth can be enabled via config).
- **Credentials:** Domino password passed via `NOTES_PASSWORD` environment variable — never hardcoded in config files.
- **NRPC transport:** NRPC does not use TLS by default. For encrypted transport, configure the Domino server to require SSL on NRPC (port 1352) and set `notes.connection.ssl=true` in config.
- **Destructive operations:** `notes_delete_document` is included but can be disabled by setting `notes.tools.delete-enabled=false` in config.

---

## Error Handling

- Connection failures: retry with exponential backoff (3 attempts), then return structured error to Claude
- Session expiry: session pool detects stale sessions and re-authenticates transparently
- Document not found: return `null` result with descriptive message, not exception
- Notes API exceptions: caught at adapter layer, translated to domain exceptions, surfaced as MCP tool errors
- Invalid `databasePath` format: validated at tool layer before reaching adapter

---

## Testing Strategy

- **Unit tests:** Service and adapter layers tested with Mockito mocks — no real Notes server needed
- **Integration tests:** Marked `@Tag("integration")`, skipped by default, require live Domino server
- **MCP smoke test:** Start server, call `notes_list_views` on a test database, verify response shape
