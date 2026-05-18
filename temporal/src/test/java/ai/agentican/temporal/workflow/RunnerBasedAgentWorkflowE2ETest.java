package ai.agentican.temporal.workflow;

import ai.agentican.framework.config.AgentConfig;
import ai.agentican.framework.event.AgenticanEventBus;
import ai.agentican.framework.event.WorkflowRunStorePersister;
import ai.agentican.framework.llm.LlmClient;
import ai.agentican.framework.llm.LlmResponse;
import ai.agentican.framework.llm.StopReason;
import ai.agentican.framework.store.KnowledgeStoreMemory;
import ai.agentican.framework.store.WorkflowRunStoreMemory;
import ai.agentican.framework.tools.Toolkit;
import ai.agentican.temporal.activity.AgenticanActivityImpl;
import ai.agentican.temporal.activity.KnowledgeStoreActivityImpl;
import ai.agentican.temporal.activity.LlmCallActivityImpl;
import ai.agentican.temporal.activity.ToolCallActivityImpl;

import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RunnerBasedAgentWorkflowE2ETest {

    private static final String TASK_QUEUE = "runner-based-agent-test";

    private TestWorkflowEnvironment env;
    private Worker worker;

    @BeforeEach
    void setUp() {

        env = TestWorkflowEnvironment.newInstance();
        worker = env.newWorker(TASK_QUEUE);

        worker.registerWorkflowImplementationTypes(RunnerBasedAgentWorkflowImpl.class);
    }

    @AfterEach
    void tearDown() {

        if (env != null) env.close();
    }

    @Test
    void completesInOneTurnWithoutTools() {

        var calls = new AtomicInteger(0);

        LlmClient fakeLlm = request -> {

            calls.incrementAndGet();

            return new LlmResponse("Hello from agent", List.of(), StopReason.END_TURN, 0, 0, 0, 0, 0);
        };

        var store = new WorkflowRunStoreMemory();
        var knowledge = new KnowledgeStoreMemory();

        // Stand up a minimal "main bus" with just the persister subscribed so the
        // activity-worker side writes through to the store the same way an in-process
        // Agentican instance would. In a real app, this is Agentican.eventBus().
        var mainBus = new AgenticanEventBus();
        mainBus.subscribeFirst(new WorkflowRunStorePersister(store));

        worker.registerActivitiesImplementations(
                new LlmCallActivityImpl(fakeLlm),
                new ToolCallActivityImpl(List.<Toolkit>of()),
                new AgenticanActivityImpl(mainBus, store),
                new KnowledgeStoreActivityImpl(knowledge));

        env.start();

        var input = new RunnerBasedAgentInput(
                AgentConfig.builder().id("test-agent").name("test-agent").role("Test role").build(),
                "Say hello.", "task-1", "step-1", "step", List.of(), List.of(),
                null, null, 5);

        var workflowId = "test-wf-" + System.nanoTime();

        var workflow = env.getWorkflowClient().newWorkflowStub(RunnerBasedAgentWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setTaskQueue(TASK_QUEUE)
                        .setWorkflowId(workflowId)
                        .setWorkflowExecutionTimeout(java.time.Duration.ofSeconds(10))
                        .build());

        String result;

        try {

            result = workflow.run(input);
        }
        catch (RuntimeException failure) {

            var history = env.getWorkflowExecutionHistory(WorkflowExecution.newBuilder()
                    .setWorkflowId(workflowId).build());

            System.err.println("=== WORKFLOW HISTORY ===");
            System.err.println(history);
            System.err.println("=== END HISTORY ===");

            throw failure;
        }
        assertEquals("Hello from agent", result, "workflow should return the LLM's final response text");
        assertEquals(1, calls.get(), "LLM activity should be invoked exactly once for a no-tools 1-turn run");

        var taskLog = store.load("task-1");

        assertNotNull(taskLog, "task entry should have been bootstrapped via taskStarted activity");
    }
}
