# HCL Notes MCP Server — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Java/Spring Boot MCP server that exposes 15 HCL Notes tools (databases, mail, calendar, tasks) to Claude via SSE/HTTP transport.

**Architecture:** Layered — MCP Tools → Service → Adapter → Connection Pool. The `lotus.domino.*` API is isolated in adapters; services contain only business logic and are unit-testable with Mockito mocks.

**Tech Stack:** Java 17+, Spring Boot 3.x, Spring AI MCP (SSE/HTTP), `NCSO.jar` (HCL Notes remote API), Maven, JUnit 5 + Mockito.

---

## Prerequisites (manual, before Task 1)

1. Download `NCSO.jar` from your HCL Notes/Domino installation (`<Domino-server>\jvm\lib\ext\NCSO.jar` or `<Notes-client>\jvm\lib\ext\NCSO.jar`).
2. Install it into your local Maven repository:
```bash
mvn install:install-file \
  -Dfile=NCSO.jar \
  -DgroupId=com.hcl.notes \
  -DartifactId=ncso \
  -Dversion=14.0 \
  -Dpackaging=jar
```
3. Verify Java 17+ is installed: `java -version`
4. Verify Maven is installed: `mvn -version`

---

## File Map

| File | Responsibility |
|---|---|
| `pom.xml` | Build, dependencies |
| `src/main/resources/application.yml` | Runtime config |
| `McpServerApplication.java` | Spring Boot entry point |
| `config/NotesConnectionConfig.java` | Config properties POJO |
| `config/McpServerConfig.java` | MCP tool registration |
| `connection/ConnectionMode.java` | Enum: LOCAL / REMOTE |
| `connection/NotesConnectionFactory.java` | Creates Notes sessions |
| `connection/NotesSessionPool.java` | Thread-safe session pool |
| `adapter/DatabaseAdapter.java` | `lotus.domino.Database` operations |
| `adapter/MailAdapter.java` | Notes mail operations |
| `adapter/CalendarAdapter.java` | `lotus.domino.NotesCalendar` |
| `adapter/TaskAdapter.java` | Notes task documents |
| `service/DatabaseService.java` | Database business logic |
| `service/MailService.java` | Mail business logic |
| `service/CalendarService.java` | Calendar business logic |
| `service/TaskService.java` | Task business logic |
| `tools/DatabaseTools.java` | 8 database MCP tools |
| `tools/MailTools.java` | 4 mail MCP tools |
| `tools/CalendarTools.java` | 2 calendar MCP tools |
| `tools/TaskTools.java` | 1 task MCP tool |
| `model/NotesDocument.java` | Document domain model |
| `model/MailMessage.java` | Mail domain model |
| `model/CalendarEvent.java` | Event domain model |
| `model/NotesTask.java` | Task domain model |

---

## Task 1: Project Scaffold

**Files:**
- Create: `pom.xml`
- Create: `src/main/resources/application.yml`
- Create: `src/main/java/com/hcl/notes/mcp/McpServerApplication.java`

- [ ] **Step 1: Create Maven project structure**

```bash
mkdir -p D:/Alex/Claude/hcl-notes-mcp/src/main/java/com/hcl/notes/mcp
mkdir -p D:/Alex/Claude/hcl-notes-mcp/src/main/resources
mkdir -p D:/Alex/Claude/hcl-notes-mcp/src/test/java/com/hcl/notes/mcp
cd D:/Alex/Claude/hcl-notes-mcp
```

- [ ] **Step 2: Write `pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.0</version>
    </parent>

    <groupId>com.hcl.notes</groupId>
    <artifactId>hcl-notes-mcp</artifactId>
    <version>1.0.0</version>
    <name>HCL Notes MCP Server</name>

    <properties>
        <java.version>17</java.version>
        <spring-ai.version>1.0.0</spring-ai.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.ai</groupId>
                <artifactId>spring-ai-bom</artifactId>
                <version>${spring-ai.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <!-- MCP Server (SSE/HTTP transport) -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-mcp-server-webflux-spring-boot-starter</artifactId>
        </dependency>

        <!-- HCL Notes Remote API (installed locally, see Prerequisites) -->
        <dependency>
            <groupId>com.hcl.notes</groupId>
            <artifactId>ncso</artifactId>
            <version>14.0</version>
        </dependency>

        <!-- Test -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.mockito</groupId>
            <artifactId>mockito-core</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>

    <repositories>
        <repository>
            <id>spring-milestones</id>
            <url>https://repo.spring.io/milestone</url>
        </repository>
    </repositories>
</project>
```

- [ ] **Step 3: Write `src/main/resources/application.yml`**

```yaml
notes:
  connection:
    mode: REMOTE
    server: domino.company.com
    username: user@company.com
    password: ${NOTES_PASSWORD:changeme}
    pool-size: 5
    timeout-ms: 30000
  tools:
    delete-enabled: true

server:
  port: 8080

spring:
  ai:
    mcp:
      server:
        name: hcl-notes-mcp
        version: 1.0.0
```

- [ ] **Step 4: Write `McpServerApplication.java`**

```java
package com.hcl.notes.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class McpServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(McpServerApplication.class, args);
    }
}
```

- [ ] **Step 5: Verify build compiles**

```bash
cd D:/Alex/Claude/hcl-notes-mcp
mvn compile -q
```
Expected: `BUILD SUCCESS`

- [ ] **Step 6: Commit**

```bash
git init
git add pom.xml src/
git commit -m "feat: initial project scaffold"
```

---

## Task 2: Domain Models

**Files:**
- Create: `src/main/java/com/hcl/notes/mcp/model/NotesDocument.java`
- Create: `src/main/java/com/hcl/notes/mcp/model/MailMessage.java`
- Create: `src/main/java/com/hcl/notes/mcp/model/CalendarEvent.java`
- Create: `src/main/java/com/hcl/notes/mcp/model/NotesTask.java`
- Test: `src/test/java/com/hcl/notes/mcp/model/ModelSerializationTest.java`

- [ ] **Step 1: Write failing serialization test**

```java
package com.hcl.notes.mcp.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class ModelSerializationTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void notesDocumentRoundTrip() throws Exception {
        var doc = new NotesDocument("ABC123", Instant.now(), Instant.now(),
                Map.of("Subject", "Hello"));
        String json = mapper.writeValueAsString(doc);
        var result = mapper.readValue(json, NotesDocument.class);
        assertThat(result.unid()).isEqualTo("ABC123");
        assertThat(result.fields()).containsKey("Subject");
    }

    @Test
    void mailMessageRoundTrip() throws Exception {
        var msg = new MailMessage("U1", "alice@x.com", List.of("bob@x.com"),
                "Hi", "Body text", Instant.now());
        String json = mapper.writeValueAsString(msg);
        var result = mapper.readValue(json, MailMessage.class);
        assertThat(result.subject()).isEqualTo("Hi");
    }
}
```

- [ ] **Step 2: Run test — verify it fails**

```bash
mvn test -pl . -Dtest=ModelSerializationTest -q
```
Expected: FAIL — `NotesDocument` class not found.

- [ ] **Step 3: Write `NotesDocument.java`**

```java
package com.hcl.notes.mcp.model;

import java.time.Instant;
import java.util.Map;

public record NotesDocument(
        String unid,
        Instant created,
        Instant modified,
        Map<String, Object> fields
) {}
```

- [ ] **Step 4: Write `MailMessage.java`**

```java
package com.hcl.notes.mcp.model;

import java.time.Instant;
import java.util.List;

public record MailMessage(
        String unid,
        String from,
        List<String> to,
        String subject,
        String body,
        Instant date
) {}
```

- [ ] **Step 5: Write `CalendarEvent.java`**

```java
package com.hcl.notes.mcp.model;

import java.time.Instant;
import java.util.List;

public record CalendarEvent(
        String unid,
        String title,
        Instant start,
        Instant end,
        String location,
        List<String> attendees
) {}
```

- [ ] **Step 6: Write `NotesTask.java`**

```java
package com.hcl.notes.mcp.model;

import java.time.LocalDate;

public record NotesTask(
        String unid,
        String subject,
        LocalDate dueDate,
        boolean completed,
        Priority priority
) {
    public enum Priority { HIGH, MEDIUM, LOW, NONE }
}
```

- [ ] **Step 7: Run tests — verify they pass**

```bash
mvn test -Dtest=ModelSerializationTest -q
```
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add src/
git commit -m "feat: add domain models"
```

---

## Task 3: Connection Layer

**Files:**
- Create: `src/main/java/com/hcl/notes/mcp/connection/ConnectionMode.java`
- Create: `src/main/java/com/hcl/notes/mcp/config/NotesConnectionConfig.java`
- Create: `src/main/java/com/hcl/notes/mcp/connection/NotesConnectionFactory.java`
- Create: `src/main/java/com/hcl/notes/mcp/connection/NotesSessionPool.java`
- Test: `src/test/java/com/hcl/notes/mcp/connection/NotesSessionPoolTest.java`

**Threading note:** `lotus.domino.Session` is NOT thread-safe. The pool uses `ArrayBlockingQueue` so sessions are borrowed exclusively by one thread at a time. Each pool thread calls `NotesThread.sinitThread()` once at startup and `NotesThread.stermThread()` at shutdown.

- [ ] **Step 1: Write `ConnectionMode.java`**

```java
package com.hcl.notes.mcp.connection;

public enum ConnectionMode { LOCAL, REMOTE }
```

- [ ] **Step 2: Write `NotesConnectionConfig.java`**

```java
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
    private int poolSize = 5;
    private long timeoutMs = 30_000;

    // getters and setters
    public ConnectionMode getMode() { return mode; }
    public void setMode(ConnectionMode mode) { this.mode = mode; }
    public String getServer() { return server; }
    public void setServer(String server) { this.server = server; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public int getPoolSize() { return poolSize; }
    public void setPoolSize(int poolSize) { this.poolSize = poolSize; }
    public long getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }
}
```

- [ ] **Step 3: Write failing pool test**

```java
package com.hcl.notes.mcp.connection;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import lotus.domino.Session;

class NotesSessionPoolTest {

    @Test
    void borrowAndReturnSession() throws Exception {
        Session mockSession = mock(Session.class);
        NotesSessionPool pool = new NotesSessionPool(() -> mockSession, 2, 1000);

        Session borrowed = pool.borrow();
        assertThat(borrowed).isSameAs(mockSession);

        pool.returnSession(borrowed);
        Session borrowedAgain = pool.borrow();
        assertThat(borrowedAgain).isSameAs(mockSession);
    }

    @Test
    void borrowTimesOutWhenPoolExhausted() {
        Session mockSession = mock(Session.class);
        NotesSessionPool pool = new NotesSessionPool(() -> mockSession, 1, 100);

        pool.borrow();
        assertThatThrownBy(pool::borrow)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("timed out");
    }
}
```

- [ ] **Step 4: Run test — verify it fails**

```bash
mvn test -Dtest=NotesSessionPoolTest -q
```
Expected: FAIL — `NotesSessionPool` not found.

- [ ] **Step 5: Write `NotesSessionPool.java`**

```java
package com.hcl.notes.mcp.connection;

import lotus.domino.Session;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class NotesSessionPool {

    private final ArrayBlockingQueue<Session> pool;
    private final long timeoutMs;

    public NotesSessionPool(Supplier<Session> factory, int size, long timeoutMs) {
        this.timeoutMs = timeoutMs;
        this.pool = new ArrayBlockingQueue<>(size);
        for (int i = 0; i < size; i++) {
            pool.offer(factory.get());
        }
    }

    public Session borrow() {
        try {
            Session session = pool.poll(timeoutMs, TimeUnit.MILLISECONDS);
            if (session == null) {
                throw new IllegalStateException("Session pool timed out after " + timeoutMs + "ms");
            }
            return session;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for Notes session", e);
        }
    }

    public void returnSession(Session session) {
        pool.offer(session);
    }

    public <T> T withSession(SessionCallback<T> callback) {
        Session session = borrow();
        try {
            return callback.execute(session);
        } catch (Exception e) {
            throw new NotesOperationException("Notes operation failed", e);
        } finally {
            returnSession(session);
        }
    }

    @FunctionalInterface
    public interface SessionCallback<T> {
        T execute(Session session) throws Exception;
    }
}
```

- [ ] **Step 6: Write `NotesOperationException.java`**

```java
package com.hcl.notes.mcp.connection;

public class NotesOperationException extends RuntimeException {
    public NotesOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

- [ ] **Step 7: Write `NotesConnectionFactory.java`**

```java
package com.hcl.notes.mcp.connection;

import com.hcl.notes.mcp.config.NotesConnectionConfig;
import lotus.domino.*;
import org.springframework.stereotype.Component;

@Component
public class NotesConnectionFactory {

    private final NotesConnectionConfig config;

    public NotesConnectionFactory(NotesConnectionConfig config) {
        this.config = config;
    }

    public NotesSessionPool createPool() {
        return new NotesSessionPool(this::createSession,
                config.getPoolSize(), config.getTimeoutMs());
    }

    private Session createSession() {
        try {
            NotesThread.sinitThread();
            return switch (config.getMode()) {
                case REMOTE -> NotesFactory.createSession(
                        config.getServer(), config.getUsername(), config.getPassword());
                case LOCAL -> NotesFactory.createSession();
            };
        } catch (NotesException e) {
            NotesThread.stermThread();
            throw new NotesOperationException("Failed to create Notes session", e);
        }
    }
}
```

- [ ] **Step 8: Register pool as Spring bean in `McpServerConfig.java`**

```java
package com.hcl.notes.mcp.config;

import com.hcl.notes.mcp.connection.NotesConnectionFactory;
import com.hcl.notes.mcp.connection.NotesSessionPool;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpServerConfig {

    @Bean
    public NotesSessionPool notesSessionPool(NotesConnectionFactory factory) {
        return factory.createPool();
    }
}
```

- [ ] **Step 9: Run pool tests — verify they pass**

```bash
mvn test -Dtest=NotesSessionPoolTest -q
```
Expected: PASS

- [ ] **Step 10: Commit**

```bash
git add src/
git commit -m "feat: add connection pool and factory"
```

---

## Task 4: Database Adapter

**Files:**
- Create: `src/main/java/com/hcl/notes/mcp/adapter/DatabaseAdapter.java`
- Test: `src/test/java/com/hcl/notes/mcp/adapter/DatabaseAdapterTest.java`

The `databasePath` format is `"server!!path"` (e.g. `"domino.host!!mail/jdoe.nsf"`). Local databases use `"!!mail/jdoe.nsf"`. Parse with `DatabaseAdapter.parsePath()`.

- [ ] **Step 1: Write failing adapter test**

```java
package com.hcl.notes.mcp.adapter;

import com.hcl.notes.mcp.connection.NotesSessionPool;
import com.hcl.notes.mcp.model.NotesDocument;
import lotus.domino.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Vector;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DatabaseAdapterTest {

    private NotesSessionPool pool;
    private Session session;
    private Database database;
    private DatabaseAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        session = mock(Session.class);
        database = mock(Database.class);
        pool = mock(NotesSessionPool.class);
        when(pool.withSession(any())).thenAnswer(inv ->
                inv.getArgument(0, NotesSessionPool.SessionCallback.class).execute(session));
        when(session.getDatabase("domino.host", "mail/jdoe.nsf")).thenReturn(database);
        adapter = new DatabaseAdapter(pool);
    }

    @Test
    void openDatabase_returnsTitle() throws Exception {
        when(database.isOpen()).thenReturn(true);
        when(database.getTitle()).thenReturn("John's Mail");

        var result = adapter.openDatabase("domino.host!!mail/jdoe.nsf");
        assertThat(result.title()).isEqualTo("John's Mail");
        assertThat(result.databasePath()).isEqualTo("domino.host!!mail/jdoe.nsf");
    }

    @Test
    void openDatabase_throwsWhenNotFound() throws Exception {
        when(database.isOpen()).thenReturn(false);
        assertThatThrownBy(() -> adapter.openDatabase("domino.host!!bad/path.nsf"))
                .isInstanceOf(NotesOperationException.class);
    }

    @Test
    void getDocument_returnsDocument() throws Exception {
        Document doc = mock(Document.class);
        when(database.isOpen()).thenReturn(true);
        when(database.getDocumentByUNID("UNID1")).thenReturn(doc);
        when(doc.getUniversalID()).thenReturn("UNID1");
        when(doc.getCreated()).thenReturn(mock(DateTime.class));
        when(doc.getLastModified()).thenReturn(mock(DateTime.class));
        when(doc.getItems()).thenReturn(new Vector<>());

        NotesDocument result = adapter.getDocument("domino.host!!mail/jdoe.nsf", "UNID1");
        assertThat(result.unid()).isEqualTo("UNID1");
    }
}
```

- [ ] **Step 2: Run test — verify it fails**

```bash
mvn test -Dtest=DatabaseAdapterTest -q
```
Expected: FAIL — `DatabaseAdapter` not found.

- [ ] **Step 3: Write `DatabaseAdapter.java`**

```java
package com.hcl.notes.mcp.adapter;

import com.hcl.notes.mcp.connection.NotesOperationException;
import com.hcl.notes.mcp.connection.NotesSessionPool;
import com.hcl.notes.mcp.model.NotesDocument;
import lotus.domino.*;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.*;

@Component
public class DatabaseAdapter {

    private final NotesSessionPool pool;

    public DatabaseAdapter(NotesSessionPool pool) {
        this.pool = pool;
    }

    public record OpenResult(String databasePath, String title) {}

    public OpenResult openDatabase(String databasePath) {
        String[] parts = parsePath(databasePath);
        return pool.withSession(session -> {
            Database db = session.getDatabase(parts[0], parts[1]);
            if (db == null || !db.isOpen()) {
                throw new NotesOperationException("Database not found: " + databasePath, null);
            }
            return new OpenResult(databasePath, db.getTitle());
        });
    }

    public List<Map<String, Object>> listViews(String databasePath) {
        String[] parts = parsePath(databasePath);
        return pool.withSession(session -> {
            Database db = openDb(session, parts);
            Vector<?> views = db.getViews();
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object v : views) {
                View view = (View) v;
                result.add(Map.of("name", view.getName(),
                        "entryCount", view.getEntryCount()));
            }
            return result;
        });
    }

    public List<NotesDocument> getViewEntries(String databasePath, String viewName,
                                               String filter, int limit, int offset) {
        String[] parts = parsePath(databasePath);
        return pool.withSession(session -> {
            Database db = openDb(session, parts);
            View view = db.getView(viewName);
            if (view == null) throw new NotesOperationException("View not found: " + viewName, null);
            ViewEntryCollection col = filter != null
                    ? view.getAllEntriesByKey(filter, true)
                    : view.getAllEntries();
            List<NotesDocument> docs = new ArrayList<>();
            ViewEntry entry = col.getNthEntry(offset + 1);
            int count = 0;
            while (entry != null && count < limit) {
                Document doc = entry.getDocument();
                docs.add(toModel(doc));
                entry = col.getNextEntry(entry);
                count++;
            }
            return docs;
        });
    }

    public long countViewEntries(String databasePath, String viewName, String filter) {
        String[] parts = parsePath(databasePath);
        return pool.withSession(session -> {
            Database db = openDb(session, parts);
            View view = db.getView(viewName);
            if (view == null) throw new NotesOperationException("View not found: " + viewName, null);
            return filter != null
                    ? (long) view.getAllEntriesByKey(filter, true).getCount()
                    : (long) view.getAllEntries().getCount();
        });
    }

    public NotesDocument getDocument(String databasePath, String unid) {
        String[] parts = parsePath(databasePath);
        return pool.withSession(session -> {
            Database db = openDb(session, parts);
            Document doc = db.getDocumentByUNID(unid);
            if (doc == null) return null;
            return toModel(doc);
        });
    }

    public List<NotesDocument> searchDocuments(String databasePath, String query,
                                                int limit, int offset) {
        String[] parts = parsePath(databasePath);
        return pool.withSession(session -> {
            Database db = openDb(session, parts);
            DocumentCollection col = db.search(query, null, limit + offset);
            List<NotesDocument> docs = new ArrayList<>();
            Document doc = col.getNthDocument(offset + 1);
            int count = 0;
            while (doc != null && count < limit) {
                docs.add(toModel(doc));
                doc = col.getNextDocument(doc);
                count++;
            }
            return docs;
        });
    }

    public int countSearchResults(String databasePath, String query) {
        String[] parts = parsePath(databasePath);
        return pool.withSession(session -> {
            Database db = openDb(session, parts);
            return db.search(query, null, 0).getCount();
        });
    }

    public String createDocument(String databasePath, Map<String, Object> fields) {
        String[] parts = parsePath(databasePath);
        return pool.withSession(session -> {
            Database db = openDb(session, parts);
            Document doc = db.createDocument();
            setFields(doc, fields);
            doc.save();
            return doc.getUniversalID();
        });
    }

    public String updateDocument(String databasePath, String unid, Map<String, Object> fields) {
        String[] parts = parsePath(databasePath);
        return pool.withSession(session -> {
            Database db = openDb(session, parts);
            Document doc = db.getDocumentByUNID(unid);
            if (doc == null) throw new NotesOperationException("Document not found: " + unid, null);
            setFields(doc, fields);
            doc.save();
            return doc.getUniversalID();
        });
    }

    public boolean deleteDocument(String databasePath, String unid) {
        String[] parts = parsePath(databasePath);
        return pool.withSession(session -> {
            Database db = openDb(session, parts);
            Document doc = db.getDocumentByUNID(unid);
            if (doc == null) return false;
            doc.remove(true);
            return true;
        });
    }

    // --- helpers ---

    public static String[] parsePath(String databasePath) {
        if (!databasePath.contains("!!")) {
            throw new IllegalArgumentException(
                    "Invalid databasePath format. Expected 'server!!path', got: " + databasePath);
        }
        return databasePath.split("!!", 2);
    }

    private Database openDb(Session session, String[] parts) throws NotesException {
        Database db = session.getDatabase(parts[0], parts[1]);
        if (db == null || !db.isOpen()) {
            throw new NotesOperationException("Database not accessible: " + parts[0] + "!!" + parts[1], null);
        }
        return db;
    }

    private void setFields(Document doc, Map<String, Object> fields) throws NotesException {
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            doc.replaceItemValue(entry.getKey(), entry.getValue());
        }
    }

    @SuppressWarnings("unchecked")
    private NotesDocument toModel(Document doc) throws NotesException {
        Map<String, Object> fields = new LinkedHashMap<>();
        Vector<Item> items = doc.getItems();
        for (Item item : items) {
            fields.put(item.getName(), item.getValues().size() == 1
                    ? item.getValues().get(0) : item.getValues());
        }
        DateTime created = doc.getCreated();
        DateTime modified = doc.getLastModified();
        return new NotesDocument(
                doc.getUniversalID(),
                created != null ? Instant.ofEpochMilli(created.toJavaDate().getTime()) : null,
                modified != null ? Instant.ofEpochMilli(modified.toJavaDate().getTime()) : null,
                fields
        );
    }
}
```

- [ ] **Step 4: Run tests — verify they pass**

```bash
mvn test -Dtest=DatabaseAdapterTest -q
```
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/
git commit -m "feat: add DatabaseAdapter with full CRUD and search"
```

---

## Task 5: Database Service + MCP Tools

**Files:**
- Create: `src/main/java/com/hcl/notes/mcp/service/DatabaseService.java`
- Create: `src/main/java/com/hcl/notes/mcp/tools/DatabaseTools.java`
- Test: `src/test/java/com/hcl/notes/mcp/service/DatabaseServiceTest.java`

- [ ] **Step 1: Write failing service test**

```java
package com.hcl.notes.mcp.service;

import com.hcl.notes.mcp.adapter.DatabaseAdapter;
import com.hcl.notes.mcp.model.NotesDocument;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DatabaseServiceTest {

    private final DatabaseAdapter adapter = mock(DatabaseAdapter.class);
    private final DatabaseService service = new DatabaseService(adapter, true);

    @Test
    void getViewEntries_returnsPagedResult() {
        var doc = new NotesDocument("U1", Instant.now(), Instant.now(), Map.of("Name", "Test"));
        when(adapter.getViewEntries("srv!!db.nsf", "All", null, 50, 0)).thenReturn(List.of(doc));
        when(adapter.countViewEntries("srv!!db.nsf", "All", null)).thenReturn(1L);

        var result = service.getViewEntries("srv!!db.nsf", "All", null, 50, 0);
        assertThat(result.entries()).hasSize(1);
        assertThat(result.total()).isEqualTo(1);
    }

    @Test
    void deleteDocument_throwsWhenDeleteDisabled() {
        var svc = new DatabaseService(adapter, false);
        assertThatThrownBy(() -> svc.deleteDocument("srv!!db.nsf", "U1"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
```

- [ ] **Step 2: Run test — verify it fails**

```bash
mvn test -Dtest=DatabaseServiceTest -q
```
Expected: FAIL

- [ ] **Step 3: Write `DatabaseService.java`**

```java
package com.hcl.notes.mcp.service;

import com.hcl.notes.mcp.adapter.DatabaseAdapter;
import com.hcl.notes.mcp.model.NotesDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

@Service
public class DatabaseService {

    private final DatabaseAdapter adapter;
    private final boolean deleteEnabled;

    public DatabaseService(DatabaseAdapter adapter,
                           @Value("${notes.tools.delete-enabled:true}") boolean deleteEnabled) {
        this.adapter = adapter;
        this.deleteEnabled = deleteEnabled;
    }

    public record PagedResult(List<NotesDocument> entries, long total) {}

    public DatabaseAdapter.OpenResult openDatabase(String server, String path) {
        String databasePath = server + "!!" + path;
        return adapter.openDatabase(databasePath);
    }

    public List<Map<String, Object>> listViews(String databasePath) {
        return adapter.listViews(databasePath);
    }

    public PagedResult getViewEntries(String databasePath, String view,
                                      String filter, int limit, int offset) {
        List<NotesDocument> entries = adapter.getViewEntries(databasePath, view, filter, limit, offset);
        long total = adapter.countViewEntries(databasePath, view, filter);
        return new PagedResult(entries, total);
    }

    public NotesDocument getDocument(String databasePath, String unid) {
        return adapter.getDocument(databasePath, unid);
    }

    public PagedResult searchDocuments(String databasePath, String query, int limit, int offset) {
        List<NotesDocument> entries = adapter.searchDocuments(databasePath, query, limit, offset);
        long total = adapter.countSearchResults(databasePath, query);
        return new PagedResult(entries, total);
    }

    public String createDocument(String databasePath, Map<String, Object> fields) {
        return adapter.createDocument(databasePath, fields);
    }

    public String updateDocument(String databasePath, String unid, Map<String, Object> fields) {
        return adapter.updateDocument(databasePath, unid, fields);
    }

    public boolean deleteDocument(String databasePath, String unid) {
        if (!deleteEnabled) {
            throw new UnsupportedOperationException("Delete is disabled via notes.tools.delete-enabled=false");
        }
        return adapter.deleteDocument(databasePath, unid);
    }
}
```

- [ ] **Step 4: Run tests — verify they pass**

```bash
mvn test -Dtest=DatabaseServiceTest -q
```
Expected: PASS

- [ ] **Step 5: Write `DatabaseTools.java`**

```java
package com.hcl.notes.mcp.tools;

import com.hcl.notes.mcp.service.DatabaseService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class DatabaseTools {

    private final DatabaseService service;

    public DatabaseTools(DatabaseService service) {
        this.service = service;
    }

    @Tool(description = "Open and validate a Notes database. Returns databasePath for use in subsequent calls.")
    public Map<String, Object> notesOpenDatabase(
            @ToolParam(description = "Domino server hostname, or empty string for local") String server,
            @ToolParam(description = "Database path on server, e.g. mail/jdoe.nsf") String path) {
        var result = service.openDatabase(server, path);
        return Map.of("databasePath", result.databasePath(), "title", result.title());
    }

    @Tool(description = "List all views in a Notes database.")
    public Map<String, Object> notesListViews(
            @ToolParam(description = "databasePath in format server!!path") String databasePath) {
        return Map.of("views", service.listViews(databasePath));
    }

    @Tool(description = "Get paginated documents from a Notes view.")
    public Map<String, Object> notesGetViewEntries(
            @ToolParam(description = "databasePath in format server!!path") String databasePath,
            @ToolParam(description = "View name") String view,
            @ToolParam(description = "Optional key filter", required = false) String filter,
            @ToolParam(description = "Max results (default 50)", required = false) Integer limit,
            @ToolParam(description = "Offset for pagination (default 0)", required = false) Integer offset) {
        int lim = limit != null ? limit : 50;
        int off = offset != null ? offset : 0;
        var result = service.getViewEntries(databasePath, view, filter, lim, off);
        return Map.of("entries", result.entries(), "total", result.total());
    }

    @Tool(description = "Get a specific Notes document by UNID.")
    public Object notesGetDocument(
            @ToolParam(description = "databasePath in format server!!path") String databasePath,
            @ToolParam(description = "Document UNID") String unid) {
        var doc = service.getDocument(databasePath, unid);
        return doc != null ? doc : Map.of("error", "Document not found: " + unid);
    }

    @Tool(description = "Full-text search in a Notes database.")
    public Map<String, Object> notesSearchDocuments(
            @ToolParam(description = "databasePath in format server!!path") String databasePath,
            @ToolParam(description = "Search query") String query,
            @ToolParam(description = "Max results (default 50)", required = false) Integer limit,
            @ToolParam(description = "Offset (default 0)", required = false) Integer offset) {
        int lim = limit != null ? limit : 50;
        int off = offset != null ? offset : 0;
        var result = service.searchDocuments(databasePath, query, lim, off);
        return Map.of("entries", result.entries(), "total", result.total());
    }

    @Tool(description = "Create a new document in a Notes database.")
    public Map<String, Object> notesCreateDocument(
            @ToolParam(description = "databasePath in format server!!path") String databasePath,
            @ToolParam(description = "Fields as key-value object") Map<String, Object> fields) {
        String unid = service.createDocument(databasePath, fields);
        return Map.of("unid", unid);
    }

    @Tool(description = "Update fields of an existing Notes document.")
    public Map<String, Object> notesUpdateDocument(
            @ToolParam(description = "databasePath in format server!!path") String databasePath,
            @ToolParam(description = "Document UNID") String unid,
            @ToolParam(description = "Fields to update as key-value object") Map<String, Object> fields) {
        String result = service.updateDocument(databasePath, unid, fields);
        return Map.of("unid", result);
    }

    @Tool(description = "Delete a Notes document by UNID.")
    public Map<String, Object> notesDeleteDocument(
            @ToolParam(description = "databasePath in format server!!path") String databasePath,
            @ToolParam(description = "Document UNID to delete") String unid) {
        boolean success = service.deleteDocument(databasePath, unid);
        return Map.of("success", success);
    }
}
```

- [ ] **Step 6: Register tools in `McpServerConfig.java`**

Add to the existing `McpServerConfig`:
```java
@Bean
public ToolCallbackProvider notesToolCallbacks(
        DatabaseTools dbTools,
        MailTools mailTools,
        CalendarTools calendarTools,
        TaskTools taskTools) {
    return MethodToolCallbackProvider.builder()
            .toolObjects(dbTools, mailTools, calendarTools, taskTools)
            .build();
}
```
(Full wiring happens after Tasks 7 and 8 are complete — for now add only `dbTools`.)

- [ ] **Step 7: Verify build**

```bash
mvn compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 8: Commit**

```bash
git add src/
git commit -m "feat: add DatabaseService and DatabaseTools (8 MCP tools)"
```

---

## Task 6: Mail Layer

**Files:**
- Create: `src/main/java/com/hcl/notes/mcp/adapter/MailAdapter.java`
- Create: `src/main/java/com/hcl/notes/mcp/service/MailService.java`
- Create: `src/main/java/com/hcl/notes/mcp/tools/MailTools.java`
- Test: `src/test/java/com/hcl/notes/mcp/service/MailServiceTest.java`

- [ ] **Step 1: Write failing mail service test**

```java
package com.hcl.notes.mcp.service;

import com.hcl.notes.mcp.adapter.MailAdapter;
import com.hcl.notes.mcp.model.MailMessage;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
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
}
```

- [ ] **Step 2: Run test — verify it fails**

```bash
mvn test -Dtest=MailServiceTest -q
```
Expected: FAIL

- [ ] **Step 3: Write `MailAdapter.java`**

```java
package com.hcl.notes.mcp.adapter;

import com.hcl.notes.mcp.connection.NotesOperationException;
import com.hcl.notes.mcp.connection.NotesSessionPool;
import com.hcl.notes.mcp.model.MailMessage;
import lotus.domino.*;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.*;

@Component
public class MailAdapter {

    private final NotesSessionPool pool;

    public MailAdapter(NotesSessionPool pool) {
        this.pool = pool;
    }

    public List<MailMessage> getInboxMessages(int count) {
        return pool.withSession(session -> {
            Database mailDb = getMailDatabase(session);
            View inbox = mailDb.getView("($Inbox)");
            List<MailMessage> messages = new ArrayList<>();
            Document doc = inbox.getLastDocument();
            int collected = 0;
            while (doc != null && collected < count) {
                messages.add(toMailMessage(doc));
                doc = inbox.getPrevDocument(doc);
                collected++;
            }
            return messages;
        });
    }

    public boolean sendMail(List<String> to, String subject, String body, List<String> cc) {
        return pool.withSession(session -> {
            Database mailDb = getMailDatabase(session);
            Document doc = mailDb.createDocument();
            doc.replaceItemValue("Form", "Memo");
            doc.replaceItemValue("SendTo", new Vector<>(to));
            doc.replaceItemValue("Subject", subject);
            doc.replaceItemValue("Body", body);
            if (cc != null && !cc.isEmpty()) {
                doc.replaceItemValue("CopyTo", new Vector<>(cc));
            }
            doc.send(false);
            return true;
        });
    }

    public List<MailMessage> searchMail(String query, String folder, int limit) {
        return pool.withSession(session -> {
            Database mailDb = getMailDatabase(session);
            String viewName = folder != null ? folder : "($Inbox)";
            DocumentCollection col = mailDb.search(query, null, limit);
            List<MailMessage> messages = new ArrayList<>();
            Document doc = col.getFirstDocument();
            while (doc != null && messages.size() < limit) {
                messages.add(toMailMessage(doc));
                doc = col.getNextDocument(doc);
            }
            return messages;
        });
    }

    public boolean moveToFolder(String unid, String folder) {
        return pool.withSession(session -> {
            Database mailDb = getMailDatabase(session);
            Document doc = mailDb.getDocumentByUNID(unid);
            if (doc == null) return false;
            doc.putInFolder(folder);
            doc.removeFromFolder("($Inbox)");
            return true;
        });
    }

    private Database getMailDatabase(Session session) throws NotesException {
        String mailFile = session.getEnvironmentString("MailFile", true);
        String mailServer = session.getEnvironmentString("MailServer", true);
        Database db = session.getDatabase(mailServer, mailFile);
        if (db == null || !db.isOpen()) {
            throw new NotesOperationException("Cannot open mail database", null);
        }
        return db;
    }

    private MailMessage toMailMessage(Document doc) throws NotesException {
        DateTime date = doc.getCreated();
        return new MailMessage(
                doc.getUniversalID(),
                doc.getItemValueString("From"),
                getSendTo(doc),
                doc.getItemValueString("Subject"),
                doc.getItemValueString("Body"),
                date != null ? Instant.ofEpochMilli(date.toJavaDate().getTime()) : null
        );
    }

    @SuppressWarnings("unchecked")
    private List<String> getSendTo(Document doc) throws NotesException {
        Vector<String> v = doc.getItemValue("SendTo");
        return v != null ? new ArrayList<>(v) : List.of();
    }
}
```

- [ ] **Step 4: Write `MailService.java`**

```java
package com.hcl.notes.mcp.service;

import com.hcl.notes.mcp.adapter.MailAdapter;
import com.hcl.notes.mcp.model.MailMessage;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class MailService {

    private final MailAdapter adapter;

    public MailService(MailAdapter adapter) {
        this.adapter = adapter;
    }

    public List<MailMessage> getInbox(int count) {
        return adapter.getInboxMessages(count);
    }

    public boolean sendMail(List<String> to, String subject, String body, List<String> cc) {
        return adapter.sendMail(to, subject, body, cc);
    }

    public List<MailMessage> searchMail(String query, String folder, int limit) {
        return adapter.searchMail(query, folder, limit);
    }

    public boolean moveToFolder(String unid, String folder) {
        return adapter.moveToFolder(unid, folder);
    }
}
```

- [ ] **Step 5: Write `MailTools.java`**

```java
package com.hcl.notes.mcp.tools;

import com.hcl.notes.mcp.service.MailService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;

@Component
public class MailTools {

    private final MailService service;

    public MailTools(MailService service) {
        this.service = service;
    }

    @Tool(description = "Get the N most recent messages from the Notes inbox.")
    public Map<String, Object> notesGetInbox(
            @ToolParam(description = "Number of messages to return (default 20)", required = false) Integer count) {
        return Map.of("messages", service.getInbox(count != null ? count : 20));
    }

    @Tool(description = "Send an email via HCL Notes.")
    public Map<String, Object> notesSendMail(
            @ToolParam(description = "List of recipient email addresses") List<String> to,
            @ToolParam(description = "Email subject") String subject,
            @ToolParam(description = "Email body (plain text)") String body,
            @ToolParam(description = "CC recipients", required = false) List<String> cc) {
        boolean success = service.sendMail(to, subject, body, cc);
        return Map.of("success", success);
    }

    @Tool(description = "Search mail messages in Notes.")
    public Map<String, Object> notesSearchMail(
            @ToolParam(description = "Search query") String query,
            @ToolParam(description = "Folder to search, default ($Inbox)", required = false) String folder,
            @ToolParam(description = "Max results (default 50)", required = false) Integer limit) {
        return Map.of("messages", service.searchMail(query, folder, limit != null ? limit : 50));
    }

    @Tool(description = "Move a mail message to a specified folder.")
    public Map<String, Object> notesMoveToFolder(
            @ToolParam(description = "Document UNID of the mail message") String unid,
            @ToolParam(description = "Target folder name") String folder) {
        return Map.of("success", service.moveToFolder(unid, folder));
    }
}
```

- [ ] **Step 6: Run tests — verify they pass**

```bash
mvn test -Dtest=MailServiceTest -q
```
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add src/
git commit -m "feat: add mail adapter, service, and MCP tools"
```

---

## Task 7: Calendar & Task Layer

**Files:**
- Create: `src/main/java/com/hcl/notes/mcp/adapter/CalendarAdapter.java`
- Create: `src/main/java/com/hcl/notes/mcp/adapter/TaskAdapter.java`
- Create: `src/main/java/com/hcl/notes/mcp/service/CalendarService.java`
- Create: `src/main/java/com/hcl/notes/mcp/service/TaskService.java`
- Create: `src/main/java/com/hcl/notes/mcp/tools/CalendarTools.java`
- Create: `src/main/java/com/hcl/notes/mcp/tools/TaskTools.java`
- Test: `src/test/java/com/hcl/notes/mcp/service/CalendarServiceTest.java`

- [ ] **Step 1: Write failing calendar service test**

```java
package com.hcl.notes.mcp.service;

import com.hcl.notes.mcp.adapter.CalendarAdapter;
import com.hcl.notes.mcp.model.CalendarEvent;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CalendarServiceTest {

    private final CalendarAdapter adapter = mock(CalendarAdapter.class);
    private final CalendarService service = new CalendarService(adapter);

    @Test
    void getEvents_delegatesToAdapter() {
        Instant start = Instant.parse("2026-04-01T00:00:00Z");
        Instant end = Instant.parse("2026-04-30T23:59:59Z");
        var event = new CalendarEvent("U1", "Team Meeting", start, end, "Room 1", List.of());
        when(adapter.getEvents(start, end)).thenReturn(List.of(event));

        var result = service.getEvents(start, end);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("Team Meeting");
    }
}
```

- [ ] **Step 2: Run test — verify it fails**

```bash
mvn test -Dtest=CalendarServiceTest -q
```
Expected: FAIL

- [ ] **Step 3: Write `CalendarAdapter.java`**

```java
package com.hcl.notes.mcp.adapter;

import com.hcl.notes.mcp.connection.NotesOperationException;
import com.hcl.notes.mcp.connection.NotesSessionPool;
import com.hcl.notes.mcp.model.CalendarEvent;
import lotus.domino.*;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.*;

@Component
public class CalendarAdapter {

    private final NotesSessionPool pool;

    public CalendarAdapter(NotesSessionPool pool) {
        this.pool = pool;
    }

    public List<CalendarEvent> getEvents(Instant startDate, Instant endDate) {
        return pool.withSession(session -> {
            Database mailDb = getMailDatabase(session);
            NotesCalendar cal = session.getCalendar(mailDb);
            DateTime start = session.createDateTime(Date.from(startDate));
            DateTime end = session.createDateTime(Date.from(endDate));
            Vector<?> entries = cal.getEntries(start, end);
            List<CalendarEvent> events = new ArrayList<>();
            for (Object e : entries) {
                NotesCalendarEntry entry = (NotesCalendarEntry) e;
                events.add(toCalendarEvent(entry));
            }
            return events;
        });
    }

    public String createEvent(String title, Instant start, Instant end,
                               String location, List<String> attendees) {
        return pool.withSession(session -> {
            Database mailDb = getMailDatabase(session);
            Document doc = mailDb.createDocument();
            doc.replaceItemValue("Form", "Appointment");
            doc.replaceItemValue("Subject", title);
            doc.replaceItemValue("StartDateTime",
                    session.createDateTime(Date.from(start)));
            doc.replaceItemValue("EndDateTime",
                    session.createDateTime(Date.from(end)));
            if (location != null) doc.replaceItemValue("Location", location);
            if (attendees != null && !attendees.isEmpty()) {
                doc.replaceItemValue("RequiredAttendees", new Vector<>(attendees));
            }
            doc.replaceItemValue("AppointmentType", "0");
            doc.save();
            return doc.getUniversalID();
        });
    }

    private CalendarEvent toCalendarEvent(NotesCalendarEntry entry) throws NotesException {
        Document doc = entry.getAsDocument();
        String title = doc.getItemValueString("Subject");
        String location = doc.getItemValueString("Location");
        DateTime startDt = (DateTime) doc.getItemValue("StartDateTime").firstElement();
        DateTime endDt = (DateTime) doc.getItemValue("EndDateTime").firstElement();
        return new CalendarEvent(
                doc.getUniversalID(), title,
                Instant.ofEpochMilli(startDt.toJavaDate().getTime()),
                Instant.ofEpochMilli(endDt.toJavaDate().getTime()),
                location.isEmpty() ? null : location,
                List.of()
        );
    }

    private Database getMailDatabase(Session session) throws NotesException {
        String mailFile = session.getEnvironmentString("MailFile", true);
        String mailServer = session.getEnvironmentString("MailServer", true);
        Database db = session.getDatabase(mailServer, mailFile);
        if (db == null || !db.isOpen()) {
            throw new NotesOperationException("Cannot open mail database for calendar", null);
        }
        return db;
    }
}
```

- [ ] **Step 4: Write `TaskAdapter.java`**

```java
package com.hcl.notes.mcp.adapter;

import com.hcl.notes.mcp.connection.NotesSessionPool;
import com.hcl.notes.mcp.model.NotesTask;
import lotus.domino.*;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Component
public class TaskAdapter {

    private final NotesSessionPool pool;

    public TaskAdapter(NotesSessionPool pool) {
        this.pool = pool;
    }

    public List<NotesTask> getTasks(boolean completed, int limit) {
        return pool.withSession(session -> {
            Database mailDb = getMailDatabase(session);
            View tasksView = mailDb.getView("($ToDo)");
            List<NotesTask> tasks = new ArrayList<>();
            Document doc = tasksView.getFirstDocument();
            while (doc != null && tasks.size() < limit) {
                boolean isCompleted = "1".equals(doc.getItemValueString("TaskState"));
                if (completed == isCompleted) {
                    tasks.add(toTask(doc));
                }
                doc = tasksView.getNextDocument(doc);
            }
            return tasks;
        });
    }

    private NotesTask toTask(Document doc) throws NotesException {
        String subject = doc.getItemValueString("Subject");
        String priorityStr = doc.getItemValueString("Priority");
        NotesTask.Priority priority = switch (priorityStr) {
            case "1" -> NotesTask.Priority.HIGH;
            case "2" -> NotesTask.Priority.MEDIUM;
            case "3" -> NotesTask.Priority.LOW;
            default -> NotesTask.Priority.NONE;
        };
        boolean completed = "1".equals(doc.getItemValueString("TaskState"));
        LocalDate dueDate = null;
        Vector<?> dueDates = doc.getItemValue("DueDate");
        if (dueDates != null && !dueDates.isEmpty()) {
            DateTime dt = (DateTime) dueDates.firstElement();
            dueDate = dt.toJavaDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        return new NotesTask(doc.getUniversalID(), subject, dueDate, completed, priority);
    }

    private Database getMailDatabase(Session session) throws NotesException {
        String mailFile = session.getEnvironmentString("MailFile", true);
        String mailServer = session.getEnvironmentString("MailServer", true);
        return session.getDatabase(mailServer, mailFile);
    }
}
```

- [ ] **Step 5: Write `CalendarService.java`**

```java
package com.hcl.notes.mcp.service;

import com.hcl.notes.mcp.adapter.CalendarAdapter;
import com.hcl.notes.mcp.model.CalendarEvent;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.List;

@Service
public class CalendarService {

    private final CalendarAdapter adapter;

    public CalendarService(CalendarAdapter adapter) {
        this.adapter = adapter;
    }

    public List<CalendarEvent> getEvents(Instant start, Instant end) {
        return adapter.getEvents(start, end);
    }

    public String createEvent(String title, Instant start, Instant end,
                               String location, List<String> attendees) {
        return adapter.createEvent(title, start, end, location, attendees);
    }
}
```

- [ ] **Step 6: Write `TaskService.java`**

```java
package com.hcl.notes.mcp.service;

import com.hcl.notes.mcp.adapter.TaskAdapter;
import com.hcl.notes.mcp.model.NotesTask;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class TaskService {

    private final TaskAdapter adapter;

    public TaskService(TaskAdapter adapter) {
        this.adapter = adapter;
    }

    public List<NotesTask> getTasks(boolean completed, int limit) {
        return adapter.getTasks(completed, limit);
    }
}
```

- [ ] **Step 7: Write `CalendarTools.java`**

```java
package com.hcl.notes.mcp.tools;

import com.hcl.notes.mcp.service.CalendarService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
public class CalendarTools {

    private final CalendarService service;

    public CalendarTools(CalendarService service) {
        this.service = service;
    }

    @Tool(description = "Get Notes calendar events in a date range.")
    public Map<String, Object> notesGetCalendarEvents(
            @ToolParam(description = "Start date in ISO-8601 format, e.g. 2026-04-01T00:00:00Z") String startDate,
            @ToolParam(description = "End date in ISO-8601 format, e.g. 2026-04-30T23:59:59Z") String endDate) {
        var events = service.getEvents(Instant.parse(startDate), Instant.parse(endDate));
        return Map.of("events", events);
    }

    @Tool(description = "Create a new calendar event in HCL Notes.")
    public Map<String, Object> notesCreateEvent(
            @ToolParam(description = "Event title") String title,
            @ToolParam(description = "Start datetime in ISO-8601") String start,
            @ToolParam(description = "End datetime in ISO-8601") String end,
            @ToolParam(description = "Location", required = false) String location,
            @ToolParam(description = "Attendee email addresses", required = false) List<String> attendees) {
        String unid = service.createEvent(title, Instant.parse(start),
                Instant.parse(end), location, attendees);
        return Map.of("unid", unid);
    }
}
```

- [ ] **Step 8: Write `TaskTools.java`**

```java
package com.hcl.notes.mcp.tools;

import com.hcl.notes.mcp.service.TaskService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class TaskTools {

    private final TaskService service;

    public TaskTools(TaskService service) {
        this.service = service;
    }

    @Tool(description = "Get Notes tasks. Filter by completion status.")
    public Map<String, Object> notesGetTasks(
            @ToolParam(description = "Include completed tasks (default false)", required = false) Boolean completed,
            @ToolParam(description = "Max tasks to return (default 50)", required = false) Integer limit) {
        var tasks = service.getTasks(
                completed != null && completed,
                limit != null ? limit : 50);
        return Map.of("tasks", tasks);
    }
}
```

- [ ] **Step 9: Run tests — verify they pass**

```bash
mvn test -Dtest=CalendarServiceTest -q
```
Expected: PASS

- [ ] **Step 10: Commit**

```bash
git add src/
git commit -m "feat: add calendar and task layers with MCP tools"
```

---

## Task 8: Final Wiring & Smoke Test

**Files:**
- Modify: `src/main/java/com/hcl/notes/mcp/config/McpServerConfig.java` (wire all tools)
- Test: `src/test/java/com/hcl/notes/mcp/McpContextTest.java`

- [ ] **Step 1: Complete `McpServerConfig.java` wiring**

Replace the partial tool registration with full wiring:

```java
package com.hcl.notes.mcp.config;

import com.hcl.notes.mcp.connection.NotesConnectionFactory;
import com.hcl.notes.mcp.connection.NotesSessionPool;
import com.hcl.notes.mcp.tools.*;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpServerConfig {

    @Bean
    public NotesSessionPool notesSessionPool(NotesConnectionFactory factory) {
        return factory.createPool();
    }

    @Bean
    public MethodToolCallbackProvider notesToolCallbacks(
            DatabaseTools dbTools,
            MailTools mailTools,
            CalendarTools calendarTools,
            TaskTools taskTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(dbTools, mailTools, calendarTools, taskTools)
                .build();
    }
}
```

- [ ] **Step 2: Write Spring context load test**

```java
package com.hcl.notes.mcp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "notes.connection.mode=REMOTE",
    "notes.connection.server=mock-server",
    "notes.connection.username=test",
    "NOTES_PASSWORD=test"
})
class McpContextTest {

    @Test
    void contextLoads() {
        // verifies Spring wiring succeeds without a real Notes server
    }
}
```

**Note:** This test will fail if `NotesSessionPool` eagerly tries to connect. Add `@MockBean NotesSessionPool` or configure lazy initialization for the pool in tests. Alternatively, annotate `NotesConnectionFactory.createPool()` with `@Lazy`.

- [ ] **Step 3: Mark pool bean as lazy**

In `McpServerConfig.java`:
```java
@Bean
@Lazy
public NotesSessionPool notesSessionPool(NotesConnectionFactory factory) {
    return factory.createPool();
}
```

- [ ] **Step 4: Run context test**

```bash
mvn test -Dtest=McpContextTest -q
```
Expected: PASS

- [ ] **Step 5: Run full test suite**

```bash
mvn test -q
```
Expected: All tests PASS (unit tests; integration tests skipped by default)

- [ ] **Step 6: Build the fat JAR**

```bash
mvn package -DskipTests -q
ls target/hcl-notes-mcp-1.0.0.jar
```
Expected: JAR file present.

- [ ] **Step 7: Verify startup (against real Domino server if available)**

```bash
NOTES_PASSWORD=yourpassword java -jar target/hcl-notes-mcp-1.0.0.jar \
  --notes.connection.server=your-domino-server \
  --notes.connection.username=your@user.com
```
Expected: Server starts, logs `Started McpServerApplication`, listening on `localhost:8080`.

- [ ] **Step 8: Final commit + tag**

```bash
git add src/
git commit -m "feat: wire all MCP tools, add context test"
git tag v1.0.0
```

---

## Integration Test Reference (optional, requires live Domino)

Run with: `mvn test -Dgroups=integration -Dnotes.server=... -Dnotes.username=... -DNOTES_PASSWORD=...`

```java
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DatabaseToolsIntegrationTest {
    // Call notesOpenDatabase, notesListViews, notesGetDocument against real server
    // Verify response structure matches spec shapes
}
```

---

## Claude Desktop Setup

After the server is running, add to `%APPDATA%\Claude\claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "hcl-notes": {
      "url": "http://localhost:8080/sse"
    }
  }
}
```

Restart Claude Desktop. You should see `hcl-notes` in the tools panel with all 15 tools available.
