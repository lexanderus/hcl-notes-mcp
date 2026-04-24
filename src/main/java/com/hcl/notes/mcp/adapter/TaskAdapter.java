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
            if (tasksView == null) return List.of();
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
            if (dt != null && dt.toJavaDate() != null) {
                dueDate = dt.toJavaDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            }
        }
        return new NotesTask(doc.getUniversalID(), subject, dueDate, completed, priority);
    }

    private Database getMailDatabase(Session session) throws NotesException {
        String mailFile = session.getEnvironmentString("MailFile", true);
        String mailServer = session.getEnvironmentString("MailServer", true);
        Database db = session.getDatabase(mailServer, mailFile);
        if (db == null || !db.isOpen()) {
            throw new com.hcl.notes.mcp.connection.NotesOperationException(
                    "Cannot open mail database for tasks", null);
        }
        return db;
    }
}
