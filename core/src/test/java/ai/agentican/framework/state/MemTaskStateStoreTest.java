package ai.agentican.framework.state;

import ai.agentican.framework.orchestration.model.Plan;
import ai.agentican.framework.orchestration.model.PlanStepAgent;
import org.junit.jupiter.api.Test;

import ai.agentican.framework.orchestration.execution.TaskStatus;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MemTaskStateStoreTest {

    private Plan dummyTask() {

        return Plan.builder("test-task").description("desc")
                .step(new PlanStepAgent("step-1", "agent-1", "do it", null, false, null, null))
                .build();
    }

    @Test
    void taskStartedAndLoad() {

        var store = new MemTaskStateStore();

        store.taskStarted("task-1", "Test", dummyTask(), Map.of());

        var loaded = store.load("task-1");

        assertNotNull(loaded);
        assertEquals("task-1", loaded.taskId());
        assertEquals("Test", loaded.taskName());
    }

    @Test
    void loadMissingReturnsNull() {

        var store = new MemTaskStateStore();

        assertNull(store.load("nonexistent"));
    }

    @Test
    void listReturnsAll() {

        var store = new MemTaskStateStore();

        store.taskStarted("t1", "Task 1", dummyTask(), Map.of());
        store.taskStarted("t2", "Task 2", dummyTask(), Map.of());

        var all = store.list();

        assertEquals(2, all.size());
    }

    @Test
    void taskCompletedUpdatesStatus() {

        var store = new MemTaskStateStore();

        store.taskStarted("id1", "task1", dummyTask(), Map.of());
        store.taskCompleted("id1", TaskStatus.COMPLETED);

        var loaded = store.load("id1");

        assertEquals(TaskStatus.COMPLETED, loaded.status());
    }
}
