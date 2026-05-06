package ai.agentican.framework.state;

import ai.agentican.framework.orchestration.model.WorkflowDefinition;
import ai.agentican.framework.orchestration.model.WorkflowStepAgent;
import ai.agentican.framework.store.WorkflowRunStoreMemory;
import org.junit.jupiter.api.Test;

import ai.agentican.framework.orchestration.execution.WorkflowRunStatus;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MemTaskStateStoreTest {

    private WorkflowDefinition dummyTask() {

        return WorkflowDefinition.builder("test-task", "test-task").description("desc")
                .step(new WorkflowStepAgent("step-1", "agent-1", "do it", null, false, null, null))
                .build();
    }

    @Test
    void taskStartedAndLoad() {

        var store = new WorkflowRunStoreMemory();

        store.taskStarted("task-1", "Test", dummyTask(), Map.of());

        var loaded = store.load("task-1");

        assertNotNull(loaded);
        assertEquals("task-1", loaded.taskId());
        assertEquals("Test", loaded.taskName());
    }

    @Test
    void loadMissingReturnsNull() {

        var store = new WorkflowRunStoreMemory();

        assertNull(store.load("nonexistent"));
    }

    @Test
    void listReturnsAll() {

        var store = new WorkflowRunStoreMemory();

        store.taskStarted("t1", "Task 1", dummyTask(), Map.of());
        store.taskStarted("t2", "Task 2", dummyTask(), Map.of());

        var all = store.list();

        assertEquals(2, all.size());
    }

    @Test
    void taskCompletedUpdatesStatus() {

        var store = new WorkflowRunStoreMemory();

        store.taskStarted("id1", "task1", dummyTask(), Map.of());
        store.taskCompleted("id1", WorkflowRunStatus.COMPLETED);

        var loaded = store.load("id1");

        assertEquals(WorkflowRunStatus.COMPLETED, loaded.status());
    }
}
