package com.hcl.notes.mcp.it;

import com.hcl.notes.mcp.adapter.TaskAdapter;
import com.hcl.notes.mcp.model.NotesTask;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for TaskAdapter against sandbox test-mail.nsf.
 * SandboxFixtureBuilder must be run first.
 *
 * Fixture volumes (ADR-3): 5 open tasks + 5 completed tasks.
 */
class TaskAdapterIT extends AbstractSandboxIT {

    private static TaskAdapter adapter;

    @BeforeAll
    static void setUpAdapter() {
        adapter = new TaskAdapter(pool, mailDbLocator);
    }

    @Test
    void getTasks_returnsOpenTasks() {
        List<NotesTask> tasks = adapter.getTasks(false, 20);

        // SandboxFixtureBuilder creates 5 open tasks
        assertThat(tasks).isNotEmpty();
        tasks.forEach(t -> assertThat(t.completed()).isFalse());
    }

    @Test
    void getTasks_returnsCompletedTasks() {
        List<NotesTask> tasks = adapter.getTasks(true, 20);

        // SandboxFixtureBuilder creates 5 completed tasks
        assertThat(tasks).isNotEmpty();
        tasks.forEach(t -> assertThat(t.completed()).isTrue());
    }

    @Test
    void getTasks_eachTaskHasSubject() {
        List<NotesTask> open = adapter.getTasks(false, 10);

        open.forEach(t -> assertThat(t.subject()).isNotBlank());
    }

    @Test
    void getTasks_respectsLimit() {
        List<NotesTask> limited = adapter.getTasks(false, 2);

        assertThat(limited.size()).isLessThanOrEqualTo(2);
    }

    @Test
    void getTasks_openAndCompletedAreDisjoint() {
        List<String> openUnids = adapter.getTasks(false, 20).stream()
                .map(NotesTask::unid).toList();
        List<String> completedUnids = adapter.getTasks(true, 20).stream()
                .map(NotesTask::unid).toList();

        assertThat(openUnids).doesNotContainAnyElementsOf(completedUnids);
    }
}
