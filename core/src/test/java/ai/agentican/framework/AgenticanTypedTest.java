package ai.agentican.framework;

import ai.agentican.framework.config.AgentConfig;
import ai.agentican.framework.config.LlmConfig;
import ai.agentican.framework.orchestration.execution.TaskStatus;
import ai.agentican.framework.orchestration.model.Plan;
import ai.agentican.framework.orchestration.model.PlanParam;
import ai.agentican.framework.orchestration.model.PlanStepAgent;
import ai.agentican.framework.invoker.AgenticanTask;
import ai.agentican.framework.invoker.OutputParseException;

import org.junit.jupiter.api.Test;

import java.util.List;
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
                .llm(LlmConfig.builder().apiKey("mock").build())
                .llm("default", mockLlm.toLlmClient())
                .agent(AgentConfig.builder()
                        .externalId("agent.triage.v1").name("triage")
                        .role("Triage agent").llm("default").build())
                .build()) {

            var plan = Plan.builder("triage")
                    .param(new PlanParam("customer_id", "Customer ID", null, true))
                    .param(new PlanParam("priority", "Priority", "NORMAL", false))
                    .step(PlanStepAgent.builder("classify")
                            .agent("triage")
                            .instructions("Triage customer {{param.customer_id}} priority {{param.priority}}")
                            .build())
                    .build();

            AgenticanTask<TriageParams, Void> triage = runtime.workflowTask("test").plan(plan).input(TriageParams.class).build();

            var result = triage.awaitTaskResult(new TriageParams("cust-42", "HIGH"));

            assertEquals(TaskStatus.COMPLETED, result.status());
        }
    }

    @Test
    void resolvingAgenticanLooksUpByName() {

        var mockLlm = new MockLlmClient()
                .onSend("Triage cust-99 priority NORMAL", "OK");

        try (var runtime = Agentican.builder()
                .llm(LlmConfig.builder().apiKey("mock").build())
                .llm("default", mockLlm.toLlmClient())
                .agent(AgentConfig.builder()
                        .externalId("agent.triage.v1").name("triage")
                        .role("Triage agent").llm("default").build())
                .build()) {

            var plan = Plan.builder("triage-by-name")
                    .param(new PlanParam("customer_id", "Customer ID", null, true))
                    .param(new PlanParam("priority", "Priority", "NORMAL", false))
                    .step(PlanStepAgent.builder("classify")
                            .agent("triage")
                            .instructions("Triage {{param.customer_id}} priority {{param.priority}}")
                            .build())
                    .build();

            runtime.registry().plans().register(plan);

            AgenticanTask<TriageParams, Void> triage = runtime.workflowTask("test").plan("triage-by-name").input(TriageParams.class).build();

            var result = triage.awaitTaskResult(new TriageParams("cust-99", "NORMAL"));
            assertEquals(TaskStatus.COMPLETED, result.status());
        }
    }

    @Test
    void resolvingAgenticanFailsWhenPlanMissing() {

        try (var runtime = Agentican.builder()
                .llm(LlmConfig.builder().apiKey("mock").build())
                .llm("default", request -> endTurn("ok"))
                .build()) {

            AgenticanTask<TriageParams, Void> triage = runtime.workflowTask("test").plan("nonexistent").input(TriageParams.class).build();

            assertThrows(IllegalStateException.class,
                    () -> triage.run(new TriageParams("cust-1", "NORMAL")));
        }
    }

    @Test
    void runtimeRegisteredPlanIsVisibleToResolvingInvoker() {

        var mockLlm = new MockLlmClient()
                .onSend("Late-registered run with cust-7", "OK");

        try (var runtime = Agentican.builder()
                .llm(LlmConfig.builder().apiKey("mock").build())
                .llm("default", mockLlm.toLlmClient())
                .agent(AgentConfig.builder()
                        .externalId("agent.triage.v1").name("triage")
                        .role("Triage agent").llm("default").build())
                .build()) {

            AgenticanTask<TriageParams, Void> triage = runtime.workflowTask("test").plan("late-plan").input(TriageParams.class).build();

            // Plan isn't registered yet — construction still succeeds (resolution is per-invoke)
            var plan = Plan.builder("late-plan")
                    .param(new PlanParam("customer_id", "Customer ID", null, true))
                    .param(new PlanParam("priority", "Priority", "NORMAL", false))
                    .step(PlanStepAgent.builder("classify")
                            .agent("triage")
                            .instructions("Late-registered run with {{param.customer_id}}")
                            .build())
                    .build();

            runtime.registry().plans().register(plan);

            var result = triage.awaitTaskResult(new TriageParams("cust-7", "NORMAL"));
            assertEquals(TaskStatus.COMPLETED, result.status());
        }
    }

    @Test
    void voidParamsUseNoArgRun() {

        var mockLlm = new MockLlmClient().onSend("Run without params", "OK");

        try (var runtime = Agentican.builder()
                .llm(LlmConfig.builder().apiKey("mock").build())
                .llm("default", mockLlm.toLlmClient())
                .agent(AgentConfig.builder()
                        .externalId("agent.noparam.v1").name("noparam")
                        .role("Agent").llm("default").build())
                .build()) {

            var plan = Plan.builder("no-params")
                    .step(PlanStepAgent.builder("do")
                            .agent("noparam")
                            .instructions("Run without params")
                            .build())
                    .build();

            AgenticanTask<Void, Void> invoker = runtime.workflowTask("test").plan(plan).input(Void.class).build();

            var result = invoker.awaitTaskResult();
            assertEquals(TaskStatus.COMPLETED, result.status());
        }
    }

    @Test
    void mapParamsArePassedThrough() {

        var mockLlm = new MockLlmClient().onSend("Map-based cust-m1", "OK");

        try (var runtime = Agentican.builder()
                .llm(LlmConfig.builder().apiKey("mock").build())
                .llm("default", mockLlm.toLlmClient())
                .agent(AgentConfig.builder()
                        .externalId("agent.triage.v1").name("triage")
                        .role("Agent").llm("default").build())
                .build()) {

            var plan = Plan.builder("map-plan")
                    .param(new PlanParam("customer_id", "Customer ID", null, true))
                    .step(PlanStepAgent.builder("do")
                            .agent("triage")
                            .instructions("Map-based {{param.customer_id}}")
                            .build())
                    .build();

            @SuppressWarnings({"rawtypes", "unchecked"})
            AgenticanTask<Map, Void> invoker = (AgenticanTask<Map, Void>) (AgenticanTask) runtime.workflowTask("test").plan(plan).input(Map.class).build();

            var result = invoker.awaitTaskResult(Map.of("customer_id", "cust-m1"));
            assertEquals(TaskStatus.COMPLETED, result.status());
        }
    }

    @Test
    void capturedAgenticanConvertsSnakeCaseFromCamelCaseRecord() {

        var mockLlm = new MockLlmClient().onSend("Snake params account-7", "OK");

        try (var runtime = Agentican.builder()
                .llm(LlmConfig.builder().apiKey("mock").build())
                .llm("default", mockLlm.toLlmClient())
                .agent(AgentConfig.builder()
                        .externalId("agent.acct.v1").name("acct")
                        .role("Agent").llm("default").build())
                .build()) {

            record AccountParams(String accountId) {}

            var plan = Plan.builder("acct-plan")
                    .param(new PlanParam("account_id", "Account ID", null, true))
                    .step(PlanStepAgent.builder("do")
                            .agent("acct")
                            .instructions("Snake params {{param.account_id}}")
                            .build())
                    .build();

            AgenticanTask<AccountParams, Void> invoker = runtime.workflowTask("test").plan(plan).input(AccountParams.class).build();

            var result = invoker.awaitTaskResult(new AccountParams("account-7"));
            assertEquals(TaskStatus.COMPLETED, result.status());
        }
    }

    record TriageOutput(String classification, String reason) {}

    @Test
    void typedOutputDeserializesAgentJsonResponse() {

        var mockLlm = new MockLlmClient()
                .onSend("Respond JSON for cust-99",
                        "{\"classification\":\"refund\",\"reason\":\"order arrived broken\"}");

        try (var runtime = Agentican.builder()
                .llm(LlmConfig.builder().apiKey("mock").build())
                .llm("default", mockLlm.toLlmClient())
                .agent(AgentConfig.builder()
                        .externalId("agent.triage.v1").name("triage")
                        .role("Triage agent").llm("default").build())
                .build()) {

            var plan = Plan.builder("typed-triage")
                    .param(new PlanParam("customer_id", "Customer ID", null, true))
                    .step(PlanStepAgent.builder("classify")
                            .agent("triage")
                            .instructions("Respond JSON for {{param.customer_id}}")
                            .build())
                    .build();

            AgenticanTask<TriageParams, TriageOutput> triage =
                    runtime.workflowTask("test").plan(plan).input(TriageParams.class).output(TriageOutput.class).build();

            TriageOutput out = triage.runAndAwait(new TriageParams("cust-99", "NORMAL"));

            assertEquals("refund", out.classification());
            assertEquals("order arrived broken", out.reason());
        }
    }

    @Test
    void typedOutputThrowsOnInvalidJson() {

        var mockLlm = new MockLlmClient()
                .onSend("respond", "this is not JSON");

        try (var runtime = Agentican.builder()
                .llm(LlmConfig.builder().apiKey("mock").build())
                .llm("default", mockLlm.toLlmClient())
                .agent(AgentConfig.builder()
                        .externalId("agent.triage.v1").name("triage")
                        .role("Agent").llm("default").build())
                .build()) {

            var plan = Plan.builder("bad-output")
                    .step(PlanStepAgent.builder("classify")
                            .agent("triage")
                            .instructions("respond")
                            .build())
                    .build();

            AgenticanTask<Void, TriageOutput> triage =
                    runtime.workflowTask("test").plan(plan).input(Void.class).output(TriageOutput.class).build();

            assertThrows(OutputParseException.class, triage::runAndAwait);
        }
    }

    @Test
    void multiStepPlanWithoutOutputStepFailsAtConstruction() {

        try (var runtime = Agentican.builder()
                .llm(LlmConfig.builder().apiKey("mock").build())
                .llm("default", request -> endTurn("ok"))
                .agent(AgentConfig.builder()
                        .externalId("agent.triage.v1").name("triage")
                        .role("Agent").llm("default").build())
                .build()) {

            var plan = Plan.builder("multi")
                    .step(PlanStepAgent.builder("a").agent("triage").instructions("a").build())
                    .step(PlanStepAgent.builder("b").agent("triage").instructions("b")
                            .dependencies(java.util.List.of("a")).build())
                    .build();

            assertThrows(IllegalStateException.class,
                    () -> runtime.workflowTask("test").plan(plan).input(Void.class).output(TriageOutput.class).build());
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
                .llm(LlmConfig.builder().apiKey("mock").build())
                .llm("default", llmClient)
                .agent(AgentConfig.builder()
                        .externalId("agent.triage.v1").name("triage")
                        .role("Triage agent").llm("default").build())
                .build()) {

            var plan = Plan.builder("schema-injection")
                    .step(PlanStepAgent.builder("classify")
                            .agent("triage")
                            .instructions("Classify this")
                            .build())
                    .build();

            AgenticanTask<Void, TriageOutput> triage =
                    runtime.workflowTask("test").plan(plan).input(Void.class).output(TriageOutput.class).build();

            triage.runAndAwait();

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
                .llm(LlmConfig.builder().apiKey("mock").build())
                .llm("default", llmClient)
                .agent(AgentConfig.builder()
                        .externalId("agent.triage.v1").name("triage")
                        .role("Agent").llm("default").build())
                .build()) {

            var plan = Plan.builder("no-schema")
                    .step(PlanStepAgent.builder("do").agent("triage").instructions("go").build())
                    .build();

            AgenticanTask<Void, Void> invoker = runtime.workflowTask("test").plan(plan).input(Void.class).output(Void.class).build();
            invoker.run();

            Thread.yield();   // let the async task submit

            var result = invoker.awaitTaskResult();
            assertEquals(TaskStatus.COMPLETED, result.status());

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
                .llm(LlmConfig.builder().apiKey("mock").build())
                .llm("default", mockLlm.toLlmClient())
                .agent(AgentConfig.builder()
                        .externalId("agent.triage.v1").name("triage")
                        .role("Agent").llm("default").build())
                .build()) {

            var plan = Plan.builder("multi")
                    .outputStep("b")
                    .step(PlanStepAgent.builder("a").agent("triage").instructions("a").build())
                    .step(PlanStepAgent.builder("b").agent("triage").instructions("b")
                            .dependencies(java.util.List.of("a")).build())
                    .build();

            AgenticanTask<Void, TriageOutput> triage =
                    runtime.workflowTask("test").plan(plan).input(Void.class).output(TriageOutput.class).build();

            TriageOutput out = triage.runAndAwait();
            assertEquals("x", out.classification());
        }
    }
}
