package ai.agentican.framework.invoker;

import ai.agentican.framework.Agentican;
import ai.agentican.framework.MockLlmClient;
import ai.agentican.framework.config.AgentConfig;
import ai.agentican.framework.config.LlmConfig;
import ai.agentican.framework.orchestration.execution.TaskStatus;

import org.junit.jupiter.api.Test;

import static ai.agentican.framework.MockLlmClient.endTurn;
import static org.junit.jupiter.api.Assertions.*;

class AgentInvokerBuilderTest {

    record CompetitorQuery(String name, String industry) {}

    record CompetitorBrief(String summary, String threatLevel) {}

    private static Agentican.Builder baseRuntime(MockLlmClient llm) {

        return Agentican.builder()
                .llm(LlmConfig.builder().apiKey("mock").build())
                .llm("default", llm.toLlmClient())
                .agent(AgentConfig.builder()
                        .externalId("agent.analyst.v1").name("Analyst")
                        .role("Competitive research analyst").llm("default").build());
    }

    @Test
    void invokeWithTypedInputAndOutput() {

        var llm = new MockLlmClient()
                .onSend("Research Stripe in payments",
                        "{\"summary\":\"leading payments platform\",\"threatLevel\":\"HIGH\"}");

        try (var runtime = baseRuntime(llm).build()) {

            var analyst = runtime.agentTask("test").agent("Analyst")
                    .input(CompetitorQuery.class)
                    .output(CompetitorBrief.class)
                    .instructions("Research {{param.name}} in {{param.industry}}")
                    .build();

            var brief = analyst.runAndAwait(new CompetitorQuery("Stripe", "payments"));

            assertEquals("leading payments platform", brief.summary());
            assertEquals("HIGH", brief.threatLevel());
        }
    }

    @Test
    void nestedParamFieldResolvesInInstructions() {

        record Address(String city) {}
        record UserQuery(String name, Address address) {}

        var llm = new MockLlmClient()
                .onSend("Look up Alice in Austin", "{\"summary\":\"ok\",\"threatLevel\":\"LOW\"}");

        try (var runtime = baseRuntime(llm).build()) {

            var invoker = runtime.agentTask("test").agent("Analyst")
                    .input(UserQuery.class)
                    .output(CompetitorBrief.class)
                    .instructions("Look up {{param.name}} in {{param.address.city}}")
                    .build();

            var out = invoker.runAndAwait(new UserQuery("Alice", new Address("Austin")));

            assertEquals("ok", out.summary());
        }
    }

    @Test
    void unknownAgentThrowsOnBuild() {

        try (var runtime = baseRuntime(new MockLlmClient()).build()) {

            assertThrows(IllegalStateException.class, () -> runtime
                    .agentTask("test").agent("NoSuchAgent")
                    .input(CompetitorQuery.class)
                    .output(CompetitorBrief.class)
                    .instructions("irrelevant")
                    .build());
        }
    }

    @Test
    void missingInstructionsThrowsOnBuild() {

        try (var runtime = baseRuntime(new MockLlmClient()).build()) {

            assertThrows(IllegalStateException.class, () -> runtime
                    .agentTask("test").agent("Analyst")
                    .input(CompetitorQuery.class)
                    .output(CompetitorBrief.class)
                    .build());
        }
    }

    @Test
    void voidInputBuildsAndInvokes() {

        var llm = new MockLlmClient()
                .onSend("Summarize today",
                        "{\"summary\":\"a summary\",\"threatLevel\":\"NONE\"}");

        try (var runtime = baseRuntime(llm).build()) {

            var invoker = runtime.agentTask("test").agent("Analyst")
                    .input(Void.class)
                    .output(CompetitorBrief.class)
                    .instructions("Summarize today")
                    .build();

            var brief = invoker.runAndAwait();
            assertEquals("a summary", brief.summary());
        }
    }

    @Test
    void stringOutputBypassesStructuredOutput() {

        var llm = new MockLlmClient().onSend("Write a note", "free-form response text");

        try (var runtime = baseRuntime(llm).build()) {

            var invoker = runtime.agentTask("test").agent("Analyst")
                    .input(Void.class)
                    .output(String.class)
                    .instructions("Write a note")
                    .build();

            assertEquals("free-form response text", invoker.runAndAwait());
        }
    }

    @Test
    void voidOutputUsesNoArgAwait() {

        var llm = new MockLlmClient().onSend("Do something", "ok");

        try (var runtime = baseRuntime(llm).build()) {

            var invoker = runtime.agentTask("test").agent("Analyst")
                    .input(Void.class)
                    .output(Void.class)
                    .instructions("Do something")
                    .build();

            var result = invoker.awaitTaskResult();
            assertEquals(TaskStatus.COMPLETED, result.status());
        }
    }

    @Test
    void persistRegistersPlan() {

        var llm = new MockLlmClient().onSend("noop", "ok");

        try (var runtime = baseRuntime(llm).build()) {

            runtime.agentTask("analyst-oneshot").agent("Analyst")
                    .input(Void.class)
                    .output(Void.class)
                    .instructions("noop")
                    .persist()
                    .build();

            assertNotNull(runtime.registry().plans().get("analyst-oneshot"),
                    "Plan should be visible in the plan registry after persist()");
        }
    }

    @Test
    void structuredOutputInjectsJsonInstruction() {

        var capturedSystemPrompts = new java.util.concurrent.CopyOnWriteArrayList<String>();

        ai.agentican.framework.llm.LlmClient llm = request -> {
            capturedSystemPrompts.add(request.systemPrompt());
            return endTurn("{\"summary\":\"x\",\"threatLevel\":\"LOW\"}");
        };

        try (var runtime = Agentican.builder()
                .llm(LlmConfig.builder().apiKey("mock").build())
                .llm("default", llm)
                .agent(AgentConfig.builder()
                        .externalId("agent.analyst.v1").name("Analyst")
                        .role("Analyst").llm("default").build())
                .build()) {

            var invoker = runtime.agentTask("test").agent("Analyst")
                    .input(Void.class)
                    .output(CompetitorBrief.class)
                    .instructions("Do it")
                    .build();

            invoker.runAndAwait();

            assertFalse(capturedSystemPrompts.isEmpty());
            assertTrue(capturedSystemPrompts.getFirst().contains("MUST be valid JSON"),
                    "Structured-output prompt should be applied when O is a record");
        }
    }

    @Test
    void toolsAreAttachedToStep() {

        var llm = new MockLlmClient().onSend("Look it up", "{\"summary\":\"done\",\"threatLevel\":\"LOW\"}");

        try (var runtime = baseRuntime(llm).build()) {

            var invoker = runtime.agentTask("analyst-with-tools").agent("Analyst")
                    .input(Void.class)
                    .output(CompetitorBrief.class)
                    .instructions("Look it up")
                    .tools("search_web", "fetch_url")
                    .persist()
                    .build();

            var plan = runtime.registry().plans().get("analyst-with-tools");
            var step = (ai.agentican.framework.orchestration.model.PlanStepAgent) plan.steps().getFirst();

            assertEquals(java.util.List.of("search_web", "fetch_url"), step.tools());
        }
    }

    @Test
    void skillsAreAttachedToStep() {

        var llm = new MockLlmClient().onSend("Do", "{\"summary\":\"ok\",\"threatLevel\":\"LOW\"}");

        try (var runtime = Agentican.builder()
                .llm(LlmConfig.builder().apiKey("mock").build())
                .llm("default", llm.toLlmClient())
                .agent(AgentConfig.builder()
                        .externalId("agent.analyst.v1").name("Analyst")
                        .role("Analyst").llm("default").build())
                .skill(ai.agentican.framework.config.SkillConfig.builder()
                        .externalId("skill.tone.v1").name("Tone").instructions("Be terse").build())
                .build()) {

            var invoker = runtime.agentTask("analyst-with-skills").agent("Analyst")
                    .input(Void.class)
                    .output(CompetitorBrief.class)
                    .instructions("Do")
                    .skills("Tone")
                    .persist()
                    .build();

            var plan = runtime.registry().plans().get("analyst-with-skills");
            var step = (ai.agentican.framework.orchestration.model.PlanStepAgent) plan.steps().getFirst();

            assertEquals(java.util.List.of("Tone"), step.skills());
        }
    }
}
