package ai.agentican.framework.orchestration.execution;

import ai.agentican.framework.MockLlmClient;
import ai.agentican.framework.agent.Agent;
import ai.agentican.framework.registry.AgentRegistryMemory;
import ai.agentican.framework.agent.SmacAgentRunner;
import ai.agentican.framework.hitl.HitlManager;
import ai.agentican.framework.hitl.HitlResponse;
import ai.agentican.framework.orchestration.code.CodeStep;
import ai.agentican.framework.orchestration.code.CodeStepRegistry;
import ai.agentican.framework.orchestration.code.CodeStepSpec;
import ai.agentican.framework.orchestration.model.Plan;
import ai.agentican.framework.orchestration.model.PlanStepAgent;
import ai.agentican.framework.orchestration.model.PlanStepCode;
import ai.agentican.framework.store.TaskStateStoreMemory;
import ai.agentican.framework.registry.ToolkitRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static ai.agentican.framework.MockLlmClient.endTurn;
import static org.junit.jupiter.api.Assertions.*;

import ai.agentican.framework.config.AgentConfig;
class StepCodeRunnerTest {

    record AmountIn(String amount) { }

    record HttpOut(String body, int status) { }

    private HitlManager autoApproveHitl() {

        return new HitlManager((mgr, cp) -> mgr.respond(cp.id(), HitlResponse.approve()));
    }

    private Agent createAgent(String name, MockLlmClient mockLlm) {

        var runner = SmacAgentRunner.builder()
                .llmClient(mockLlm.toLlmClient())
                .hitlManager(autoApproveHitl())
                .maxIterations(5)
                .build();

        return Agent.builder().config(AgentConfig.builder().name(name).role("Test agent for " + name).build()).runner(runner).build();
    }

    private TaskRunner taskRunner(AgentRegistryMemory agents, CodeStepRegistry codeSteps) {

        return new TaskRunner(agents, autoApproveHitl(), new ToolkitRegistry(),
                new TaskStateStoreMemory(), null, 0, null, codeSteps);
    }

    @Test
    void typedRecordRoundTripsThroughTemplateResolution() {

        var mockLlm = new MockLlmClient()
                .onSend("final output", endTurn("AGENT DONE"));

        var agents = new AgentRegistryMemory();
        agents.register(createAgent("collector", mockLlm));

        var codeSteps = new CodeStepRegistry();
        CodeStep<AmountIn, String> step = (input, ctx) -> "final output (amount=" + input.amount() + ")";
        codeSteps.register(new CodeStepSpec<>("make-output", null, AmountIn.class, String.class), step);

        var runner = taskRunner(agents, codeSteps);

        var plan = Plan.builder("typed-test")
                .param("amount")
                .step(PlanStepCode.<AmountIn>builder("produce")
                        .code("make-output")
                        .inputs(new AmountIn("{{param.amount}}"))
                        .build())
                .step(new PlanStepAgent("finalize", "collector",
                        "Process {{step.produce.output}}", List.of("produce"),
                        false, null, null))
                .build();

        var result = runner.run(plan, java.util.Map.of("amount", "100"));

        assertEquals(TaskStatus.COMPLETED, result.status());

        var codeStepResult = result.stepResults().stream()
                .filter(r -> r.stepName().equals("produce"))
                .findFirst().orElseThrow();

        assertEquals("final output (amount=100)", codeStepResult.output());
        assertTrue(codeStepResult.agentResults().isEmpty(),
                "code steps have no agent runs");
    }

    @Test
    void typedRecordOutputSerializesToJson() {

        var agents = new AgentRegistryMemory();

        var codeSteps = new CodeStepRegistry();
        CodeStep<Void, HttpOut> step = (input, ctx) -> new HttpOut("response body", 200);
        codeSteps.register(new CodeStepSpec<>("http", null, Void.class, HttpOut.class), step);

        var runner = taskRunner(agents, codeSteps);

        var plan = Plan.builder("http-test")
                .step(new PlanStepCode<>("call", "http", null, List.of()))
                .build();

        var result = runner.run(plan);

        assertEquals(TaskStatus.COMPLETED, result.status());
        var output = result.stepResults().getFirst().output();
        assertTrue(output.contains("\"body\":\"response body\""));
        assertTrue(output.contains("\"status\":200"));
    }

    @Test
    void downstreamAgentReadsOutputField() {

        // The mock matches on a substring; if {{step.produce.output.body}} and
        // .status resolve correctly, the agent's prompt will contain
        // "body=hello world status=201" and the mock returns DONE. If
        // resolution fails, no entry matches and the mock throws.
        var mockLlm = new MockLlmClient()
                .onSend("body=hello world status=201", endTurn("DONE"));

        var agents = new AgentRegistryMemory();
        agents.register(createAgent("reader", mockLlm));

        var codeSteps = new CodeStepRegistry();
        codeSteps.register(new CodeStepSpec<>("make", null, Void.class, HttpOut.class),
                (CodeStep<Void, HttpOut>) (input, ctx) -> new HttpOut("hello world", 201));

        var runner = taskRunner(agents, codeSteps);

        var plan = Plan.builder("field-access")
                .step(new PlanStepCode<>("produce", "make", null, List.of()))
                .step(new PlanStepAgent("read", "reader",
                        "body={{step.produce.output.body}} status={{step.produce.output.status}}",
                        List.of("produce"), false, null, null))
                .build();

        var result = runner.run(plan);

        assertEquals(TaskStatus.COMPLETED, result.status());
    }

    @Test
    void rawMapInputIsPassedThroughWithoutRoundTrip() {

        var captured = new java.util.concurrent.atomic.AtomicReference<Object>();

        var agents = new AgentRegistryMemory();

        var codeSteps = new CodeStepRegistry();
        @SuppressWarnings({"unchecked", "rawtypes"})
        var spec = (CodeStepSpec) new CodeStepSpec<>("raw", null, Map.class, String.class);
        @SuppressWarnings({"unchecked", "rawtypes"})
        CodeStep step = (input, ctx) -> {
            captured.set(input);
            return "ok";
        };
        codeSteps.register(spec, step);

        var runner = taskRunner(agents, codeSteps);

        var plan = Plan.builder("raw-map")
                .step(PlanStepCode.<Map<String, Object>>builder("step")
                        .code("raw")
                        .inputs(Map.of("key", "value"))
                        .build())
                .build();

        var result = runner.run(plan);

        assertEquals(TaskStatus.COMPLETED, result.status());
        assertEquals(Map.of("key", "value"), captured.get());
    }

    @Test
    void voidInputAndOutputWork() {

        var ran = new java.util.concurrent.atomic.AtomicBoolean(false);

        var agents = new AgentRegistryMemory();

        var codeSteps = new CodeStepRegistry();
        codeSteps.register(new CodeStepSpec<>("noop", null, Void.class, Void.class),
                (CodeStep<Void, Void>) (input, ctx) -> { ran.set(true); return null; });

        var runner = taskRunner(agents, codeSteps);

        var plan = Plan.builder("void-test")
                .step(new PlanStepCode<>("noop-step", "noop", null, List.of()))
                .build();

        var result = runner.run(plan);

        assertEquals(TaskStatus.COMPLETED, result.status());
        assertTrue(ran.get());
        assertEquals("", result.stepResults().getFirst().output());
    }

    @Test
    void missingSlugFailsTheStep() {

        var agents = new AgentRegistryMemory();
        var codeSteps = new CodeStepRegistry();

        var runner = taskRunner(agents, codeSteps);

        var plan = Plan.builder("missing-test")
                .step(new PlanStepCode<>("missing", "not-registered", null, List.of()))
                .build();

        var result = runner.run(plan);

        assertEquals(TaskStatus.FAILED, result.status());

        var stepResult = result.stepResults().getFirst();
        assertEquals(TaskStatus.FAILED, stepResult.status());
        assertTrue(stepResult.output().contains("not-registered"),
                "Failure message should mention the missing slug");
    }

    @Test
    void exceptionInCodeStepSurfacesAsFailure() {

        var agents = new AgentRegistryMemory();
        var codeSteps = new CodeStepRegistry();
        codeSteps.register(new CodeStepSpec<>("boom", null, Void.class, String.class),
                (CodeStep<Void, String>) (input, ctx) -> { throw new IllegalStateException("kaboom"); });

        var runner = taskRunner(agents, codeSteps);

        var plan = Plan.builder("boom-test")
                .step(new PlanStepCode<>("explode", "boom", null, List.of()))
                .build();

        var result = runner.run(plan);

        assertEquals(TaskStatus.FAILED, result.status());
        assertTrue(result.stepResults().getFirst().output().contains("kaboom"));
    }

    @Test
    void stringOutputStoredVerbatim() {

        var agents = new AgentRegistryMemory();
        var codeSteps = new CodeStepRegistry();
        codeSteps.register(new CodeStepSpec<>("speak", null, Void.class, String.class),
                (CodeStep<Void, String>) (input, ctx) -> "hello world");

        var runner = taskRunner(agents, codeSteps);

        var plan = Plan.builder("string-out")
                .step(new PlanStepCode<>("say", "speak", null, List.of()))
                .build();

        var result = runner.run(plan);

        assertEquals("hello world", result.stepResults().getFirst().output());
    }
}
