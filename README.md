# HCL Notes MCP Server

MCP (Model Context Protocol) server for integrating HCL Notes / IBM Domino with Claude AI.
Exposes 15 tools for working with databases, mail, calendar and tasks.

## Requirements

| Tool | Version |
|------|---------|
| Java | 17+ |
| Maven | 3.9+ |
| HCL Notes / Domino | 12+ (remote API: NCSO.jar) |

## Quick Start

### 1. Install NCSO.jar

Copy `NCSO.jar` from the HCL Notes server (`<domino>/jvm/lib/ext/NCSO.jar`) and install it into local Maven repo:

```bash
mvn install:install-file \
  -Dfile=/path/to/NCSO.jar \
  -DgroupId=com.hcl.notes \
  -DartifactId=ncso \
  -Dversion=14.0 \
  -Dpackaging=jar
```

### 2. Configure the server

Edit `src/main/resources/application.yml` or set environment variables:

```yaml
notes:
  connection:
    mode: REMOTE          # REMOTE (via NCSO) or LOCAL (requires local Notes client)
    server: domino.company.com
    username: user@company.com
    password: ${NOTES_PASSWORD}   # set via env var
    pool-size: 5
    timeout-ms: 30000
  tools:
    delete-enabled: true  # set false to disable notesDeleteDocument
```

### 3. Build and run

```bash
export NOTES_PASSWORD=your_password
mvn package -DskipTests
java -jar target/hcl-notes-mcp-1.0.0.jar
```

Server starts on `http://localhost:8080` (SSE/HTTP transport).

### 4. Connect Claude

In Claude Desktop (`claude_desktop_config.json`):

```json
{
  "mcpServers": {
    "hcl-notes": {
      "url": "http://localhost:8080/sse"
    }
  }
}
```

In Claude Code:

```bash
claude mcp add hcl-notes --transport http http://localhost:8080/sse
```

---

## Available Tools

### Database (8 tools)

| Tool | Description |
|------|-------------|
| `notesOpenDatabase` | Open a database by path and return its title |
| `notesListViews` | List all views in a database |
| `notesGetViewEntries` | Get documents from a view with optional key filter, pagination |
| `notesGetDocument` | Get a single document by UNID |
| `notesSearchDocuments` | Full-text search across a database |
| `notesCreateDocument` | Create a document with arbitrary fields |
| `notesUpdateDocument` | Update fields of an existing document |
| `notesDeleteDocument` | Delete a document (requires `delete-enabled: true`) |

**Database path format:** `server!!path`, e.g. `domino.host!!mail/jdoe.nsf`

### Mail (4 tools)

| Tool | Description |
|------|-------------|
| `notesGetInbox` | Get the last N messages from the inbox |
| `notesSendMail` | Send a message to one or more recipients |
| `notesSearchMail` | Search messages by query in inbox or a specified folder |
| `notesMoveToFolder` | Move a message (by UNID) to a folder |

### Calendar (2 tools)

| Tool | Description |
|------|-------------|
| `notesGetCalendarEvents` | Get events for a date range (ISO-8601 strings) |
| `notesCreateEvent` | Create a calendar appointment |

### Tasks (1 tool)

| Tool | Description |
|------|-------------|
| `notesGetTasks` | Get tasks from the to-do list, with completed/pending filter |

---

## Example Prompts

```
Show me the last 10 unread emails from my inbox.

Search for messages from John about the Q1 report.

Create a calendar event "Sprint Review" on 2025-05-15 from 14:00 to 15:00.

Show all documents in the view "By Author" in the database domino.local!!apps/crm.nsf.

Find all documents where Subject contains "Invoice" in domino.local!!finance/invoices.nsf.
```

---

## Architecture

```
Claude AI
   │  MCP (SSE/HTTP)
   ▼
McpServerApplication (Spring Boot + Netty, port 8080)
   │
   ├── DatabaseTools  ──► DatabaseService  ──► DatabaseAdapter
   ├── MailTools      ──► MailService      ──► MailAdapter
   ├── CalendarTools  ──► CalendarService  ──► CalendarAdapter
   └── TaskTools      ──► TaskService      ──► TaskAdapter
                                                    │
                                               NotesSessionPool
                                                    │
                                               lotus.domino API (NCSO.jar)
                                                    │
                                           HCL Domino Server
```

**Key design decisions:**
- `NotesSessionPool` — thread-safe pool of Notes sessions (`ArrayBlockingQueue`); Notes sessions are not thread-safe
- `@Lazy` on the pool bean — avoids connecting to the Notes server at startup if the bean is not used
- `databasePath` format `server!!path` — unambiguously separates server from file path
- `NOTES_PASSWORD` env var — credentials are never hardcoded

---

## Development

### Run tests (no Notes server required)

```bash
mvn test
```

Tests use Mockito — no real Notes server needed. Integration tests are tagged `@Tag("integration")` and excluded by default.

### Build without tests

```bash
mvn package -DskipTests
```

### Dev build stub (no real NCSO.jar)

The repository includes `lib/ncso-14.0.jar` — a minimal stub of the `lotus.domino` API for compilation and unit testing. Replace it with the real `NCSO.jar` before running against a live Domino server.

---

## Configuration Reference

| Property | Default | Description |
|----------|---------|-------------|
| `notes.connection.mode` | `REMOTE` | `REMOTE` or `LOCAL` |
| `notes.connection.server` | — | Domino server hostname |
| `notes.connection.username` | — | Notes username |
| `notes.connection.password` | `${NOTES_PASSWORD}` | Password (use env var) |
| `notes.connection.pool-size` | `5` | Number of Notes sessions in pool |
| `notes.connection.timeout-ms` | `30000` | Session borrow timeout (ms) |
| `notes.tools.delete-enabled` | `true` | Enable `notesDeleteDocument` tool |
| `server.port` | `8080` | HTTP port |

---

## License

Apache 2.0
