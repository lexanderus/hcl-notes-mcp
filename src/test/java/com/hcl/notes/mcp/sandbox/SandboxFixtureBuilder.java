package com.hcl.notes.mcp.sandbox;

import lotus.domino.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

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
            // Password-free ID per ADR-3
            session = NotesFactory.createSession("", "", "");
            System.out.println("Notes session: " + session.getUserName());
            System.out.println("Sandbox dir  : " + sandboxDir);

            createMailFixtures();
            createCalendarFixtures();
            createTaskFixtures();
            createAppFixtures();
            createHrFixtures();

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
            View inbox = db.getView("($Inbox)");
            if (inbox != null) inbox.recycle();

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
                    doc.replaceItemValue("StartDate", startDt);
                    doc.replaceItemValue("StartTime", startDt);
                    doc.replaceItemValue("EndDate", endDt);
                    doc.replaceItemValue("EndTime", endDt);
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
                    doc.replaceItemValue("Status", "2"); // In Progress
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
                    doc.replaceItemValue("Status", "3"); // Completed
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
            // Update FT index so FTSearch works in tests
            db.updateFTIndex(true);
            System.out.println("  HR DB: FT index updated.");
        } finally {
            db.recycle();
        }
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
