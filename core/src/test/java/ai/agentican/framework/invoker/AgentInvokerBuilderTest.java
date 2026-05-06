package ai.agentican.framework;

import ai.agentican.framework.Agentican;
import ai.agentican.framework.MockLlmClient;
import ai.agentican.framework.config.AgentConfig;
import ai.agentican.framework.config.LlmConfig;
import ai.agentican.framework.orchestration.execution.WorkflowRunStatus;

import org.junit.jupiter.api.Test;

import static ai.agentican.framework.MockLlmClient.endTurn;
import static org.junit.jupiter.api.Assertions.*;

class AgentInvokerBuilderTest {

    record CompetitorQuery(String name, String industry) {}

    record CompetitorBrief(String summary, String threatLevel) {}

    private static Agentican.Builder baseRuntime(MockLlmClient llm) {

        return Agentican.builder()
                .configuration().api()
                    .llm(LlmConfig.builder().apiKey("mock").build())
                    .end()
                .llm("default", llm.toLlmClient())
                .registry().api()
                    .agent(AgentConfig.builder()
                            .name("Analyst").id("Analyst")
                            .role("Competitive research analyst").llm("default").build())
                    .end();
    }

    @Test
    void invokeWithTypedInputAndOutput() {

        var llm = new MockLlmClient()
                .onSend("Research Stripe in payments",
                        "{\"summary\":\"leading payments platform\",\"threatLevel\":\"HIGH\"}");

        try (var runtime = baseRuntime(llm).build()) {

            var analyst = runtime.task("test").agent("Analyst")
                    .instructions("Research {{param.name}} in {{param.industry}}")
                    .input(CompetitorQuery.class)
                    .output(CompetitorBrief.class)
                    .build();

            var brief = analyst.start(new CompetitorQuery("Stripe", "payments")).await();

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

            var invoker = runtime.task("test").agent("Analyst")
                    .instructions("Look up {{param.name}} in {{param.address.city}}")
                    .input(UserQuery.class)
                    .output(CompetitorBrief.class)
                    .build();

            var out = invoker.start(new UserQuery("Alice", new Address("Austin"))).await();

            assertEquals("ok", out.summary());
        }
    }

    @Test
    void unknownAgentThrowsOnBuild() {

        try (var runtime = baseRuntime(new MockLlmClient()).build()) {

            assertThrows(IllegalStateException.class, () -> runtime
                    .task("test").agent("NoSuchAgent")
                    .instructions("irrelevant")
                    .input(CompetitorQuery.class)
                    .output(CompetitorBrief.class)
                    .build());
        }
    }

    @Test
    void missingInstructionsThrowsOnBuild() {

        try (var runtime = baseRuntime(new MockLlmClient()).build()) {

            assertThrows(IllegalStateException.class, () -> runtime
                    .task("test").agent("Analyst")
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

            var invoker = runtime.task("test").agent("Analyst")
                    .instructions("Summarize today")
                    .input(Void.class)
                    .output(CompetitorBrief.class)
                    .build();

            var brief = invoker.start().await();
            assertEquals("a summary", brief.summary());
        }
    }

    @Test
    void stringOutputBypassesStructuredOutput() {

        var llm = new MockLlmClient().onSend("Write a note", "free-form response text");

        try (var runtime = baseRuntime(llm).build()) {

            var invoker = runtime.task("test").agent("Analyst")
                    .instructions("Write a note")
                    .input(Void.class)
                    .output(String.class)
                    .build();

            assertEquals("free-form response text", invoker.start().await());
        }
    }

    @Test
    void voidOutputUsesNoArgAwait() {

        var llm = new MockLlmClient().onSend("Do something", "ok");

        try (var runtime = baseRuntime(llm).build()) {

            var invoker = runtime.task("test").agent("Analyst")
                    .instructions("Do something")
                    .input(Void.class)
                    .output(Void.class)
                    .build();

            var result = invoker.start().untypedResult();
            assertEquals(WorkflowRunStatus.COMPLETED, result.status());
        }
    }

    @Test
    void persistRegistersPlan() {

        var llm = new MockLlmClient().onSend("noop", "ok");

        try (var runtime = baseRuntime(llm).build()) {

            runtime.task("analyst-oneshot").agent("Analyst")
                    .instructions("noop")
                    .persist()
                    .input(Void.class)
                    .output(Void.class)
                    .build();

            assertNotNull(runtime.registry().workflows().byName("analyst-oneshot"),
                    "WorkflowDefinition should be visible in the definition registry after persist()");
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

                .configuration().api()
                    .llm(LlmConfig.builder().apiKey("mock").build())
                    .end()
                .llm("default", llm)
                .registry().api()
                    .agent(AgentConfig.builder()
                        .name("Analyst").id("Analyst")
                        .role("Analyst").llm("default").build())
                    .end()
                .build()) {

            var invoker = runtime.task("test").agent("Analyst")
                    .instructions("Do it")
                    .input(Void.class)
                    .output(CompetitorBrief.class)
                    .build();

            invoker.start().await();

            assertFalse(capturedSystemPrompts.isEmpty());
            assertTrue(capturedSystemPrompts.getFirst().contains("MUST be valid JSON"),
                    "Structured-output prompt should be applied when O is a record");
        }
    }

    @Test
    void toolsAreAttachedToStep() {

        var llm = new MockLlmClient().onSend("Look it up", "{\"summary\":\"done\",\"threatLevel\":\"LOW\"}");

        try (var runtime = baseRuntime(llm).build()) {

            var invoker = runtime.task("analyst-with-tools").agent("Analyst")
                    .instructions("Look it up")
                    .tools("search_web", "fetch_url")
                    .persist()
                    .input(Void.class)
                    .output(CompetitorBrief.class)
                    .build();

            var plan = runtime.registry().workflows().byName("analyst-with-tools");
            var step = (ai.agentican.framework.orchestration.model.WorkflowStepAgent) plan.steps().getFirst();

            assertEquals(java.util.List.of("search_web", "fetch_url"), step.tools());
        }
    }

    @Test
    void skillsAreAttachedToStep() {

        var llm = new MockLlmClient().onSend("Do", "{\"summary\":\"ok\",\"threatLevel\":\"LOW\"}");

        try (var runtime = Agentican.builder()

                .configuration().api()
                    .llm(LlmConfig.builder().apiKey("mock").build())
                    .end()
                .llm("default", llm.toLlmClient())
                .registry().api()
                    .agent(AgentConfig.builder()
                        .name("Analyst").id("Analyst")
                        .role("Analyst").llm("default").build())
                    .skill(ai.agentican.framework.config.SkillConfig.builder()
                        .name("Tone").id("Tone").instructions("Be terse").build())
                    .end()
                .build()) {

            var invoker = runtime.task("analyst-with-skills").agent("Analyst")
                    .instructions("Do")
                    .skills("Tone")
                    .persist()
                    .input(Void.class)
                    .output(CompetitorBrief.class)
                    .build();

            var plan = runtime.registry().workflows().byName("analyst-with-skills");
            var step = (ai.agentican.framework.orchestration.model.WorkflowStepAgent) plan.steps().getFirst();

            assertEquals(java.util.List.of("Tone"), step.skills());
        }
    }
}
