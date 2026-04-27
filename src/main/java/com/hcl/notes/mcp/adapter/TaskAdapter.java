package com.hcl.notes.mcp.adapter;

import com.hcl.notes.mcp.connection.NotesSessionPool;
import com.hcl.notes.mcp.model.NotesTask;
import lotus.domino.*;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

import static com.hcl.notes.mcp.adapter.NotesUtils.recycle;

@Component
public class TaskAdapter {

    private final NotesSessionPool pool;
    private final MailDatabaseLocator mailDb;

    public TaskAdapter(NotesSessionPool pool, MailDatabaseLocator mailDb) {
        this.pool   = pool;
        this.mailDb = mailDb;
    }

    public List<NotesTask> getTasks(boolean completed, int limit) {
        return pool.withSession(session -> {
            Database db = mailDb.openMailDatabase(session, "tasks");
            try {
                View tasksView = db.getView("($ToDo)");
                if (tasksView == null) return List.of();
                List<NotesTask> tasks = new ArrayList<>();
                try {
                    Document doc = tasksView.getFirstDocument();
                    while (doc != null && tasks.size() < limit) {
                        boolean isCompleted = "1".equals(doc.getItemValueString("TaskState"));
                        if (completed == isCompleted) {
                            tasks.add(toTask(doc));
                        }
                        Document next = tasksView.getNextDocument(doc);
                        recycle(doc);
                        doc = next;
                    }
                    if (doc != null) recycle(doc);
                } finally {
                    recycle(tasksView);
                }
                return tasks;
            } finally {
                recycle(db);
            }
        });
    }

    private NotesTask toTask(Document doc) throws NotesException {
        String subject     = doc.getItemValueString("Subject");
        String priorityStr = doc.getItemValueString("Priority");
        NotesTask.Priority priority = switch (priorityStr) {
            case "1" -> NotesTask.Priority.HIGH;
            case "2" -> NotesTask.Priority.MEDIUM;
            case "3" -> NotesTask.Priority.LOW;
            default  -> NotesTask.Priority.NONE;
        };
        boolean completed = "1".equals(doc.getItemValueString("TaskState"));
        LocalDate dueDate = null;
        Vector<?> dueDates = doc.getItemValue("DueDate");
        if (dueDates != null && !dueDates.isEmpty()) {
            DateTime dt = (DateTime) dueDates.firstElement();
            if (dt != null && dt.toJavaDate() != null) {
                dueDate = dt.toJavaDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            }
            recycle(dt);
        }
        return new NotesTask(doc.getUniversalID(), subject, dueDate, completed, priority);
    }
}
