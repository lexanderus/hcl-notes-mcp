package com.hcl.notes.mcp.sandbox;

import lotus.domino.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.Vector;

/**
 * Creates sandbox NSF fixtures for integration tests (ADR-3).
 *
 * Usage (from project root, after tools/init-sandbox.cmd):
 *   mvn exec:java -Dexec.mainClass=com.hcl.notes.mcp.sandbox.SandboxFixtureBuilder \
 *       -Dexec.args="tests/sandbox"
 *
 * Run with Notes JVM:
 *   "D:\Program Files\Notes\jvm\bin\java.exe"
 *       -Djava.library.path="D:\Program Files\Notes"
 *       -cp "target/hcl-notes-mcp-1.1.0.jar;D:\Program Files\Notes\jvm\lib\ext\Notes.jar"
 *       com.hcl.notes.mcp.sandbox.SandboxFixtureBuilder tests/sandbox
 *
 * IMPORTANT: Never run against production Notes databases (ADR-3).
 * IMPORTANT: sandbox.id must be password-free (Initialize("") contract).
 */
public class SandboxFixtureBuilder {

    // Volumes per ADR-3
    private static final int MAIL_MESSAGES      = 50;
    private static final int CALENDAR_EVENTS    = 20;
    private static final int TASKS_OPEN         = 5;
    private static final int TASKS_COMPLETED    = 5;
    private static final int APP_DOCUMENTS      = 200;
    private static final int HR_EMPLOYEES       = 600;

    private final String sandboxDir;
    private Session session;

    public SandboxFixtureBuilder(String sandboxDir) {
        this.sandboxDir = sandboxDir;
    }

    public static void main(String[] args) throws Exception {
        String dir = args.length > 0 ? args[0] : "tests/sandbox";
        new SandboxFixtureBuilder(dir).run();
    }

    private void run() throws Exception {
        NotesThread.sinitThread();
        try {
            // Password from NOTES_PASSWORD env var (production user.id reused for sandbox)
            String pwd = System.getenv("NOTES_PASSWORD");
            if (pwd != null && !pwd.isEmpty()) {
                session = NotesFactory.createSession((String) null, (String) null, pwd);
            } else {
                session = NotesFactory.createSession();
            }
            System.out.println("Notes session: " + session.getUserName());
            System.out.println("Sandbox dir  : " + sandboxDir);

            // Phase 1: Add ALL documents first (no view warmup yet)
            createMailFixtures();
            createCalendarFixtures();
            createTaskFixtures();
            createAppFixtures();
            createHrFixtures();

            // Phase 2: Create and warm ALL views AFTER all docs are added.
            // This ensures the view index is complete and up-to-date when the test JVM opens it.
            // If warmup happens before all docs are added, the index becomes stale and the
            // first access in the test JVM triggers an incremental rebuild (30+ second hang).
            finalWarmupAllViews();

            System.out.println("Done. All sandbox NSF fixtures created.");
        } finally {
            if (session != null) session.recycle();
            NotesThread.stermThread();
        }
    }

    private void createMailFixtures() throws NotesException {
        System.out.println("Creating mail fixtures (" + MAIL_MESSAGES + " messages)...");
        String dbPath = sandboxDir + "/Data/test-mail.nsf";
        Database db = openOrCreate(dbPath, "mail8.ntf");
        try {
            for (int i = 1; i <= MAIL_MESSAGES; i++) {
                Document doc = db.createDocument();
                try {
                    doc.replaceItemValue("Form", "Memo");
                    doc.replaceItemValue("Subject", "Test message " + i);
                    doc.replaceItemValue("From", "sandbox@test.local");
                    doc.replaceItemValue("SendTo", "testuser@test.local");
                    doc.replaceItemValue("Body", "Body of test message " + i + ". Lorem ipsum dolor sit amet.");
                    DateTime dt = session.createDateTime(daysAgo(i % 30));
                    doc.replaceItemValue("DeliveredDate", dt);
                    dt.recycle();
                    doc.save(true, false);
                } finally {
                    doc.recycle();
                }
            }
            System.out.println("  Mail: " + MAIL_MESSAGES + " messages created.");
        } finally {
            db.recycle();
        }
    }

    private void createCalendarFixtures() throws NotesException {
        System.out.println("Creating calendar fixtures (" + CALENDAR_EVENTS + " events)...");
        String dbPath = sandboxDir + "/Data/test-mail.nsf";
        Database db = openOrCreate(dbPath, "mail8.ntf");
        try {
            for (int i = 0; i < CALENDAR_EVENTS; i++) {
                // Spread ±30 days from today
                int offset = i - (CALENDAR_EVENTS / 2);
                Document doc = db.createDocument();
                try {
                    doc.replaceItemValue("Form", "Appointment");
                    doc.replaceItemValue("AppointmentType", "0");
                    doc.replaceItemValue("Subject", "Test event " + (i + 1));
                    doc.replaceItemValue("Location", "Conference Room " + (i % 5 + 1));
                    doc.replaceItemValue("Body", "Agenda for test event " + (i + 1));
                    DateTime startDt = session.createDateTime(daysFromNow(offset));
                    DateTime endDt   = session.createDateTime(daysFromNow(offset));
                    endDt.adjustHour(1, false);
                    doc.replaceItemValue("StartDate",     startDt);
                    doc.replaceItemValue("StartTime",     startDt);
                    doc.replaceItemValue("EndDate",       endDt);
                    doc.replaceItemValue("EndTime",       endDt);
                    // StartDateTime/EndDateTime required by CalendarAdapter.getEvents() view-based read
                    doc.replaceItemValue("StartDateTime", startDt);
                    doc.replaceItemValue("EndDateTime",   endDt);
                    startDt.recycle();
                    endDt.recycle();
                    doc.save(true, false);
                } finally {
                    doc.recycle();
                }
            }
            System.out.println("  Calendar: " + CALENDAR_EVENTS + " events created.");
        } finally {
            db.recycle();
        }
    }

    private void createTaskFixtures() throws NotesException {
        System.out.println("Creating task fixtures (" + (TASKS_OPEN + TASKS_COMPLETED) + " tasks)...");
        String dbPath = sandboxDir + "/Data/test-mail.nsf";
        Database db = openOrCreate(dbPath, "mail8.ntf");
        try {
            for (int i = 1; i <= TASKS_OPEN; i++) {
                Document doc = db.createDocument();
                try {
                    doc.replaceItemValue("Form", "Task");
                    doc.replaceItemValue("Subject", "Open task " + i);
                    doc.replaceItemValue("TaskState", "0"); // open (TaskAdapter reads TaskState)
                    doc.replaceItemValue("Priority", "1");
                    DateTime due = session.createDateTime(daysFromNow(i * 3));
                    doc.replaceItemValue("DueDate", due);
                    due.recycle();
                    doc.save(true, false);
                } finally {
                    doc.recycle();
                }
            }
            for (int i = 1; i <= TASKS_COMPLETED; i++) {
                Document doc = db.createDocument();
                try {
                    doc.replaceItemValue("Form", "Task");
                    doc.replaceItemValue("Subject", "Completed task " + i);
                    doc.replaceItemValue("TaskState", "1"); // completed (TaskAdapter reads TaskState)
                    doc.replaceItemValue("Priority", "1");
                    DateTime due = session.createDateTime(daysAgo(i * 2));
                    doc.replaceItemValue("DueDate", due);
                    due.recycle();
                    doc.save(true, false);
                } finally {
                    doc.recycle();
                }
            }
            System.out.println("  Tasks: " + TASKS_OPEN + " open + " + TASKS_COMPLETED + " completed.");
        } finally {
            db.recycle();
        }
    }

    private void createAppFixtures() throws NotesException {
        System.out.println("Creating app fixtures (" + APP_DOCUMENTS + " documents)...");
        String dbPath = sandboxDir + "/Data/test-app.nsf";
        Database db = openOrCreate(dbPath, "blank.ntf");
        try {
            String[] categories = {"Alpha", "Beta", "Gamma", "Delta", "Epsilon"};
            for (int i = 1; i <= APP_DOCUMENTS; i++) {
                Document doc = db.createDocument();
                try {
                    doc.replaceItemValue("Form", "Document");
                    doc.replaceItemValue("Title", "Document " + i);
                    doc.replaceItemValue("Category", categories[i % categories.length]);
                    doc.replaceItemValue("Body", "Content of document " + i + ". Category: " + categories[i % categories.length]);
                    doc.replaceItemValue("Author", "sandbox@test.local");
                    doc.save(true, false);
                } finally {
                    doc.recycle();
                }
            }
            System.out.println("  App DB: " + APP_DOCUMENTS + " documents in " + categories.length + " categories.");
        } finally {
            db.recycle();
        }
    }

    private void createHrFixtures() throws NotesException {
        System.out.println("Creating HR fixtures (" + HR_EMPLOYEES + " employees)...");
        String dbPath = sandboxDir + "/Data/test-hr.nsf";
        Database db = openOrCreate(dbPath, "blank.ntf");
        try {
            String[] departments = {"Engineering", "Marketing", "HR", "Finance", "Operations", "Sales"};
            String[] countries   = {"BY", "PL", "DE", "CZ", "UA", "LT"};
            for (int i = 1; i <= HR_EMPLOYEES; i++) {
                Document doc = db.createDocument();
                try {
                    doc.replaceItemValue("Form", "Employee");
                    doc.replaceItemValue("LastName", "Lastname" + i);
                    doc.replaceItemValue("FirstName", "Firstname" + i);
                    doc.replaceItemValue("Department", departments[i % departments.length]);
                    doc.replaceItemValue("Country", countries[i % countries.length]);
                    doc.replaceItemValue("Position", "Engineer " + (i % 5 + 1));
                    doc.replaceItemValue("EmployeeId", String.format("EMP%04d", i));
                    doc.replaceItemValue("Email", "employee" + i + "@test.local");
                    doc.save(true, false);
                } finally {
                    doc.recycle();
                }
            }
            System.out.println("  HR DB: " + HR_EMPLOYEES + " employees.");
            // Build FT index so FTSearch works in tests.
            // createFTIndex(0, recreate=true) is more reliable than updateFTIndex() for a newly
            // created database — it creates index files adjacent to the NSF unconditionally.
            db.createFTIndex(0, true);
            System.out.println("  HR DB: FT index built. isFTIndexed=" + db.isFTIndexed());
        } finally {
            db.recycle();
        }
    }

    /**
     * Creates and fully iterates all views AFTER all documents have been added to each NSF.
     *
     * WHY: Notes view indexes are built lazily. If we warm up a view and then add more documents
     * to the same NSF (e.g. calendar+task docs are added after the inbox warmup), the index
     * becomes stale. The first access in the test JVM triggers an incremental rebuild that
     * can hang for 30+ seconds — blocking the shared pool thread and cascading into timeouts.
     *
     * By deferring all view creation and warmup until after ALL documents are added, the initial
     * index build includes every document, and the test JVM sees a complete, up-to-date index.
     */
    private void finalWarmupAllViews() throws NotesException {
        System.out.println("Final view warmup pass (all docs already added)...");

        // --- test-mail.nsf: ($Inbox) and ($ToDo) ---
        String mailPath = sandboxDir + "/Data/test-mail.nsf";
        Database mailDb = openOrCreate(mailPath, "mail8.ntf");
        try {
            // ($Inbox) view
            View inbox = mailDb.getView("($Inbox)");
            if (inbox == null) {
                inbox = mailDb.createView("($Inbox)", "SELECT Form = \"Memo\"");
                System.out.println("  Created ($Inbox) view.");
            }
            // Full iteration forces a complete index build (not just first-doc)
            Document d = inbox.getFirstDocument();
            int inboxCount = 0;
            while (d != null) {
                Document next = inbox.getNextDocument(d);
                d.recycle();
                d = next;
                inboxCount++;
            }
            inbox.recycle();
            System.out.println("  ($Inbox) warmed: " + inboxCount + " docs indexed.");

            // ($Calendar) view — used by CalendarAdapter.getEvents() view-based read
            View calendar = mailDb.getView("($Calendar)");
            if (calendar == null) {
                calendar = mailDb.createView("($Calendar)", "SELECT Form = \"Appointment\"");
                System.out.println("  Created ($Calendar) view.");
            }
            Document cd = calendar.getFirstDocument();
            int calCount = 0;
            while (cd != null) {
                Document next = calendar.getNextDocument(cd);
                cd.recycle();
                cd = next;
                calCount++;
            }
            calendar.recycle();
            System.out.println("  ($Calendar) warmed: " + calCount + " docs indexed.");

            // ($ToDo) view
            View todo = mailDb.getView("($ToDo)");
            if (todo == null) {
                todo = mailDb.createView("($ToDo)", "SELECT Form = \"Task\"");
                System.out.println("  Created ($ToDo) view.");
            }
            Document td = todo.getFirstDocument();
            int todoCount = 0;
            while (td != null) {
                Document next = todo.getNextDocument(td);
                td.recycle();
                td = next;
                todoCount++;
            }
            todo.recycle();
            System.out.println("  ($ToDo) warmed: " + todoCount + " docs indexed.");
        } finally {
            mailDb.recycle();
        }

        // --- test-app.nsf: All Documents ---
        String appPath = sandboxDir + "/Data/test-app.nsf";
        Database appDb = openOrCreate(appPath, "blank.ntf");
        try {
            View allDocs = appDb.getView("All Documents");
            if (allDocs == null) {
                allDocs = appDb.createView("All Documents", "SELECT @All");
                System.out.println("  Created 'All Documents' view.");
            }
            Document d = allDocs.getFirstDocument();
            int appCount = 0;
            while (d != null) {
                Document next = allDocs.getNextDocument(d);
                d.recycle();
                d = next;
                appCount++;
            }
            allDocs.recycle();
            System.out.println("  'All Documents' warmed: " + appCount + " docs indexed.");

            // "By Category" view — first sorted column = Category field.
            // Required so getAllEntriesByKey("Alpha", true) works in DatabaseAdapterIT.
            View byCategory = appDb.getView("By Category");
            if (byCategory == null) {
                byCategory = appDb.createView("By Category", "SELECT @All");
                // Modify the default column to be the Category field, sorted ascending.
                @SuppressWarnings("unchecked")
                Vector<ViewColumn> cols = (Vector<ViewColumn>) byCategory.getColumns();
                if (!cols.isEmpty()) {
                    ViewColumn col = cols.get(0);
                    col.setTitle("Category");
                    col.setFormula("Category");
                    col.setSorted(true);
                    col.setSortDescending(false);
                }
                byCategory.recycle(); // persist design changes
                byCategory = appDb.getView("By Category"); // reopen for warmup
                System.out.println("  Created 'By Category' view.");
            }
            Document bd = byCategory.getFirstDocument();
            int byCatCount = 0;
            while (bd != null) {
                Document next = byCategory.getNextDocument(bd);
                bd.recycle();
                bd = next;
                byCatCount++;
            }
            byCategory.recycle();
            System.out.println("  'By Category' warmed: " + byCatCount + " docs indexed.");
        } finally {
            appDb.recycle();
        }

        System.out.println("Final view warmup complete.");
    }

    private Database openOrCreate(String path, String templateName) throws NotesException {
        // Try to open existing; create from template if not found
        Database db = session.getDatabase("", path, false);
        if (db != null && db.isOpen()) {
            System.out.println("  Opened existing: " + path);
            return db;
        }
        if (db != null) db.recycle();

        DbDirectory dir = session.getDbDirectory("");
        try {
            db = dir.createDatabase(path, true);
        } finally {
            dir.recycle();
        }
        System.out.println("  Created: " + path);
        return db;
    }

    private Date daysAgo(int n) {
        return Date.from(LocalDate.now().minusDays(n)
                .atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private Date daysFromNow(int n) {
        return Date.from(LocalDate.now().plusDays(n)
                .atStartOfDay(ZoneId.systemDefault()).toInstant());
    }
}
