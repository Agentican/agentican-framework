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
