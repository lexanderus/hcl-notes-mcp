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
