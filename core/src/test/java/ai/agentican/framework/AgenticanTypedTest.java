package ai.agentican.framework;

import ai.agentican.framework.config.AgentConfig;
import ai.agentican.framework.config.LlmConfig;
import ai.agentican.framework.orchestration.execution.WorkflowRunStatus;
import ai.agentican.framework.orchestration.model.WorkflowDefinition;
import ai.agentican.framework.orchestration.model.WorkflowParam;
import ai.agentican.framework.orchestration.model.WorkflowStepAgent;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static ai.agentican.framework.MockLlmClient.endTurn;
import static org.junit.jupiter.api.Assertions.*;

class AgenticanTypedTest {

    record TriageParams(String customerId, String priority) {}

    @Test
    void capturedAgenticanRunsWithTypedParams() {

        var mockLlm = new MockLlmClient()
                .onSend("Triage customer cust-42 priority HIGH", "Triaged");

        try (var runtime = Agentican.builder()

                .configuration().api()
                    .llm(LlmConfig.builder().apiKey("mock").build())
                    .end()
                .llm("default", mockLlm.toLlmClient())
                .registry().api()
                    .agent(AgentConfig.builder()
                        .name("triage").id("triage")
                        .role("Triage agent").llm("default").build())
                    .end()
                .build()) {

            var plan = WorkflowDefinition.builder("triage", "triage")
                    .param(new WorkflowParam("customer_id", "Customer ID", null, true))
                    .param(new WorkflowParam("priority", "Priority", "NORMAL", false))
                    .step(WorkflowStepAgent.builder("classify")
                            .agent("triage")
                            .instructions("Triage customer {{param.customer_id}} priority {{param.priority}}")
                            .build())
                    .build();

            Workflow<TriageParams, Void> triage = runtime.workflow(plan).input(TriageParams.class).build();

            var result = triage.start(new TriageParams("cust-42", "HIGH")).untypedResult();

            assertEquals(WorkflowRunStatus.COMPLETED, result.status());
        }
    }

    @Test
    void resolvingAgenticanLooksUpByName() {

        var mockLlm = new MockLlmClient()
                .onSend("Triage cust-99 priority NORMAL", "OK");

        try (var runtime = Agentican.builder()

                .configuration().api()
                    .llm(LlmConfig.builder().apiKey("mock").build())
                    .end()
                .llm("default", mockLlm.toLlmClient())
                .registry().api()
                    .agent(AgentConfig.builder()
                        .name("triage").id("triage")
                        .role("Triage agent").llm("default").build())
                    .end()
                .build()) {

            var plan = WorkflowDefinition.builder("triage-by-name", "triage-by-name")
                    .param(new WorkflowParam("customer_id", "Customer ID", null, true))
                    .param(new WorkflowParam("priority", "Priority", "NORMAL", false))
                    .step(WorkflowStepAgent.builder("classify")
                            .agent("triage")
                            .instructions("Triage {{param.customer_id}} priority {{param.priority}}")
                            .build())
                    .build();

            runtime.registry().workflows().register(plan);

            Workflow<TriageParams, Void> triage = runtime.workflow("triage-by-name").input(TriageParams.class).build();

            var result = triage.start(new TriageParams("cust-99", "NORMAL")).untypedResult();
            assertEquals(WorkflowRunStatus.COMPLETED, result.status());
        }
    }

    @Test
    void resolvingAgenticanFailsWhenPlanMissing() {

        try (var runtime = Agentican.builder()

                .configuration().api()
                    .llm(LlmConfig.builder().apiKey("mock").build())
                    .end()
                .llm("default", request -> endTurn("ok"))
                .build()) {

            assertThrows(IllegalStateException.class,
                    () -> runtime.workflow("nonexistent").input(TriageParams.class).build());
        }
    }

    @Test
    void runtimeRegisteredPlanIsVisibleToResolvingInvoker() {

        var mockLlm = new MockLlmClient()
                .onSend("Late-registered run with cust-7", "OK");

        try (var runtime = Agentican.builder()

                .configuration().api()
                    .llm(LlmConfig.builder().apiKey("mock").build())
                    .end()
                .llm("default", mockLlm.toLlmClient())
                .registry().api()
                    .agent(AgentConfig.builder()
                        .name("triage").id("triage")
                        .role("Triage agent").llm("default").build())
                    .end()
                .build()) {

            var plan = WorkflowDefinition.builder("late-definition", "late-definition")
                    .param(new WorkflowParam("customer_id", "Customer ID", null, true))
                    .param(new WorkflowParam("priority", "Priority", "NORMAL", false))
                    .step(WorkflowStepAgent.builder("classify")
                            .agent("triage")
                            .instructions("Late-registered run with {{param.customer_id}}")
                            .build())
                    .build();

            runtime.registry().workflows().register(plan);

            Workflow<TriageParams, Void> triage = runtime.workflow("late-definition").input(TriageParams.class).build();

            var result = triage.start(new TriageParams("cust-7", "NORMAL")).untypedResult();
            assertEquals(WorkflowRunStatus.COMPLETED, result.status());
        }
    }

    @Test
    void voidParamsUseNoArgRun() {

        var mockLlm = new MockLlmClient().onSend("Run without params", "OK");

        try (var runtime = Agentican.builder()

                .configuration().api()
                    .llm(LlmConfig.builder().apiKey("mock").build())
                    .end()
                .llm("default", mockLlm.toLlmClient())
                .registry().api()
                    .agent(AgentConfig.builder()
                        .name("noparam").id("noparam")
                        .role("Agent").llm("default").build())
                    .end()
                .build()) {

            var plan = WorkflowDefinition.builder("no-params", "no-params")
                    .step(WorkflowStepAgent.builder("do")
                            .agent("noparam")
                            .instructions("Run without params")
                            .build())
                    .build();

            Workflow<Void, Void> invoker = runtime.workflow(plan).input(Void.class).build();

            var result = invoker.start().untypedResult();
            assertEquals(WorkflowRunStatus.COMPLETED, result.status());
        }
    }

    @Test
    void mapParamsArePassedThrough() {

        var mockLlm = new MockLlmClient().onSend("Map-based cust-m1", "OK");

        try (var runtime = Agentican.builder()

                .configuration().api()
                    .llm(LlmConfig.builder().apiKey("mock").build())
                    .end()
                .llm("default", mockLlm.toLlmClient())
                .registry().api()
                    .agent(AgentConfig.builder()
                        .name("triage").id("triage")
                        .role("Agent").llm("default").build())
                    .end()
                .build()) {

            var plan = WorkflowDefinition.builder("map-definition", "map-definition")
                    .param(new WorkflowParam("customer_id", "Customer ID", null, true))
                    .step(WorkflowStepAgent.builder("do")
                            .agent("triage")
                            .instructions("Map-based {{param.customer_id}}")
                            .build())
                    .build();

            @SuppressWarnings({"rawtypes", "unchecked"})
            Workflow<Map, Void> invoker = (Workflow<Map, Void>) (Workflow) runtime.workflow(plan).input(Map.class).build();

            var result = invoker.start(Map.of("customer_id", "cust-m1")).untypedResult();
            assertEquals(WorkflowRunStatus.COMPLETED, result.status());
        }
    }

    @Test
    void capturedAgenticanConvertsSnakeCaseFromCamelCaseRecord() {

        var mockLlm = new MockLlmClient().onSend("Snake params account-7", "OK");

        try (var runtime = Agentican.builder()

                .configuration().api()
                    .llm(LlmConfig.builder().apiKey("mock").build())
                    .end()
                .llm("default", mockLlm.toLlmClient())
                .registry().api()
                    .agent(AgentConfig.builder()
                        .name("acct").id("acct")
                        .role("Agent").llm("default").build())
                    .end()
                .build()) {

            record AccountParams(String accountId) {}

            var plan = WorkflowDefinition.builder("acct-definition", "acct-definition")
                    .param(new WorkflowParam("account_id", "Account ID", null, true))
                    .step(WorkflowStepAgent.builder("do")
                            .agent("acct")
                            .instructions("Snake params {{param.account_id}}")
                            .build())
                    .build();

            Workflow<AccountParams, Void> invoker = runtime.workflow(plan).input(AccountParams.class).build();

            var result = invoker.start(new AccountParams("account-7")).untypedResult();
            assertEquals(WorkflowRunStatus.COMPLETED, result.status());
        }
    }

    record TriageOutput(String classification, String reason) {}

    @Test
    void typedOutputDeserializesAgentJsonResponse() {

        var mockLlm = new MockLlmClient()
                .onSend("Respond JSON for cust-99",
                        "{\"classification\":\"refund\",\"reason\":\"order arrived broken\"}");

        try (var runtime = Agentican.builder()

                .configuration().api()
                    .llm(LlmConfig.builder().apiKey("mock").build())
                    .end()
                .llm("default", mockLlm.toLlmClient())
                .registry().api()
                    .agent(AgentConfig.builder()
                        .name("triage").id("triage")
                        .role("Triage agent").llm("default").build())
                    .end()
                .build()) {

            var plan = WorkflowDefinition.builder("typed-triage", "typed-triage")
                    .param(new WorkflowParam("customer_id", "Customer ID", null, true))
                    .step(WorkflowStepAgent.builder("classify")
                            .agent("triage")
                            .instructions("Respond JSON for {{param.customer_id}}")
                            .build())
                    .build();

            Workflow<TriageParams, TriageOutput> triage =
                    runtime.workflow(plan).input(TriageParams.class).output(TriageOutput.class).build();

            TriageOutput out = triage.start(new TriageParams("cust-99", "NORMAL")).await();

            assertEquals("refund", out.classification());
            assertEquals("order arrived broken", out.reason());
        }
    }

    @Test
    void typedOutputThrowsOnInvalidJson() {

        var mockLlm = new MockLlmClient()
                .onSend("respond", "this is not JSON");

        try (var runtime = Agentican.builder()

                .configuration().api()
                    .llm(LlmConfig.builder().apiKey("mock").build())
                    .end()
                .llm("default", mockLlm.toLlmClient())
                .registry().api()
                    .agent(AgentConfig.builder()
                        .name("triage").id("triage")
                        .role("Agent").llm("default").build())
                    .end()
                .build()) {

            var plan = WorkflowDefinition.builder("bad-output", "bad-output")
                    .step(WorkflowStepAgent.builder("classify")
                            .agent("triage")
                            .instructions("respond")
                            .build())
                    .build();

            Workflow<Void, TriageOutput> triage =
                    runtime.workflow(plan).input(Void.class).output(TriageOutput.class).build();

            assertThrows(WorkflowOutputException.class, () -> triage.start().await());
        }
    }

    @Test
    void multiStepPlanWithoutOutputStepFailsAtConstruction() {

        try (var runtime = Agentican.builder()

                .configuration().api()
                    .llm(LlmConfig.builder().apiKey("mock").build())
                    .end()
                .llm("default", request -> endTurn("ok"))
                .registry().api()
                    .agent(AgentConfig.builder()
                        .name("triage").id("triage")
                        .role("Agent").llm("default").build())
                    .end()
                .build()) {

            var plan = WorkflowDefinition.builder("multi", "multi")
                    .step(WorkflowStepAgent.builder("a").agent("triage").instructions("a").build())
                    .step(WorkflowStepAgent.builder("b").agent("triage").instructions("b")
                            .dependencies(java.util.List.of("a")).build())
                    .build();

            assertThrows(IllegalStateException.class,
                    () -> runtime.workflow(plan).input(Void.class).output(TriageOutput.class).build());
        }
    }

    @Test
    void typedOutputInjectsSchemaIntoSystemPrompt() {

        var capturedSystemPrompts = new java.util.concurrent.CopyOnWriteArrayList<String>();

        var llmClient = (ai.agentican.framework.llm.LlmClient) request -> {
            capturedSystemPrompts.add(request.systemPrompt());
            return endTurn("{\"classification\":\"ok\",\"reason\":\"done\"}");
        };

        try (var runtime = Agentican.builder()

                .configuration().api()
                    .llm(LlmConfig.builder().apiKey("mock").build())
                    .end()
                .llm("default", llmClient)
                .registry().api()
                    .agent(AgentConfig.builder()
                        .name("triage").id("triage")
                        .role("Triage agent").llm("default").build())
                    .end()
                .build()) {

            var plan = WorkflowDefinition.builder("schema-injection", "schema-injection")
                    .step(WorkflowStepAgent.builder("classify")
                            .agent("triage")
                            .instructions("Classify this")
                            .build())
                    .build();

            Workflow<Void, TriageOutput> triage =
                    runtime.workflow(plan).input(Void.class).output(TriageOutput.class).build();

            triage.start().await();

            assertFalse(capturedSystemPrompts.isEmpty());
            var prompt = capturedSystemPrompts.getFirst();
            assertTrue(prompt.contains("MUST be valid JSON"),
                    "Prompt should include the JSON-output instruction");
            assertFalse(prompt.contains("classification"),
                    "Schema content should NOT appear in the prompt — it's enforced via the native API field");
        }
    }

    @Test
    void noStructuredOutputWhenOutputTypeIsVoid() {

        var capturedSystemPrompts = new java.util.concurrent.CopyOnWriteArrayList<String>();

        var llmClient = (ai.agentican.framework.llm.LlmClient) request -> {
            capturedSystemPrompts.add(request.systemPrompt());
            return endTurn("plain text response");
        };

        try (var runtime = Agentican.builder()

                .configuration().api()
                    .llm(LlmConfig.builder().apiKey("mock").build())
                    .end()
                .llm("default", llmClient)
                .registry().api()
                    .agent(AgentConfig.builder()
                        .name("triage").id("triage")
                        .role("Agent").llm("default").build())
                    .end()
                .build()) {

            var plan = WorkflowDefinition.builder("no-schema", "no-schema")
                    .step(WorkflowStepAgent.builder("do").agent("triage").instructions("go").build())
                    .build();

            Workflow<Void, Void> invoker = runtime.workflow(plan).input(Void.class).output(Void.class).build();
            invoker.start();

            Thread.yield();

            var result = invoker.start().untypedResult();
            assertEquals(WorkflowRunStatus.COMPLETED, result.status());

            var prompt = capturedSystemPrompts.getFirst();
            assertFalse(prompt.contains("MUST be valid JSON"),
                    "No JSON-output instruction when output type is Void");
        }
    }

    @Test
    void multiStepPlanWithOutputStepResolvesCleanly() {

        var mockLlm = new MockLlmClient()
                .onSend("a", "first")
                .onSend("b", "{\"classification\":\"x\",\"reason\":\"y\"}");

        try (var runtime = Agentican.builder()

                .configuration().api()
                    .llm(LlmConfig.builder().apiKey("mock").build())
                    .end()
                .llm("default", mockLlm.toLlmClient())
                .registry().api()
                    .agent(AgentConfig.builder()
                        .name("triage").id("triage")
                        .role("Agent").llm("default").build())
                    .end()
                .build()) {

            var plan = WorkflowDefinition.builder("multi", "multi")
                    .outputStep("b")
                    .step(WorkflowStepAgent.builder("a").agent("triage").instructions("a").build())
                    .step(WorkflowStepAgent.builder("b").agent("triage").instructions("b")
                            .dependencies(java.util.List.of("a")).build())
                    .build();

            Workflow<Void, TriageOutput> triage =
                    runtime.workflow(plan).input(Void.class).output(TriageOutput.class).build();

            TriageOutput out = triage.start().await();
            assertEquals("x", out.classification());
        }
    }
}
