package ai.agentican.framework;

import ai.agentican.framework.config.AgentConfig;
import ai.agentican.framework.config.LlmConfig;
import ai.agentican.framework.config.PlanConfig;
import ai.agentican.framework.config.RuntimeConfig;
import ai.agentican.framework.config.SkillConfig;
import ai.agentican.framework.hitl.HitlManager;
import ai.agentican.framework.hitl.HitlResponse;
import ai.agentican.framework.orchestration.model.Plan;
import ai.agentican.framework.orchestration.execution.TaskStatus;
import ai.agentican.framework.orchestration.model.PlanStepAgent;
import ai.agentican.framework.tools.ToolDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static ai.agentican.framework.MockLlmClient.*;
import static org.junit.jupiter.api.Assertions.*;

class AgenticanTest {

    private static final String MOCK = "mock/llm-notion-test/";

    @Test
    void builderRequiresConfig() {

        assertThrows(IllegalStateException.class, () -> Agentican.builder().build());
    }

    @Test
    void builderDefaultsHitlManager() {

        var config = RuntimeConfig.builder()
                .llm(LlmConfig.builder().apiKey("mock").build())
                .build();

        try (var agentican = Agentican.builder()
                .config(config)
                .llm("default", request -> endTurn("ok"))
                .build()) {

            assertNotNull(agentican);
        }
    }

    @Test
    void builderDefaultsTaskStateStore() {

        var config = RuntimeConfig.builder()
                .llm(LlmConfig.builder().apiKey("mock").build())
                .build();

        try (var agentican = Agentican.builder()
                .config(config)
                .llm("default", request -> endTurn("ok"))
                .build()) {

            assertNotNull(agentican);
        }
    }

    @Test
    void runTaskReturnsTaskHandle() {

        var mockLlm = new MockLlmClient()
                .onSend("Do the thing", "Done it.");

        var config = RuntimeConfig.builder()
                .llm(LlmConfig.builder().apiKey("mock").build())
                .build();

        try (var agentican = Agentican.builder()
                .config(config)
                .llm("default", mockLlm.toLlmClient())
                .build()) {

            var task = new Plan(null, "test-task", "", List.of(),
                    List.of(new PlanStepAgent("step-a", "test-agent", "Do the thing", List.of(), false, List.of(), List.of())));

            var handle = agentican.run(task);

            assertNotNull(handle);
            assertFalse(handle.isCancelled());

            var result = handle.result();
            assertEquals(TaskStatus.FAILED, result.status());
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
                        "paramConfigs": [],
                        "stepConfigs": [{"name": "work", "type": "agent", "agent": "worker", "instructions": "Do the work", "toolkits": []}]
                    }
                    """)
                .onSend("Do the work", "Work completed successfully.");

        var config = RuntimeConfig.builder()
                .llm(LlmConfig.builder().apiKey("mock").build())
                .build();

        try (var agentican = Agentican.builder()
                .config(config)
                .llm("default", mockLlm.toLlmClient())
                .build()) {

            var handle = agentican.run("Do some work");
            var result = handle.result();

            assertEquals(TaskStatus.COMPLETED, result.status());
            assertEquals("Work completed successfully.", result.lastOutput());
        }
    }

    @Test
    void runTaskWithInputs() {

        var config = RuntimeConfig.builder()
                .llm(LlmConfig.builder().apiKey("mock").build())
                .build();

        var mockLlm = new MockLlmClient()
                .onSend("planning-process", """
                    {
                        "type": "create",
                        "name": "Param Task",
                        "description": "Uses params",
                        "agents": [{"name": "worker", "role": "Worker", "skills": []}],
                        "paramConfigs": [{"name": "target", "description": "What to process", "defaultValue": "widgets", "required": true}],
                        "stepConfigs": [{"name": "process", "type": "agent", "agent": "worker", "instructions": "Process {{param.target}}", "toolkits": []}]
                    }
                    """)
                .onSend("Process widgets", "Processed widgets successfully.");

        try (var agentican = Agentican.builder()
                .config(config)
                .llm("default", mockLlm.toLlmClient())
                .build()) {

            var handle = agentican.run("Process something");
            var result = handle.result();

            assertEquals(TaskStatus.COMPLETED, result.status());
            assertTrue(result.lastOutput().contains("widgets"));
        }
    }

    @Test
    void runTaskCancellation() {

        var config = RuntimeConfig.builder()
                .llm(LlmConfig.builder().apiKey("mock").build())
                .build();

        var mockLlm = new MockLlmClient()
                .onSend("planning-process", """
                    {
                        "type": "create",
                        "name": "Cancel Task",
                        "description": "Test",
                        "agents": [{"name": "agent-a", "role": "Worker", "skills": []}],
                        "paramConfigs": [],
                        "stepConfigs": [
                            {"name": "step-a", "type": "agent", "agent": "agent-a", "instructions": "Step A", "toolkits": []},
                            {"name": "step-b", "type": "agent", "agent": "agent-a", "instructions": "Step B", "dependencies": ["step-a"], "toolkits": []}
                        ]
                    }
                    """)
                .onSend("Step A", "Step A done.");

        try (var agentican = Agentican.builder()
                .config(config)
                .llm("default", mockLlm.toLlmClient())
                .build()) {

            var handle = agentican.run("Do a cancellable task");

            handle.cancel();
            assertTrue(handle.isCancelled());

            var result = handle.result();
            assertNotEquals(TaskStatus.COMPLETED, result.status());
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
                        "paramConfigs": [],
                        "stepConfigs": [{"name": "use-tool", "type": "agent", "agent": "tool-user", "instructions": "Use MY_TOOL", "tools": ["MY_TOOL"]}]
                    }
                    """)
                .onSend("plan refiner", """
                    {
                      "paramConfigs": [],
                      "stepConfigs": [
                        {"name": "use-tool", "type": "agent", "agent": "tool-user", "instructions": "Use MY_TOOL to get data", "tools": ["MY_TOOL"]}
                      ]
                    }
                    """)
                .onSend("Use MY_TOOL", toolUse("Calling tool", "MY_TOOL", Map.of("q", "test")))
                .onSend("<name>MY_TOOL</name>", "Got the data.");

        var myToolkit = new MockToolkit(List.of(
                new ToolDefinition("MY_TOOL", "A custom tool", Map.of(), List.of())))
                .onExecute("MY_TOOL", "{\"result\": \"custom data\"}");

        var config = RuntimeConfig.builder()
                .llm(LlmConfig.builder().apiKey("mock").build())
                .build();

        try (var agentican = Agentican.builder()
                .config(config)
                .llm("default", mockLlm.toLlmClient())
                .toolkit("my-toolkit", myToolkit)
                .build()) {

            var handle = agentican.run("Use the custom tool");
            var result = handle.result();

            assertEquals(TaskStatus.COMPLETED, result.status());
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
                        "paramConfigs": [],
                        "stepConfigs": [{"name": "write", "type": "agent", "agent": "writer", "instructions": "Write something", "hitl": true, "toolkits": []}]
                    }
                    """)
                .onSend("Write something", "Here is my draft.");

        var hitlManager = new HitlManager((mgr, checkpoint) ->
                mgr.respond(checkpoint.id(), HitlResponse.approve()));

        var config = RuntimeConfig.builder()
                .llm(LlmConfig.builder().apiKey("mock").build())
                .build();

        try (var agentican = Agentican.builder()
                .config(config)
                .llm("default", mockLlm.toLlmClient())
                .hitlManager(hitlManager)
                .build()) {

            var handle = agentican.run("Write something that needs approval");
            var result = handle.result();

            assertEquals(TaskStatus.COMPLETED, result.status());
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
                        "paramConfigs": [],
                        "stepConfigs": [{"name": "build", "type": "agent", "agent": "builder", "instructions": "Build with SAFE_TOOL", "tools": ["SAFE_TOOL"]}]
                    }
                    """)
                .onSend("plan refiner", """
                    {
                      "paramConfigs": [],
                      "stepConfigs": [
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

        var config = RuntimeConfig.builder()
                .llm(LlmConfig.builder().apiKey("mock").build())
                .build();

        try (var agentican = Agentican.builder()
                .config(config)
                .llm("default", mockLlm.toLlmClient())
                .toolkit("tools", toolkit)
                .hitlManager(hitlManager)
                .build()) {

            var handle = agentican.run("Build something safely");
            var result = handle.result();

            assertEquals(TaskStatus.COMPLETED, result.status());
        }
    }

    @Test
    void closeIsIdempotent() {

        var config = RuntimeConfig.builder()
                .llm(LlmConfig.builder().apiKey("mock").build())
                .build();

        var agentican = Agentican.builder()
                .config(config)
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

                .onSendRepeated("curate a team knowledge base", endTurn("{\"entries\":[]}"))
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

        var config = RuntimeConfig.builder()
                .llm(LlmConfig.builder().apiKey("mock").build())
                .build();

        try (var agentican = Agentican.builder()
                .config(config)
                .llm("default", mockLlm.toLlmClient())
                .toolkit("notion", notionToolkit)
                .hitlManager(hitlManager)
                .build()) {

            var result = agentican.run("Find the top 3 LLMs based on reasoning and tool use. For each one, find its pricing and create a separate page in Notion with its details.");

            assertEquals(TaskStatus.COMPLETED, result.result().status());
            assertTrue(result.result().stepResults().size() >= 3);
        }
    }

    @Test
    void fluentAgentIsRegistered() {

        var config = RuntimeConfig.builder()
                .llm(LlmConfig.builder().apiKey("mock").build())
                .build();

        try (var agentican = Agentican.builder()
                .config(config)
                .llm("default", request -> endTurn("ok"))
                .agent(AgentConfig.forCatalog("fluent-agent-id", "FluentAgent", "a fluent test role", null))
                .build()) {

            assertTrue(agentican.agents().isRegisteredByName("FluentAgent"));
            assertEquals("FluentAgent", agentican.agents().getByName("FluentAgent").name());
        }
    }

    @Test
    void fluentSkillIsRegistered() {

        var config = RuntimeConfig.builder()
                .llm(LlmConfig.builder().apiKey("mock").build())
                .build();

        try (var agentican = Agentican.builder()
                .config(config)
                .llm("default", request -> endTurn("ok"))
                .skill(SkillConfig.forCatalog("fluent-skill-id", "FluentSkill", "do the thing"))
                .build()) {

            assertTrue(agentican.skills().isRegisteredByName("FluentSkill"));
        }
    }

    @Test
    void fluentPlanIsRegistered() {

        var step = new PlanConfig.PlanStepConfig("s1", "agent", "noop", "do nothing",
                List.of(), false, List.of(), List.of(), null, null, List.of(), null, List.of());

        var planConfig = new PlanConfig("fluent-plan", "desc", List.of(), List.of(step), "fluent-plan-ext");

        var config = RuntimeConfig.builder()
                .llm(LlmConfig.builder().apiKey("mock").build())
                .build();

        try (var agentican = Agentican.builder()
                .config(config)
                .llm("default", request -> endTurn("ok"))
                .plan(planConfig)
                .build()) {

            assertNotNull(agentican.plans().get("fluent-plan"));
        }
    }

    @Test
    void fluentAndConfigAgentsBothRegister() {

        var config = RuntimeConfig.builder()
                .llm(LlmConfig.builder().apiKey("mock").build())
                .agent(AgentConfig.forCatalog("config-agent-id", "FromConfig", "config role", null))
                .build();

        try (var agentican = Agentican.builder()
                .config(config)
                .llm("default", request -> endTurn("ok"))
                .agent(AgentConfig.forCatalog("fluent-agent-id", "FromFluent", "fluent role", null))
                .build()) {

            assertTrue(agentican.agents().isRegisteredByName("FromConfig"));
            assertTrue(agentican.agents().isRegisteredByName("FromFluent"));
        }
    }

    @Test
    void reapOrphansMarksInProgressTasksFailed() {

        var config = RuntimeConfig.builder()
                .llm(LlmConfig.builder().apiKey("mock").build())
                .build();

        try (var agentican = Agentican.builder()
                .config(config)
                .llm("default", request -> endTurn("ok"))
                .build()) {

            var store = agentican.taskStateStore();

            var taskId = "orphan-" + ai.agentican.framework.util.Ids.generate();
            store.taskStarted(taskId, "left running", null, Map.of());
            var stepId = "step-" + ai.agentican.framework.util.Ids.generate();
            store.stepStarted(taskId, stepId, "running-step");

            var reaped = agentican.reapOrphans();

            assertEquals(1, reaped);

            var reloaded = store.load(taskId);
            assertEquals(TaskStatus.FAILED, reloaded.status());
            assertEquals(TaskStatus.FAILED, reloaded.step("running-step").status());
        }
    }

    @Test
    void reapOrphansLeavesTerminalTasksAlone() {

        var config = RuntimeConfig.builder()
                .llm(LlmConfig.builder().apiKey("mock").build())
                .build();

        try (var agentican = Agentican.builder()
                .config(config)
                .llm("default", request -> endTurn("ok"))
                .build()) {

            var store = agentican.taskStateStore();
            var taskId = "done-" + ai.agentican.framework.util.Ids.generate();
            store.taskStarted(taskId, "already done", null, Map.of());
            store.taskCompleted(taskId, TaskStatus.COMPLETED);

            var reaped = agentican.reapOrphans();

            assertEquals(0, reaped);
            assertEquals(TaskStatus.COMPLETED, store.load(taskId).status());
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
                        "paramConfigs": [],
                        "stepConfigs": [{"name": "do-work", "type": "agent", "agent": "worker", "instructions": "Run to completion after resume"}]
                    }
                    """)
                .onSend("after resume", "All done after resume");

        var config = RuntimeConfig.builder()
                .llm(LlmConfig.builder().apiKey("mock").build())
                .build();

        try (var agentican = Agentican.builder()
                .config(config)
                .llm("default", mockLlm.toLlmClient())
                .agent(AgentConfig.forCatalog("worker", "worker", "Worker role", null))
                .build()) {

            var store = agentican.taskStateStore();

            var taskId = "t-" + ai.agentican.framework.util.Ids.generate();
            var stepId = "s-" + ai.agentican.framework.util.Ids.generate();
            var runId = ai.agentican.framework.util.Ids.generate();
            var turnId = ai.agentican.framework.util.Ids.generate();

            var step = ai.agentican.framework.orchestration.model.PlanStepAgent.of(
                    "do-work", "worker", "Run to completion after resume",
                    List.of(), false, List.of(), List.of());
            var plan = ai.agentican.framework.orchestration.model.Plan.of(
                    "Resume Task", "test", List.of(), List.of(step));

            agentican.plans().register(plan);

            store.taskStarted(taskId, "Resume Task", plan, Map.of());
            store.stepStarted(taskId, stepId, "do-work");
            store.runStarted(taskId, stepId, runId, "worker");
            store.turnStarted(taskId, runId, turnId);

            int handled = agentican.resumeInterrupted();
            assertEquals(1, handled);

            long deadline = System.currentTimeMillis() + 5000;
            while (System.currentTimeMillis() < deadline) {
                var loaded = store.load(taskId);
                if (loaded != null && loaded.status() == TaskStatus.COMPLETED) break;
                Thread.sleep(50);
            }

            var final_ = store.load(taskId);
            assertEquals(TaskStatus.COMPLETED, final_.status(),
                    "Resume should drive the abandoned turn to completion via a fresh turn");

            var doWorkStep = final_.step("do-work");
            assertEquals(TaskStatus.COMPLETED, doWorkStep.status());
            assertTrue(doWorkStep.output() != null && doWorkStep.output().contains("done"));
        }
    }

    @Test
    void resumeWithPlanCorruptReapsWithSpecificReason() {

        var config = RuntimeConfig.builder()
                .llm(LlmConfig.builder().apiKey("mock").build())
                .build();

        try (var agentican = Agentican.builder()
                .config(config)
                .llm("default", request -> endTurn("ok"))
                .build()) {

            var store = agentican.taskStateStore();

            var taskId = "t-corrupt-" + ai.agentican.framework.util.Ids.generate();
            store.taskStarted(taskId, "corrupt-plan-task", null, Map.of());

            var taskLog = store.load(taskId);
            taskLog.setPlanSnapshotCorrupt(true);

            var classified = ai.agentican.framework.orchestration.execution.resume.ResumeClassifier
                    .classify(taskLog, null);

            assertTrue(classified.reapOnly());
            assertEquals(ai.agentican.framework.orchestration.execution.resume.ReapReason.PLAN_CORRUPT, classified.reapReason());
        }
    }

    @Test
    void listInProgressFiltersOutTerminalTasks() {

        var config = RuntimeConfig.builder()
                .llm(LlmConfig.builder().apiKey("mock").build())
                .build();

        try (var agentican = Agentican.builder()
                .config(config)
                .llm("default", request -> endTurn("ok"))
                .build()) {

            var store = agentican.taskStateStore();
            var runningId = "run-" + ai.agentican.framework.util.Ids.generate();
            var doneId = "done-" + ai.agentican.framework.util.Ids.generate();

            store.taskStarted(runningId, "running", null, Map.of());
            store.taskStarted(doneId, "done", null, Map.of());
            store.taskCompleted(doneId, TaskStatus.COMPLETED);

            var inProgressIds = store.listInProgress().stream()
                    .map(t -> t.taskId()).toList();

            assertTrue(inProgressIds.contains(runningId));
            assertFalse(inProgressIds.contains(doneId));
        }
    }

    @Test
    void resumeMaxConcurrentGatesResumesWithoutLosingAny() throws Exception {

        var config = RuntimeConfig.builder()
                .llm(LlmConfig.builder().apiKey("mock").build())
                .build();

        try (var agentican = Agentican.builder()
                .config(config)
                .llm("default", request -> endTurn("ok"))
                .agent(AgentConfig.forCatalog("worker", "worker", "Worker", null))
                .build()) {

            var store = agentican.taskStateStore();
            var step = ai.agentican.framework.orchestration.model.PlanStepAgent.of(
                    "do", "worker", "do it", List.of(), false, List.of(), List.of());
            var plan = ai.agentican.framework.orchestration.model.Plan.of(
                    "Bounded Resume", "test", List.of(), List.of(step));
            agentican.plans().register(plan);

            for (int i = 0; i < 3; i++) {
                var taskId = "t-" + i + "-" + ai.agentican.framework.util.Ids.generate();
                store.taskStarted(taskId, "Bounded Resume", plan, Map.of());
                var stepId = "s-" + i + "-" + ai.agentican.framework.util.Ids.generate();
                store.stepStarted(taskId, stepId, "do");
            }

            var handled = agentican.resumeInterrupted(1);
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
                .onSendRepeated("curate a team knowledge base", endTurn("{\"entries\":[]}"))
                .onSend("sibling-a", "A done")
                .onSend("sibling-b", "B done")
                .onSend("sibling-c", "C done")
                .onSend("synthesize", "all synthesized");

        var config = RuntimeConfig.builder()
                .llm(LlmConfig.builder().apiKey("mock").build())
                .build();

        try (var agentican = Agentican.builder()
                .config(config)
                .llm("default", mockLlm.toLlmClient())
                .agent(AgentConfig.forCatalog("worker", "worker", "Worker", null))
                .build()) {

            var store = agentican.taskStateStore();

            var siblingA = ai.agentican.framework.orchestration.model.PlanStepAgent.of(
                    "sibling-a", "worker", "sibling-a", List.of(), false, List.of(), List.of());
            var siblingB = ai.agentican.framework.orchestration.model.PlanStepAgent.of(
                    "sibling-b", "worker", "sibling-b", List.of(), false, List.of(), List.of());
            var siblingC = ai.agentican.framework.orchestration.model.PlanStepAgent.of(
                    "sibling-c", "worker", "sibling-c", List.of(), false, List.of(), List.of());
            var synth = ai.agentican.framework.orchestration.model.PlanStepAgent.of(
                    "synthesize", "worker", "synthesize",
                    List.of("sibling-a", "sibling-b", "sibling-c"), false, List.of(), List.of());

            var plan = ai.agentican.framework.orchestration.model.Plan.of(
                    "Parallel Resume", "test", List.of(),
                    List.of(siblingA, siblingB, siblingC, synth));

            agentican.plans().register(plan);

            var taskId = "t-" + ai.agentican.framework.util.Ids.generate();
            store.taskStarted(taskId, "Parallel Resume", plan, Map.of());

            var aStepId = ai.agentican.framework.util.Ids.generate();
            store.stepStarted(taskId, aStepId, "sibling-a");
            store.stepCompleted(taskId, aStepId, TaskStatus.COMPLETED, "A done");

            int handled = agentican.resumeInterrupted();
            assertEquals(1, handled);

            long deadline = System.currentTimeMillis() + 5000;
            while (System.currentTimeMillis() < deadline) {
                var loaded = store.load(taskId);
                if (loaded != null && loaded.status() == TaskStatus.COMPLETED) break;
                Thread.sleep(50);
            }

            var finalLog = store.load(taskId);
            assertEquals(TaskStatus.COMPLETED, finalLog.status(),
                    "Parallel-resume should reach COMPLETED via the runSeeded dispatch loop");

            assertEquals(TaskStatus.COMPLETED, finalLog.step("sibling-a").status());
            assertEquals(TaskStatus.COMPLETED, finalLog.step("sibling-b").status());
            assertEquals(TaskStatus.COMPLETED, finalLog.step("sibling-c").status());
            assertEquals(TaskStatus.COMPLETED, finalLog.step("synthesize").status());

            assertEquals(0, finalLog.step("sibling-a").runs().size(),
                    "Already-completed step must NOT be re-dispatched (zero new runs after resume)");
            assertTrue(finalLog.step("sibling-b").runs().size() >= 1);
            assertTrue(finalLog.step("sibling-c").runs().size() >= 1);
        }
    }

    @Test
    void resumeInterruptedClassifiesAndReaps() {

        var config = RuntimeConfig.builder()
                .llm(LlmConfig.builder().apiKey("mock").build())
                .build();

        try (var agentican = Agentican.builder()
                .config(config)
                .llm("default", request -> endTurn("ok"))
                .build()) {

            var store = agentican.taskStateStore();
            var taskId = "interrupted-" + ai.agentican.framework.util.Ids.generate();
            store.taskStarted(taskId, "mid-step-crash", null, Map.of());
            var stepId = "step-" + ai.agentican.framework.util.Ids.generate();
            store.stepStarted(taskId, stepId, "working-step");

            var handled = agentican.resumeInterrupted();

            assertEquals(1, handled);

            var reloaded = store.load(taskId);
            assertEquals(TaskStatus.FAILED, reloaded.status(),
                    "In v1, resumeInterrupted falls back to reap while drive-forward is implemented in PR 5");
        }
    }

    @Test
    void reapOrphansLeavesSubTasksToParent() {

        var config = RuntimeConfig.builder()
                .llm(LlmConfig.builder().apiKey("mock").build())
                .build();

        try (var agentican = Agentican.builder()
                .config(config)
                .llm("default", request -> endTurn("ok"))
                .build()) {

            var store = agentican.taskStateStore();
            var parentId = "parent-" + ai.agentican.framework.util.Ids.generate();
            var childId = "child-" + ai.agentican.framework.util.Ids.generate();
            var stepId = "s-" + ai.agentican.framework.util.Ids.generate();

            store.taskStarted(parentId, "parent", null, Map.of());
            store.stepStarted(parentId, stepId, "loop-step");
            store.taskStarted(childId, "iter-0", null, Map.of(), parentId, stepId, 0);

            var reaped = agentican.reapOrphans();

            assertEquals(1, reaped, "Only the parent is counted in the reap total; sub-tasks cascade");
            assertEquals(TaskStatus.FAILED, store.load(parentId).status());
            assertEquals(TaskStatus.FAILED, store.load(childId).status(),
                    "Sub-task cascades to FAILED when its parent is reaped — prevents orphan RUNNING sub-task rows");
        }
    }

    @Test
    void resumeBranchStepUsesExistingCompletedChildWithoutReDispatch() throws Exception {

        // Covers gap #13: when the branch-step's chosen child sub-task is already COMPLETED,
        // resume must reuse its output — not re-dispatch the path body.
        var llmCallCount = new java.util.concurrent.atomic.AtomicInteger(0);

        var mockLlm = new MockLlmClient()
                .onSendRepeated("should-never-call", endTurn("would be wrong"));

        var config = RuntimeConfig.builder()
                .llm(LlmConfig.builder().apiKey("mock").build())
                .build();

        try (var agentican = Agentican.builder()
                .config(config)
                .llm("default", request -> {
                    llmCallCount.incrementAndGet();
                    return mockLlm.toLlmClient().send(request);
                })
                .agent(AgentConfig.forCatalog("worker", "worker", "Worker", null))
                .build()) {

            var store = agentican.taskStateStore();

            var pathBodyStep = ai.agentican.framework.orchestration.model.PlanStepAgent.of(
                    "path-body", "worker", "do path", List.of(), false, List.of(), List.of());
            var sourceForBranch = ai.agentican.framework.orchestration.model.PlanStepAgent.of(
                    "source", "worker", "produce", List.of(), false, List.of(), List.of());
            var branch = ai.agentican.framework.orchestration.model.PlanStepBranch.of(
                    "choose", "source",
                    List.of(new ai.agentican.framework.orchestration.model.PlanStepBranch.Path(
                            "A", List.of(pathBodyStep))),
                    "A", List.of(), false);

            var plan = ai.agentican.framework.orchestration.model.Plan.of(
                    "Branch Resume", "test", List.of(), List.of(sourceForBranch, branch));

            agentican.plans().register(plan);

            var taskId = "t-branch-" + ai.agentican.framework.util.Ids.generate();
            var stepId = "s-" + ai.agentican.framework.util.Ids.generate();
            var childId = "c-" + ai.agentican.framework.util.Ids.generate();
            var childStepId = "cs-" + ai.agentican.framework.util.Ids.generate();

            store.taskStarted(taskId, "Branch Resume", plan, Map.of());

            var sourceStepId = "src-" + ai.agentican.framework.util.Ids.generate();
            store.stepStarted(taskId, sourceStepId, "source");
            store.stepCompleted(taskId, sourceStepId, TaskStatus.COMPLETED, "source-output");

            store.stepStarted(taskId, stepId, "choose");
            store.branchPathChosen(taskId, stepId, "A");

            // Pre-seed a COMPLETED child sub-task for the chosen path.
            var childPlan = ai.agentican.framework.orchestration.model.Plan.of(
                    "choose-A", "", List.of(), List.of(pathBodyStep));
            store.taskStarted(childId, "choose-A", childPlan, Map.of(), taskId, stepId, 0);
            store.stepStarted(childId, childStepId, "path-body");
            store.stepCompleted(childId, childStepId, TaskStatus.COMPLETED, "prerecorded path output");
            store.taskCompleted(childId, TaskStatus.COMPLETED);

            var before = llmCallCount.get();

            int handled = agentican.resumeInterrupted();
            assertEquals(1, handled);

            long deadline = System.currentTimeMillis() + 5000;
            while (System.currentTimeMillis() < deadline) {
                var loaded = store.load(taskId);
                if (loaded != null && loaded.status() == TaskStatus.COMPLETED) break;
                Thread.sleep(50);
            }

            var finalLog = store.load(taskId);
            assertEquals(TaskStatus.COMPLETED, finalLog.status(),
                    "Branch-resume should complete the parent task by reusing the existing child output");
            assertEquals(before, llmCallCount.get(),
                    "No LLM call should be made — the existing completed child output is reused verbatim");
        }
    }

    @Test
    void resumeLoopStepSkipsCompletedIterations() throws Exception {

        // Covers gap #14: when a loop step has a completed iteration 0 and missing iteration 1,
        // resume must preserve iter-0's output and dispatch iter-1 fresh — never re-run iter-0.
        var llmCallCount = new java.util.concurrent.atomic.AtomicInteger(0);

        var mockLlm = new MockLlmClient()
                .onSendRepeated("iter-body", endTurn("iter-1 fresh output"));

        var config = RuntimeConfig.builder()
                .llm(LlmConfig.builder().apiKey("mock").build())
                .build();

        try (var agentican = Agentican.builder()
                .config(config)
                .llm("default", request -> {
                    llmCallCount.incrementAndGet();
                    return mockLlm.toLlmClient().send(request);
                })
                .agent(AgentConfig.forCatalog("worker", "worker", "Worker", null))
                .build()) {

            var store = agentican.taskStateStore();

            var source = ai.agentican.framework.orchestration.model.PlanStepAgent.of(
                    "source", "worker", "produce items", List.of(), false, List.of(), List.of());

            var bodyStep = ai.agentican.framework.orchestration.model.PlanStepAgent.of(
                    "iter-body", "worker", "iter-body", List.of(), false, List.of(), List.of());

            var loop = new ai.agentican.framework.orchestration.model.PlanStepLoop(
                    "each", "source", List.of(bodyStep), List.of(), false);

            var plan = ai.agentican.framework.orchestration.model.Plan.of(
                    "Loop Resume", "test", List.of(), List.of(source, loop));

            agentican.plans().register(plan);

            var taskId = "t-loop-" + ai.agentican.framework.util.Ids.generate();
            var sourceStepId = "src-" + ai.agentican.framework.util.Ids.generate();
            var loopStepId = "loop-" + ai.agentican.framework.util.Ids.generate();
            var iter0Id = "i0-" + ai.agentican.framework.util.Ids.generate();
            var iter0StepId = "i0s-" + ai.agentican.framework.util.Ids.generate();

            store.taskStarted(taskId, "Loop Resume", plan, Map.of());

            store.stepStarted(taskId, sourceStepId, "source");
            store.stepCompleted(taskId, sourceStepId, TaskStatus.COMPLETED,
                    "[\"a\",\"b\"]");

            store.stepStarted(taskId, loopStepId, "each");

            // Pre-seed iter-0 as COMPLETED, iter-1 absent.
            var iterPlan = ai.agentican.framework.orchestration.model.Plan.of(
                    "each-iter-1", "", List.of(), List.of(bodyStep));
            store.taskStarted(iter0Id, "each-iter-1", iterPlan, Map.of(), taskId, loopStepId, 0);
            store.stepStarted(iter0Id, iter0StepId, "iter-body");
            store.stepCompleted(iter0Id, iter0StepId, TaskStatus.COMPLETED, "iter-0 prerecorded");
            store.taskCompleted(iter0Id, TaskStatus.COMPLETED);

            var before = llmCallCount.get();

            int handled = agentican.resumeInterrupted();
            assertEquals(1, handled);

            long deadline = System.currentTimeMillis() + 5000;
            while (System.currentTimeMillis() < deadline) {
                var loaded = store.load(taskId);
                if (loaded != null && loaded.status() == TaskStatus.COMPLETED) break;
                Thread.sleep(50);
            }

            var finalLog = store.load(taskId);
            assertEquals(TaskStatus.COMPLETED, finalLog.status(),
                    "Loop-resume should complete after dispatching only the missing iteration");

            // iter-0 child sub-task must remain COMPLETED with its prerecorded output — NOT re-run.
            var iter0Log = store.load(iter0Id);
            assertEquals(TaskStatus.COMPLETED, iter0Log.status());
            assertEquals("iter-0 prerecorded", iter0Log.step("iter-body").output(),
                    "Completed iteration output must be preserved verbatim — iter-0 was not re-run");

            // Exactly one LLM call: the missing iter-1. iter-0 must NOT be re-run.
            assertEquals(before + 1, llmCallCount.get(),
                    "Exactly one LLM call expected — for the missing iteration only");
        }
    }

    @Test
    void resumeSuspendedStepWithRejectedStepOutputMarksTaskFailedWithFeedback() throws Exception {

        // Covers gap #11 (full integration): SUSPENDED step with persisted rejected STEP_OUTPUT
        // HITL response — resumeSuspendedAgentStep's rejection shortcut must mark the step FAILED
        // with the feedback text, without invoking the LLM.

        var mockLlm = new MockLlmClient();  // zero entries: throws if invoked

        var config = RuntimeConfig.builder()
                .llm(LlmConfig.builder().apiKey("mock").build())
                .build();

        try (var agentican = Agentican.builder()
                .config(config)
                .llm("default", mockLlm.toLlmClient())
                .agent(AgentConfig.forCatalog("worker", "worker", "Worker", null))
                .build()) {

            var store = agentican.taskStateStore();

            var step = ai.agentican.framework.orchestration.model.PlanStepAgent.of(
                    "review", "worker", "review draft", List.of(), true, List.of(), List.of());
            var plan = ai.agentican.framework.orchestration.model.Plan.of(
                    "Rejected-Output Resume", "test", List.of(), List.of(step));

            agentican.plans().register(plan);

            var taskId = "t-rej-" + ai.agentican.framework.util.Ids.generate();
            var stepId = "s-" + ai.agentican.framework.util.Ids.generate();
            var runId = ai.agentican.framework.util.Ids.generate();
            var turnId = ai.agentican.framework.util.Ids.generate();

            store.taskStarted(taskId, "Rejected-Output Resume", plan, Map.of());
            store.stepStarted(taskId, stepId, "review");
            store.runStarted(taskId, stepId, runId, "worker");
            store.turnStarted(taskId, runId, turnId);
            store.messageSent(taskId, turnId,
                    new ai.agentican.framework.llm.LlmRequest("sys", null, "u", List.of(), 0, "d", "a", "c"));
            store.responseReceived(taskId, turnId,
                    new ai.agentican.framework.llm.LlmResponse("draft", List.of(),
                            ai.agentican.framework.llm.StopReason.END_TURN, 1, 1, 0, 0, 0));
            store.turnCompleted(taskId, turnId);

            var checkpoint = new ai.agentican.framework.hitl.HitlCheckpoint(
                    ai.agentican.framework.util.Ids.generate(),
                    ai.agentican.framework.hitl.HitlCheckpointType.STEP_OUTPUT,
                    "review", "Step output: review", "draft");
            store.hitlNotified(taskId, stepId, checkpoint);
            store.hitlResponded(taskId, stepId, HitlResponse.reject("needs more polish"));
            store.stepCompleted(taskId, stepId, TaskStatus.SUSPENDED, "draft");

            int handled = agentican.resumeInterrupted();
            assertEquals(1, handled);

            long deadline = System.currentTimeMillis() + 5000;
            while (System.currentTimeMillis() < deadline) {
                var loaded = store.load(taskId);
                if (loaded != null && loaded.status() != null) break;
                Thread.sleep(50);
            }

            var finalLog = store.load(taskId);
            assertEquals(TaskStatus.FAILED, finalLog.status(),
                    "Rejected STEP_OUTPUT on resume must drive the task to FAILED");
            assertEquals(TaskStatus.FAILED, finalLog.step("review").status());
            assertNotNull(finalLog.step("review").output());
            assertTrue(finalLog.step("review").output().contains("needs more polish"),
                    "Rejection feedback must be surfaced in the step output");
        }
    }

    @Test
    void agentMissingExternalIdFailsAtBoot() {

        var config = RuntimeConfig.builder()
                .llm(LlmConfig.builder().apiKey("mock").build())
                .build();

        var builder = Agentican.builder()
                .config(config)
                .llm("default", request -> endTurn("ok"))
                .agent(AgentConfig.of("Nameless", "role", null));

        assertThrows(IllegalStateException.class, builder::build);
    }
}
