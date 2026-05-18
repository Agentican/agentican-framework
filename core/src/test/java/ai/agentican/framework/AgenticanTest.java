package ai.agentican.framework;

import ai.agentican.framework.config.AgentConfig;
import ai.agentican.framework.config.LlmConfig;
import ai.agentican.framework.config.WorkflowConfig;
import ai.agentican.framework.config.SkillConfig;
import ai.agentican.framework.hitl.HitlManager;
import ai.agentican.framework.hitl.HitlResponse;
import ai.agentican.framework.orchestration.model.WorkflowDefinition;
import ai.agentican.framework.orchestration.execution.WorkflowRunStatus;
import ai.agentican.framework.store.WorkflowRunStoreMemory;
import ai.agentican.framework.orchestration.model.WorkflowStepAgent;
import ai.agentican.framework.tools.ToolDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static ai.agentican.framework.MockLlmClient.*;
import ai.agentican.framework.state.RuntimeOwner;
import static org.junit.jupiter.api.Assertions.*;
import ai.agentican.framework.hitl.HitlCheckpoint;
import ai.agentican.framework.llm.LlmRequest;
import ai.agentican.framework.llm.LlmResponse;
import ai.agentican.framework.llm.StopReason;
import ai.agentican.framework.orchestration.execution.resume.ReapReason;
import ai.agentican.framework.orchestration.execution.resume.ResumeClassifier;
import ai.agentican.framework.orchestration.model.WorkflowStepBranch;
import ai.agentican.framework.orchestration.model.WorkflowStepLoop;
import ai.agentican.framework.util.Ids;

class AgenticanTest {

    private static final String MOCK = "mock/llm-notion-test/";

    @Test
    void builderRequiresConfig() {

        assertThrows(IllegalStateException.class, () -> Agentican.builder().build());
    }

    @Test
    void builderDefaultsHitlManager() {

        try (var agentican = Agentican.builder()

                .configuration().api()
                    .llm().apiKey("mock").end()
                    .end()
                .llm("default", request -> endTurn("ok"))
                .build()) {

            assertNotNull(agentican);
        }
    }

    @Test
    void builderDefaultsTaskStateStore() {

        try (var agentican = Agentican.builder()

                .configuration().api()
                    .llm().apiKey("mock").end()
                    .end()
                .llm("default", request -> endTurn("ok"))
                .build()) {

            assertNotNull(agentican);
        }
    }

    @Test
    void runTaskReturnsTaskHandle() {

        var mockLlm = new MockLlmClient()
                .onSend("Do the thing", "Done it.");

        try (var agentican = Agentican.builder()

                .configuration().api()
                    .llm().apiKey("mock").end()
                    .end()
                .llm("default", mockLlm.toLlmClient())
                .build()) {

            var task = WorkflowDefinition.builder("test-task", "test-task").description("")
                    .step().name("step-a").agent("test-agent").instructions("Do the thing").end()
                    .build();

            var handle = agentican.workflow(task).input(Void.class).build().start();

            assertNotNull(handle);
            assertFalse(handle.isCancelled());

            var result = handle.untypedFuture().join();
            assertEquals(WorkflowRunStatus.FAILED, result.status());
        }
    }

    @Test
    void runTaskWithKnownAgent() {

        var mockLlm = new MockLlmClient()

                .onSend("planning-process", """
                    {
                        "type": "create",
                        "name": "Simple Task",
                        "description": "A simple task",
                        "agents": [{"name": "worker", "role": "Does work", "skills": []}],
                        "params": [],
                        "steps": [{"name": "work", "type": "agent", "agent": "worker", "instructions": "Do the work", "toolkits": []}]
                    }
                    """)
                .onSend("Do the work", "Work completed successfully.");

        try (var agentican = Agentican.builder()

                .configuration().api()
                    .llm().apiKey("mock").end()
                    .end()
                .llm("default", mockLlm.toLlmClient())
                .build()) {

            var handle = agentican.run("Do some work");
            var result = handle.untypedFuture().join();

            assertEquals(WorkflowRunStatus.COMPLETED, result.status());
            assertEquals("Work completed successfully.", result.output());
        }
    }

    @Test
    void runTaskWithInputs() {

        var mockLlm = new MockLlmClient()
                .onSend("planning-process", """
                    {
                        "type": "create",
                        "name": "Param Task",
                        "description": "Uses params",
                        "agents": [{"name": "worker", "role": "Worker", "skills": []}],
                        "params": [{"name": "target", "description": "What to process", "defaultValue": "widgets", "required": true}],
                        "steps": [{"name": "process", "type": "agent", "agent": "worker", "instructions": "Process {{param.target}}", "toolkits": []}]
                    }
                    """)
                .onSend("Process widgets", "Processed widgets successfully.");

        try (var agentican = Agentican.builder()

                .configuration().api()
                    .llm().apiKey("mock").end()
                    .end()
                .llm("default", mockLlm.toLlmClient())
                .build()) {

            var handle = agentican.run("Process something");
            var result = handle.untypedFuture().join();

            assertEquals(WorkflowRunStatus.COMPLETED, result.status());
            assertTrue(result.output().contains("widgets"));
        }
    }

    @Test
    void runTaskCancellation() {

        var mockLlm = new MockLlmClient()
                .onSend("planning-process", """
                    {
                        "type": "create",
                        "name": "Cancel Task",
                        "description": "Test",
                        "agents": [{"name": "agent-a", "role": "Worker", "skills": []}],
                        "params": [],
                        "steps": [
                            {"name": "step-a", "type": "agent", "agent": "agent-a", "instructions": "Step A", "toolkits": []},
                            {"name": "step-b", "type": "agent", "agent": "agent-a", "instructions": "Step B", "dependencies": ["step-a"], "toolkits": []}
                        ]
                    }
                    """)
                .onSend("Step A", "Step A done.");

        try (var agentican = Agentican.builder()

                .configuration().api()
                    .llm().apiKey("mock").end()
                    .end()
                .llm("default", mockLlm.toLlmClient())
                .build()) {

            var handle = agentican.run("Do a cancellable task");

            handle.cancel();
            assertTrue(handle.isCancelled());

            var result = handle.untypedFuture().join();
            assertNotEquals(WorkflowRunStatus.COMPLETED, result.status());
        }
    }

    @Test
    void customToolkitRegistered() {

        var mockLlm = new MockLlmClient()
                .onSend("planning-process", """
                    {
                        "type": "create",
                        "name": "Tool Task",
                        "description": "Uses tools",
                        "agents": [{"name": "tool-user", "role": "Uses tools", "skills": []}],
                        "params": [],
                        "steps": [{"name": "use-tool", "type": "agent", "agent": "tool-user", "instructions": "Use MY_TOOL", "tools": ["MY_TOOL"]}]
                    }
                    """)
                .onSend("definition refiner", """
                    {
                      "params": [],
                      "steps": [
                        {"name": "use-tool", "type": "agent", "agent": "tool-user", "instructions": "Use MY_TOOL to get data", "tools": ["MY_TOOL"]}
                      ]
                    }
                    """)
                .onSend("Use MY_TOOL", toolUse("Calling tool", "MY_TOOL", Map.of("q", "test")))
                .onSend("<name>MY_TOOL</name>", "Got the data.");

        var myToolkit = new MockToolkit(List.of(
                new ToolDefinition("MY_TOOL", "A custom tool", Map.of(), List.of())))
                .onExecute("MY_TOOL", "{\"result\": \"custom data\"}");

        try (var agentican = Agentican.builder()

                .configuration().api()
                    .llm().apiKey("mock").end()
                    .end()
                .llm("default", mockLlm.toLlmClient())
                .toolkit("my-toolkit", myToolkit)
                .build()) {

            var handle = agentican.run("Use the custom tool");
            var result = handle.untypedFuture().join();

            assertEquals(WorkflowRunStatus.COMPLETED, result.status());
        }
    }

    @Test
    void hitlAutoApproveFlow() {

        var mockLlm = new MockLlmClient()
                .onSend("planning-process", """
                    {
                        "type": "create",
                        "name": "HITL Task",
                        "description": "Needs approval",
                        "agents": [{"name": "writer", "role": "Writer", "skills": []}],
                        "params": [],
                        "steps": [{"name": "write", "type": "agent", "agent": "writer", "instructions": "Write something", "hitl": true, "toolkits": []}]
                    }
                    """)
                .onSend("Write something", "Here is my draft.");

        var hitlManager = new HitlManager((mgr, checkpoint) ->
                mgr.respond(checkpoint.id(), HitlResponse.approve()));

        try (var agentican = Agentican.builder()

                .configuration().api()
                    .llm().apiKey("mock").end()
                    .end()
                .llm("default", mockLlm.toLlmClient())
                .hitlManager(hitlManager)
                .build()) {

            var handle = agentican.run("Write something that needs approval");
            var result = handle.untypedFuture().join();

            assertEquals(WorkflowRunStatus.COMPLETED, result.status());
        }
    }

    @Test
    void hitlRejectThenApprove_firesCheckpointForEachAttempt() {

        var mockLlm = new MockLlmClient()
                .onSend("planning-process", """
                    {
                        "type": "create",
                        "name": "Iterative HITL Task",
                        "description": "Reviewer revises once then approves",
                        "agents": [{"name": "writer", "role": "Writer", "skills": []}],
                        "params": [],
                        "steps": [{"name": "write", "type": "agent", "agent": "writer", "instructions": "Write something", "hitl": true, "toolkits": []}]
                    }
                    """)
                .onSend("Reviewer Feedback", "Second draft, addressing the feedback.")
                .onSend("Write something", "First draft.");

        var checkpointCount = new java.util.concurrent.atomic.AtomicInteger();

        var hitlManager = new HitlManager((mgr, checkpoint) -> {

            var n = checkpointCount.incrementAndGet();

            if (n == 1) mgr.respond(checkpoint.id(), HitlResponse.reject("Please be more concrete"));
            else        mgr.respond(checkpoint.id(), HitlResponse.approve());
        });

        try (var agentican = Agentican.builder()

                .configuration().api()
                    .llm().apiKey("mock").end()
                    .end()
                .llm("default", mockLlm.toLlmClient())
                .hitlManager(hitlManager)
                .build()) {

            var handle = agentican.run("Write something that needs approval");
            var result = handle.untypedFuture().join();

            assertEquals(WorkflowRunStatus.COMPLETED, result.status());
            assertEquals(2, checkpointCount.get(),
                    "Every attempt (initial + each retry) should fire its own checkpoint");
        }
    }

    @Test
    void hitlRejectEveryAttempt_failsAfterMaxRetries() {

        var mockLlm = new MockLlmClient()
                .onSend("planning-process", """
                    {
                        "type": "create",
                        "name": "Never Approved HITL Task",
                        "description": "Reviewer rejects every attempt",
                        "agents": [{"name": "writer", "role": "Writer", "skills": []}],
                        "params": [],
                        "steps": [{"name": "write", "type": "agent", "agent": "writer", "instructions": "Write something", "hitl": true, "toolkits": []}]
                    }
                    """)
                .onSend("Reviewer Feedback", "Retry draft 2.")
                .onSend("Reviewer Feedback", "Retry draft 3.")
                .onSend("Write something", "Initial draft.");

        var checkpointCount = new java.util.concurrent.atomic.AtomicInteger();

        var hitlManager = new HitlManager((mgr, checkpoint) -> {

            checkpointCount.incrementAndGet();
            mgr.respond(checkpoint.id(), HitlResponse.reject("still not good enough"));
        });

        try (var agentican = Agentican.builder()

                .configuration().api()
                    .llm().apiKey("mock").end()
                    .end()
                .llm("default", mockLlm.toLlmClient())
                .hitlManager(hitlManager)
                .build()) {

            var handle = agentican.run("Write something that needs approval");
            var result = handle.untypedFuture().join();

            assertEquals(WorkflowRunStatus.FAILED, result.status());
            assertEquals(3, checkpointCount.get(),
                    "Default maxRetries=3, so we expect 3 attempts before giving up");
        }
    }

    @Test
    void hitlToolApprovalFlow() {

        var mockLlm = new MockLlmClient()
                .onSend("planning-process", """
                    {
                        "type": "create",
                        "name": "Tool HITL Task",
                        "description": "Tool needs approval",
                        "agents": [{"name": "builder", "role": "Builder", "skills": []}],
                        "params": [],
                        "steps": [{"name": "build", "type": "agent", "agent": "builder", "instructions": "Build with SAFE_TOOL", "tools": ["SAFE_TOOL"]}]
                    }
                    """)
                .onSend("definition refiner", """
                    {
                      "params": [],
                      "steps": [
                        {"name": "build", "type": "agent", "agent": "builder", "instructions": "Use SAFE_TOOL to build", "tools": ["SAFE_TOOL"]}
                      ]
                    }
                    """)
                .onSend("Use SAFE_TOOL", toolUse("Building", "SAFE_TOOL", Map.of("action", "create")))
                .onSend("<name>SAFE_TOOL</name>", "Build complete.");

        var toolkit = new MockToolkit(List.of(
                new ToolDefinition("SAFE_TOOL", "A dangerous tool", Map.of(), List.of())))
                .withHitl("SAFE_TOOL")
                .onExecute("SAFE_TOOL", "{\"created\": true}");

        var hitlManager = new HitlManager((mgr, checkpoint) ->
                mgr.respond(checkpoint.id(), HitlResponse.approve()));

        try (var agentican = Agentican.builder()

                .configuration().api()
                    .llm().apiKey("mock").end()
                    .end()
                .llm("default", mockLlm.toLlmClient())
                .toolkit("tools", toolkit)
                .hitlManager(hitlManager)
                .build()) {

            var handle = agentican.run("Build something safely");
            var result = handle.untypedFuture().join();

            assertEquals(WorkflowRunStatus.COMPLETED, result.status());
        }
    }

    @Test
    void closeIsIdempotent() {

        var agentican = Agentican.builder()

                .configuration().api()
                    .llm().apiKey("mock").end()
                    .end()
                .llm("default", request -> endTurn("ok"))
                .build();

        assertDoesNotThrow(() -> {
            agentican.close();
            agentican.close();
        });
    }

    @Test
    void fullIntegrationWithPlanningAndHitl() {

        var fetchDataResponse = readResource(MOCK + "toolkit-fetch-data-response.json");
        var createPageResponse = readResource(MOCK + "toolkit-create-page-response.json");

        var mockLlm = new MockLlmClient()

                .onSendRepeated("curate a team vector index", endTurn("{\"entries\":[]}"))
                .onSend("planning-process", readResource(MOCK + "pass1-response.json"))
                .onSend("<name>setup-notion</name>", readResource(MOCK + "pass2-setup-response.txt"))
                .onSend("<name>create-page</name>", readResource(MOCK + "pass2-create-response.txt"))
                .onSend("loop step", readResource(MOCK + "pass3-response.json"))
                .onSend("Research Top LLMs", readResource(MOCK + "agent-research-response.txt"))
                .onSend("Browse", toolUse("Browsing workspace.",
                        "NOTION_FETCH_DATA", Map.of("fetch_type", "pages", "query", "")))
                .onSend("<name>NOTION_FETCH_DATA</name>", toolUse("Creating LLM Research parent page.",
                        "NOTION_CREATE_NOTION_PAGE", Map.of("parent_id", "32f5d50f-1480-80d8-acb9-ef671eb4623b", "title", "LLM Research")))
                .onSend("<name>NOTION_CREATE_NOTION_PAGE</name>", readResource(MOCK + "agent-setup-response.txt"))
                .onSend("Claude Opus 4.6", toolUse("Creating page.",
                        "NOTION_CREATE_NOTION_PAGE", Map.of("parent_id", "mock-parent-page-id-001", "title", "Claude Opus 4.6", "markdown", "# Overview")))
                .onSend("GPT-5.4", toolUse("Creating page.",
                        "NOTION_CREATE_NOTION_PAGE", Map.of("parent_id", "mock-parent-page-id-001", "title", "GPT-5.4", "markdown", "# Overview")))
                .onSend("Gemini 3.1 Pro", toolUse("Creating page.",
                        "NOTION_CREATE_NOTION_PAGE", Map.of("parent_id", "mock-parent-page-id-001", "title", "Gemini 3.1 Pro", "markdown", "# Overview")))
                .onSend("<name>NOTION_CREATE_NOTION_PAGE</name>", "Page created successfully.")
                .onSend("<name>NOTION_CREATE_NOTION_PAGE</name>", "Page created successfully.")
                .onSend("<name>NOTION_CREATE_NOTION_PAGE</name>", "Page created successfully.");

        var notionToolkit = new MockToolkit(List.of(
                new ToolDefinition("NOTION_CREATE_NOTION_PAGE", "Create a new Notion page",
                        Map.of("parent_id", Map.of("type", "string", "description", "Parent page ID"),
                               "title", Map.of("type", "string", "description", "Page title"),
                               "markdown", Map.of("type", "string", "description", "Page content as markdown")),
                        List.of("title")),
                new ToolDefinition("NOTION_FETCH_DATA", "Fetch pages or databases from Notion",
                        Map.of("fetch_type", Map.of("type", "string", "description", "Type: pages or databases"),
                               "query", Map.of("type", "string", "description", "Search query")),
                        List.of("fetch_type")),
                new ToolDefinition("NOTION_SEARCH_NOTION_PAGE", "Search for Notion pages",
                        Map.of("query", Map.of("type", "string", "description", "Search query")),
                        List.of()),
                new ToolDefinition("NOTION_ADD_MULTIPLE_PAGE_CONTENT", "Add content blocks to a page",
                        Map.of("parent_block_id", Map.of("type", "string", "description", "Page ID"),
                               "content_blocks", Map.of("type", "array", "description", "Content blocks")),
                        List.of("parent_block_id", "content_blocks"))
        ))
                .withHitl("NOTION_CREATE_NOTION_PAGE")
                .onExecute("NOTION_FETCH_DATA", fetchDataResponse)
                .onExecute("NOTION_CREATE_NOTION_PAGE", createPageResponse);

        var hitlManager = new HitlManager((mgr, checkpoint) ->
                mgr.respond(checkpoint.id(), HitlResponse.approve()));

        try (var agentican = Agentican.builder()

                .configuration().api()
                    .llm().apiKey("mock").end()
                    .end()
                .llm("default", mockLlm.toLlmClient())
                .toolkit("notion", notionToolkit)
                .hitlManager(hitlManager)
                .build()) {

            var run = agentican.run("Find the top 3 LLMs based on reasoning and tool use. For each one, find its pricing and create a separate page in Notion with its details.");
            var result = run.untypedFuture().join();

            assertEquals(WorkflowRunStatus.COMPLETED, result.status());
            assertTrue(result.stepResults().size() >= 3);
        }
    }

    @Test
    void fluentAgentIsRegistered() {

        try (var agentican = Agentican.builder()

                .configuration().api()
                    .llm().apiKey("mock").end()
                    .end()
                .llm("default", request -> endTurn("ok"))
                .registry().api()
                    .agent().id("FluentAgent").name("FluentAgent").role("a fluent test role").end()
                    .end()
                .build()) {

            assertTrue(agentican.registry().agents().hasByName("FluentAgent"));
            assertEquals("FluentAgent", agentican.registry().agents().byName("FluentAgent").name());
        }
    }

    @Test
    void fluentSkillIsRegistered() {

        try (var agentican = Agentican.builder()

                .configuration().api()
                    .llm().apiKey("mock").end()
                    .end()
                .llm("default", request -> endTurn("ok"))
                .registry().api()
                    .skill().id("FluentSkill").name("FluentSkill").instructions("do the thing").end()
                    .end()
                .build()) {

            assertTrue(agentican.registry().skills().hasByName("FluentSkill"));
        }
    }

    @Test
    void fluentPlanIsRegistered() {

        try (var agentican = Agentican.builder()

                .configuration().api()
                    .llm().apiKey("mock").end()
                    .end()
                .llm("default", request -> endTurn("ok"))
                .registry().api()
                    .workflow()
                        .id("fluent-plan").name("fluent-plan").description("desc")
                        .step().name("s1").agent("noop").instructions("do nothing").end()
                        .end()
                    .end()
                .build()) {

            assertNotNull(agentican.registry().workflows().byName("fluent-plan"));
        }
    }

    @Test
    void multipleAgentsFromBuilderRegister() {

        try (var agentican = Agentican.builder()

                .configuration().api()
                    .llm().apiKey("mock").end()
                    .end()
                .llm("default", request -> endTurn("ok"))
                .registry().api()
                    .agent().id("FromConfig").name("FromConfig").role("config role").end()
                    .agent().id("FromFluent").name("FromFluent").role("fluent role").end()
                    .end()
                .build()) {

            assertTrue(agentican.registry().agents().hasByName("FromConfig"));
            assertTrue(agentican.registry().agents().hasByName("FromFluent"));
        }
    }

    @Test
    void reapOrphansMarksInProgressTasksFailed() {

        var store = new WorkflowRunStoreMemory();

        try (var agentican = Agentican.builder()

                .configuration().api()
                    .llm().apiKey("mock").end()
                    .end()
                .llm("default", request -> endTurn("ok"))
                .workflowRunStore(store)
                .build();
                 var service = agentican.recovery()) {

            var taskId = "orphan-" + Ids.generate();
            store.taskStarted(taskId, "left running", null, Map.of(), RuntimeOwner.IN_PROCESS, null);
            var stepId = "step-" + Ids.generate();
            store.stepStarted(taskId, stepId, "running-step");

            var reaped = service.reapOrphans();

            assertEquals(1, reaped);

            var reloaded = store.load(taskId);
            assertEquals(WorkflowRunStatus.FAILED, reloaded.status());
            assertEquals(WorkflowRunStatus.FAILED, reloaded.step("running-step").status());
        }
    }

    @Test
    void reapOrphansLeavesTerminalTasksAlone() {

        var store = new WorkflowRunStoreMemory();

        try (var agentican = Agentican.builder()

                .configuration().api()
                    .llm().apiKey("mock").end()
                    .end()
                .llm("default", request -> endTurn("ok"))
                .workflowRunStore(store)
                .build();
                 var service = agentican.recovery()) {

            var taskId = "done-" + Ids.generate();
            store.taskStarted(taskId, "already done", null, Map.of(), RuntimeOwner.IN_PROCESS, null);
            store.taskCompleted(taskId, WorkflowRunStatus.COMPLETED);

            var reaped = service.reapOrphans();

            assertEquals(0, reaped);
            assertEquals(WorkflowRunStatus.COMPLETED, store.load(taskId).status());
        }
    }

    @Test
    void resumeInterruptedDrivesInflightAgentStepToCompletion() throws Exception {

        var mockLlm = new MockLlmClient()
                .onSend("planning-process", """
                    {
                        "type": "create",
                        "name": "Resume Task",
                        "description": "Resumable task",
                        "agents": [{"name": "worker", "role": "Worker", "skills": []}],
                        "params": [],
                        "steps": [{"name": "do-work", "type": "agent", "agent": "worker", "instructions": "Run to completion after resume"}]
                    }
                    """)
                .onSend("after resume", "All done after resume");

        var store = new WorkflowRunStoreMemory();

        try (var agentican = Agentican.builder()

                .configuration().api()
                    .llm().apiKey("mock").end()
                    .end()
                .llm("default", mockLlm.toLlmClient())
                .registry().api()
                    .agent().id("worker").name("worker").role("Worker role").end()
                    .end()

                .workflowRunStore(store)
                .build();
                 var service = agentican.recovery()) {

            var taskId = "t-" + Ids.generate();
            var stepId = "s-" + Ids.generate();
            var runId = Ids.generate();
            var turnId = Ids.generate();

            var plan = WorkflowDefinition.builder("Resume Task", "Resume Task")
                    .description("test")
                    .step().name("do-work").agent("worker").instructions("Run to completion after resume").end()
                    .build();

            agentican.registry().workflows().register(plan);

            store.taskStarted(taskId, "Resume Task", plan, Map.of(), RuntimeOwner.IN_PROCESS, null);
            store.stepStarted(taskId, stepId, "do-work");
            store.runStarted(taskId, stepId, runId, "worker");
            store.turnStarted(taskId, runId, turnId);

            int handled = service.resumeInterrupted();
            assertEquals(1, handled);

            long deadline = System.currentTimeMillis() + 5000;
            while (System.currentTimeMillis() < deadline) {
                var loaded = store.load(taskId);
                if (loaded != null && loaded.status() == WorkflowRunStatus.COMPLETED) break;
                Thread.sleep(50);
            }

            var final_ = store.load(taskId);
            assertEquals(WorkflowRunStatus.COMPLETED, final_.status(),
                    "Resume should drive the abandoned turn to completion via a fresh turn");

            var doWorkStep = final_.step("do-work");
            assertEquals(WorkflowRunStatus.COMPLETED, doWorkStep.status());
            assertTrue(doWorkStep.output() != null && doWorkStep.output().contains("done"));
        }
    }

    @Test
    void resumeWithPlanCorruptReapsWithSpecificReason() {

        var store = new WorkflowRunStoreMemory();

        try (var agentican = Agentican.builder()

                .configuration().api()
                    .llm().apiKey("mock").end()
                    .end()
                .llm("default", request -> endTurn("ok"))
                .workflowRunStore(store)
                .build()) {

            var taskId = "t-corrupt-" + Ids.generate();
            store.taskStarted(taskId, "corrupt-plan-task", null, Map.of(), RuntimeOwner.IN_PROCESS, null);

            var taskLog = store.load(taskId);
            taskLog.setPlanSnapshotCorrupt(true);

            var classified = ResumeClassifier
                    .classify(taskLog, null);

            assertTrue(classified.reapOnly());
            assertEquals(ReapReason.PLAN_CORRUPT, classified.reapReason());
        }
    }

    @Test
    void listInProgressFiltersOutTerminalTasks() {

        var store = new WorkflowRunStoreMemory();

        try (var agentican = Agentican.builder()

                .configuration().api()
                    .llm().apiKey("mock").end()
                    .end()
                .llm("default", request -> endTurn("ok"))
                .workflowRunStore(store)
                .build()) {

            var runningId = "run-" + Ids.generate();
            var doneId = "done-" + Ids.generate();

            store.taskStarted(runningId, "running", null, Map.of(), RuntimeOwner.IN_PROCESS, null);
            store.taskStarted(doneId, "done", null, Map.of(), RuntimeOwner.IN_PROCESS, null);
            store.taskCompleted(doneId, WorkflowRunStatus.COMPLETED);

            var inProgressIds = store.listInProgress().stream()
                    .map(t -> t.taskId()).toList();

            assertTrue(inProgressIds.contains(runningId));
            assertFalse(inProgressIds.contains(doneId));
        }
    }

    @Test
    void resumeMaxConcurrentGatesResumesWithoutLosingAny() throws Exception {

        var store = new WorkflowRunStoreMemory();

        try (var agentican = Agentican.builder()

                .configuration().api()
                    .llm().apiKey("mock").end()
                    .end()
                .llm("default", request -> endTurn("ok"))
                .registry().api()
                    .agent().id("worker").name("worker").role("Worker").end()
                    .end()

                .workflowRunStore(store)
                .build();
                 var service = agentican.recovery()) {

            var plan = WorkflowDefinition.builder("Bounded Resume", "Bounded Resume")
                    .description("test")
                    .step().name("do").agent("worker").instructions("do it").end()
                    .build();
            agentican.registry().workflows().register(plan);

            for (int i = 0; i < 3; i++) {
                var taskId = "t-" + i + "-" + Ids.generate();
                store.taskStarted(taskId, "Bounded Resume", plan, Map.of(), RuntimeOwner.IN_PROCESS, null);
                var stepId = "s-" + i + "-" + Ids.generate();
                store.stepStarted(taskId, stepId, "do");
            }

            var handled = service.resumeInterrupted(1);
            assertEquals(3, handled);

            long deadline = System.currentTimeMillis() + 10_000;
            while (System.currentTimeMillis() < deadline) {
                if (store.listInProgress().isEmpty()) break;
                Thread.sleep(100);
            }

            assertEquals(0, store.listInProgress().size(),
                    "All 3 tasks should eventually complete despite concurrency=1");
        }
    }

    @Test
    void resumeDispatchesRemainingParallelSiblingsConcurrently() throws Exception {

        var mockLlm = new MockLlmClient()
                .onSendRepeated("curate a team vector index", endTurn("{\"entries\":[]}"))
                .onSend("sibling-a", "A done")
                .onSend("sibling-b", "B done")
                .onSend("sibling-c", "C done")
                .onSend("synthesize", "all synthesized");

        var store = new WorkflowRunStoreMemory();

        try (var agentican = Agentican.builder()

                .configuration().api()
                    .llm().apiKey("mock").end()
                    .end()
                .llm("default", mockLlm.toLlmClient())
                .registry().api()
                    .agent().id("worker").name("worker").role("Worker").end()
                    .end()

                .workflowRunStore(store)
                .build();
                 var service = agentican.recovery()) {

            var siblingA = new WorkflowStepAgent(
                    "sibling-a", "worker", "sibling-a", List.of(), false, List.of(), List.of());
            var siblingB = new WorkflowStepAgent(
                    "sibling-b", "worker", "sibling-b", List.of(), false, List.of(), List.of());
            var siblingC = new WorkflowStepAgent(
                    "sibling-c", "worker", "sibling-c", List.of(), false, List.of(), List.of());
            var synth = new WorkflowStepAgent(
                    "synthesize", "worker", "synthesize",
                    List.of("sibling-a", "sibling-b", "sibling-c"), false, List.of(), List.of());

            var plan = WorkflowDefinition.builder("Parallel Resume", "Parallel Resume")
                    .description("test")
                    .steps(List.of(siblingA, siblingB, siblingC, synth))
                    .build();

            agentican.registry().workflows().register(plan);

            var taskId = "t-" + Ids.generate();
            store.taskStarted(taskId, "Parallel Resume", plan, Map.of(), RuntimeOwner.IN_PROCESS, null);

            var aStepId = Ids.generate();
            store.stepStarted(taskId, aStepId, "sibling-a");
            store.stepCompleted(taskId, aStepId, WorkflowRunStatus.COMPLETED, "A done");

            int handled = service.resumeInterrupted();
            assertEquals(1, handled);

            long deadline = System.currentTimeMillis() + 5000;
            while (System.currentTimeMillis() < deadline) {
                var loaded = store.load(taskId);
                if (loaded != null && loaded.status() == WorkflowRunStatus.COMPLETED) break;
                Thread.sleep(50);
            }

            var finalLog = store.load(taskId);
            assertEquals(WorkflowRunStatus.COMPLETED, finalLog.status(),
                    "Parallel-resume should reach COMPLETED via the runSeeded dispatch loop");

            assertEquals(WorkflowRunStatus.COMPLETED, finalLog.step("sibling-a").status());
            assertEquals(WorkflowRunStatus.COMPLETED, finalLog.step("sibling-b").status());
            assertEquals(WorkflowRunStatus.COMPLETED, finalLog.step("sibling-c").status());
            assertEquals(WorkflowRunStatus.COMPLETED, finalLog.step("synthesize").status());

            assertEquals(0, finalLog.step("sibling-a").runs().size(),
                    "Already-completed step must NOT be re-dispatched (zero new runs after resume)");
            assertTrue(finalLog.step("sibling-b").runs().size() >= 1);
            assertTrue(finalLog.step("sibling-c").runs().size() >= 1);
        }
    }

    @Test
    void resumeInterruptedClassifiesAndReaps() {

        var store = new WorkflowRunStoreMemory();

        try (var agentican = Agentican.builder()

                .configuration().api()
                    .llm().apiKey("mock").end()
                    .end()
                .llm("default", request -> endTurn("ok"))
                .workflowRunStore(store)
                .build();
                 var service = agentican.recovery()) {

            var taskId = "interrupted-" + Ids.generate();
            store.taskStarted(taskId, "mid-step-crash", null, Map.of(), RuntimeOwner.IN_PROCESS, null);
            var stepId = "step-" + Ids.generate();
            store.stepStarted(taskId, stepId, "working-step");

            var handled = service.resumeInterrupted();

            assertEquals(1, handled);

            var reloaded = store.load(taskId);
            assertEquals(WorkflowRunStatus.FAILED, reloaded.status(),
                    "In v1, resumeInterrupted falls back to reap while drive-forward is implemented in PR 5");
        }
    }

    @Test
    void reapOrphansLeavesSubTasksToParent() {

        var store = new WorkflowRunStoreMemory();

        try (var agentican = Agentican.builder()

                .configuration().api()
                    .llm().apiKey("mock").end()
                    .end()
                .llm("default", request -> endTurn("ok"))
                .workflowRunStore(store)
                .build();
                 var service = agentican.recovery()) {

            var parentId = "parent-" + Ids.generate();
            var childId = "child-" + Ids.generate();
            var stepId = "s-" + Ids.generate();

            store.taskStarted(parentId, "parent", null, Map.of(), RuntimeOwner.IN_PROCESS, null);
            store.stepStarted(parentId, stepId, "loop-step");
            store.taskStarted(childId, "iter-0", null, Map.of(), parentId, stepId, 0, RuntimeOwner.IN_PROCESS, null);

            var reaped = service.reapOrphans();

            assertEquals(1, reaped, "Only the parent is counted in the reap total; sub-tasks cascade");
            assertEquals(WorkflowRunStatus.FAILED, store.load(parentId).status());
            assertEquals(WorkflowRunStatus.FAILED, store.load(childId).status(),
                    "Sub-task cascades to FAILED when its parent is reaped — prevents orphan RUNNING sub-task rows");
        }
    }

    @Test
    void resumeBranchStepUsesExistingCompletedChildWithoutReDispatch() throws Exception {

        var llmCallCount = new java.util.concurrent.atomic.AtomicInteger(0);

        var mockLlm = new MockLlmClient()
                .onSendRepeated("should-never-call", endTurn("would be wrong"));

        var store = new WorkflowRunStoreMemory();

        try (var agentican = Agentican.builder()

                .configuration().api()
                    .llm().apiKey("mock").end()
                    .end()
                .llm("default", request -> {
                    llmCallCount.incrementAndGet();
                    return mockLlm.toLlmClient().send(request);
                })
                .registry().api()
                    .agent().id("worker").name("worker").role("Worker").end()
                    .end()

                .workflowRunStore(store)
                .build();
                 var service = agentican.recovery()) {

            var pathBodyStep = new WorkflowStepAgent(
                    "path-body", "worker", "do path", List.of(), false, List.of(), List.of());
            var sourceForBranch = new WorkflowStepAgent(
                    "source", "worker", "produce", List.of(), false, List.of(), List.of());
            var branch = new WorkflowStepBranch(
                    "choose", "source",
                    List.of(new WorkflowStepBranch.Branch(
                            "A", List.of(pathBodyStep))),
                    "A", List.of(), false);

            var plan = WorkflowDefinition.builder("Branch Resume", "Branch Resume")
                    .description("test")
                    .steps(List.of(sourceForBranch, branch))
                    .build();

            agentican.registry().workflows().register(plan);

            var taskId = "t-branch-" + Ids.generate();
            var stepId = "s-" + Ids.generate();
            var childId = "c-" + Ids.generate();
            var childStepId = "cs-" + Ids.generate();

            store.taskStarted(taskId, "Branch Resume", plan, Map.of(), RuntimeOwner.IN_PROCESS, null);

            var sourceStepId = "src-" + Ids.generate();
            store.stepStarted(taskId, sourceStepId, "source");
            store.stepCompleted(taskId, sourceStepId, WorkflowRunStatus.COMPLETED, "source-output");

            store.stepStarted(taskId, stepId, "choose");
            store.branchPathChosen(taskId, stepId, "A");

            var childPlan = WorkflowDefinition.builder("choose-A", "choose-A")
                    .description("").steps(List.of(pathBodyStep)).build();
            store.taskStarted(childId, "choose-A", childPlan, Map.of(), taskId, stepId, 0, RuntimeOwner.IN_PROCESS, null);
            store.stepStarted(childId, childStepId, "path-body");
            store.stepCompleted(childId, childStepId, WorkflowRunStatus.COMPLETED, "prerecorded path output");
            store.taskCompleted(childId, WorkflowRunStatus.COMPLETED);

            var before = llmCallCount.get();

            int handled = service.resumeInterrupted();
            assertEquals(1, handled);

            long deadline = System.currentTimeMillis() + 5000;
            while (System.currentTimeMillis() < deadline) {
                var loaded = store.load(taskId);
                if (loaded != null && loaded.status() == WorkflowRunStatus.COMPLETED) break;
                Thread.sleep(50);
            }

            var finalLog = store.load(taskId);
            assertEquals(WorkflowRunStatus.COMPLETED, finalLog.status(),
                    "Branch-resume should complete the parent task by reusing the existing child output");
            assertEquals(before, llmCallCount.get(),
                    "No LLM call should be made — the existing completed child output is reused verbatim");
        }
    }

    @Test
    void resumeLoopStepSkipsCompletedIterations() throws Exception {

        var llmCallCount = new java.util.concurrent.atomic.AtomicInteger(0);

        var mockLlm = new MockLlmClient()
                .onSendRepeated("iter-body", endTurn("iter-1 fresh output"));

        var store = new WorkflowRunStoreMemory();

        try (var agentican = Agentican.builder()

                .configuration().api()
                    .llm().apiKey("mock").end()
                    .end()
                .llm("default", request -> {
                    llmCallCount.incrementAndGet();
                    return mockLlm.toLlmClient().send(request);
                })
                .registry().api()
                    .agent().id("worker").name("worker").role("Worker").end()
                    .end()

                .workflowRunStore(store)
                .build();
                 var service = agentican.recovery()) {

            var source = new WorkflowStepAgent(
                    "source", "worker", "produce items", List.of(), false, List.of(), List.of());

            var bodyStep = new WorkflowStepAgent(
                    "iter-body", "worker", "iter-body", List.of(), false, List.of(), List.of());

            var loop = new WorkflowStepLoop(
                    "each", "source", List.of(bodyStep), List.of(), false);

            var plan = WorkflowDefinition.builder("Loop Resume", "Loop Resume")
                    .description("test")
                    .steps(List.of(source, loop))
                    .build();

            agentican.registry().workflows().register(plan);

            var taskId = "t-loop-" + Ids.generate();
            var sourceStepId = "src-" + Ids.generate();
            var loopStepId = "loop-" + Ids.generate();
            var iter0Id = "i0-" + Ids.generate();
            var iter0StepId = "i0s-" + Ids.generate();

            store.taskStarted(taskId, "Loop Resume", plan, Map.of(), RuntimeOwner.IN_PROCESS, null);

            store.stepStarted(taskId, sourceStepId, "source");
            store.stepCompleted(taskId, sourceStepId, WorkflowRunStatus.COMPLETED,
                    "[\"a\",\"b\"]");

            store.stepStarted(taskId, loopStepId, "each");

            var iterPlan = WorkflowDefinition.builder("each-iter-1", "each-iter-1")
                    .description("").steps(List.of(bodyStep)).build();
            store.taskStarted(iter0Id, "each-iter-1", iterPlan, Map.of(), taskId, loopStepId, 0, RuntimeOwner.IN_PROCESS, null);
            store.stepStarted(iter0Id, iter0StepId, "iter-body");
            store.stepCompleted(iter0Id, iter0StepId, WorkflowRunStatus.COMPLETED, "iter-0 prerecorded");
            store.taskCompleted(iter0Id, WorkflowRunStatus.COMPLETED);

            var before = llmCallCount.get();

            int handled = service.resumeInterrupted();
            assertEquals(1, handled);

            long deadline = System.currentTimeMillis() + 5000;
            while (System.currentTimeMillis() < deadline) {
                var loaded = store.load(taskId);
                if (loaded != null && loaded.status() == WorkflowRunStatus.COMPLETED) break;
                Thread.sleep(50);
            }

            var finalLog = store.load(taskId);
            assertEquals(WorkflowRunStatus.COMPLETED, finalLog.status(),
                    "Loop-resume should complete after dispatching only the missing iteration");

            var iter0Log = store.load(iter0Id);
            assertEquals(WorkflowRunStatus.COMPLETED, iter0Log.status());
            assertEquals("iter-0 prerecorded", iter0Log.step("iter-body").output(),
                    "Completed iteration output must be preserved verbatim — iter-0 was not re-run");

            assertEquals(before + 1, llmCallCount.get(),
                    "Exactly one LLM call expected — for the missing iteration only");
        }
    }

    @Test
    void resumeSuspendedStepWithRejectedStepOutputMarksTaskFailedWithFeedback() throws Exception {

        var mockLlm = new MockLlmClient();

        var store = new WorkflowRunStoreMemory();

        try (var agentican = Agentican.builder()

                .configuration().api()
                    .llm().apiKey("mock").end()
                    .end()
                .llm("default", mockLlm.toLlmClient())
                .registry().api()
                    .agent().id("worker").name("worker").role("Worker").end()
                    .end()

                .workflowRunStore(store)
                .build();
                 var service = agentican.recovery()) {

            var plan = WorkflowDefinition.builder("Rejected-Output Resume", "Rejected-Output Resume")
                    .description("test")
                    .step().name("review").agent("worker").instructions("review draft").hitl().end()
                    .build();

            agentican.registry().workflows().register(plan);

            var taskId = "t-rej-" + Ids.generate();
            var stepId = "s-" + Ids.generate();
            var runId = Ids.generate();
            var turnId = Ids.generate();

            store.taskStarted(taskId, "Rejected-Output Resume", plan, Map.of(), RuntimeOwner.IN_PROCESS, null);
            store.stepStarted(taskId, stepId, "review");
            store.runStarted(taskId, stepId, runId, "worker");
            store.turnStarted(taskId, runId, turnId);
            store.messageSent(taskId, turnId,
                    new LlmRequest("sys", null, "u", List.of(), 0, "d", "a", "c", null, java.util.List.of()));
            store.responseReceived(taskId, turnId,
                    new LlmResponse("draft", List.of(),
                            StopReason.END_TURN, 1, 1, 0, 0, 0));
            store.turnCompleted(taskId, turnId);

            var checkpoint = new HitlCheckpoint(
                    Ids.generate(),
                    HitlCheckpoint.Type.STEP_OUTPUT,
                    "review", "Step output: review", "draft");
            store.hitlNotified(taskId, stepId, checkpoint);
            store.hitlResponded(taskId, stepId, HitlResponse.reject("needs more polish"));
            store.stepCompleted(taskId, stepId, WorkflowRunStatus.SUSPENDED, "draft");

            int handled = service.resumeInterrupted();
            assertEquals(1, handled);

            long deadline = System.currentTimeMillis() + 5000;
            while (System.currentTimeMillis() < deadline) {
                var loaded = store.load(taskId);
                if (loaded != null && loaded.status() != null) break;
                Thread.sleep(50);
            }

            var finalLog = store.load(taskId);
            assertEquals(WorkflowRunStatus.FAILED, finalLog.status(),
                    "Rejected STEP_OUTPUT on resume must drive the task to FAILED");
            assertEquals(WorkflowRunStatus.FAILED, finalLog.step("review").status());
            assertNotNull(finalLog.step("review").output());
            assertTrue(finalLog.step("review").output().contains("needs more polish"),
                    "Rejection feedback must be surfaced in the step output");
        }
    }

}
