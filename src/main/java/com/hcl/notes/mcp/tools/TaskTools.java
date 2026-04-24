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
        return Map.of("tasks", service.getTasks(
                completed != null && completed,
                limit != null ? limit : 50));
    }
}
