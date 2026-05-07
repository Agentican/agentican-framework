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
import ai.agentican.framework.orchestration.model.WorkflowDefinition;
import ai.agentican.framework.store.WorkflowRunStoreMemory;
import ai.agentican.framework.registry.ToolkitRegistry;
import org.junit.jupiter.api.Test;

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

        return Agent.builder().config(AgentConfig.builder().name(name).id(name).role("Test agent for " + name).build()).runner(runner).build();
    }

    private WorkflowRunner taskRunner(AgentRegistryMemory agents, CodeStepRegistry codeSteps) {

        return new WorkflowRunner(agents, autoApproveHitl(), new ToolkitRegistry(),
                new WorkflowRunStoreMemory(), null, 0, null, codeSteps);
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

        var plan = WorkflowDefinition.builder("typed-test", "typed-test")
                .param().name("amount").end()
                .step().name("produce").code("make-output")
                        .input(new AmountIn("{{param.amount}}")).end()
                .step().name("finalize").agent("collector")
                        .instructions("Process {{step.produce.output}}")
                        .dependencies("produce").end()
                .build();

        var result = runner.run(plan, java.util.Map.of("amount", "100"));

        assertEquals(WorkflowRunStatus.COMPLETED, result.status());

        var codeStepResult = result.stepResults().stream()
                .filter(r -> r.name().equals("produce"))
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

        var plan = WorkflowDefinition.builder("http-test", "http-test")
                .step().name("call").code("http").end()
                .build();

        var result = runner.run(plan);

        assertEquals(WorkflowRunStatus.COMPLETED, result.status());
        var output = result.stepResults().getFirst().output();
        assertTrue(output.contains("\"body\":\"response body\""));
        assertTrue(output.contains("\"status\":200"));
    }

    @Test
    void downstreamAgentReadsOutputField() {

        var mockLlm = new MockLlmClient()
                .onSend("body=hello world status=201", endTurn("DONE"));

        var agents = new AgentRegistryMemory();
        agents.register(createAgent("reader", mockLlm));

        var codeSteps = new CodeStepRegistry();
        codeSteps.register(new CodeStepSpec<>("make", null, Void.class, HttpOut.class),
                (CodeStep<Void, HttpOut>) (input, ctx) -> new HttpOut("hello world", 201));

        var runner = taskRunner(agents, codeSteps);

        var plan = WorkflowDefinition.builder("field-access", "field-access")
                .step().name("produce").code("make").end()
                .step().name("read").agent("reader")
                        .instructions("body={{step.produce.output.body}} status={{step.produce.output.status}}")
                        .dependencies("produce").end()
                .build();

        var result = runner.run(plan);

        assertEquals(WorkflowRunStatus.COMPLETED, result.status());
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

        var plan = WorkflowDefinition.builder("raw-map", "raw-map")
                .step().name("step").code("raw")
                        .input(Map.of("key", "value")).end()
                .build();

        var result = runner.run(plan);

        assertEquals(WorkflowRunStatus.COMPLETED, result.status());
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

        var plan = WorkflowDefinition.builder("void-test", "void-test")
                .step().name("noop-step").code("noop").end()
                .build();

        var result = runner.run(plan);

        assertEquals(WorkflowRunStatus.COMPLETED, result.status());
        assertTrue(ran.get());
        assertEquals("", result.stepResults().getFirst().output());
    }

    @Test
    void missingSlugFailsTheStep() {

        var agents = new AgentRegistryMemory();
        var codeSteps = new CodeStepRegistry();

        var runner = taskRunner(agents, codeSteps);

        var plan = WorkflowDefinition.builder("missing-test", "missing-test")
                .step().name("missing").code("not-registered").end()
                .build();

        var result = runner.run(plan);

        assertEquals(WorkflowRunStatus.FAILED, result.status());

        var stepResult = result.stepResults().getFirst();
        assertEquals(WorkflowRunStatus.FAILED, stepResult.status());
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

        var plan = WorkflowDefinition.builder("boom-test", "boom-test")
                .step().name("explode").code("boom").end()
                .build();

        var result = runner.run(plan);

        assertEquals(WorkflowRunStatus.FAILED, result.status());
        assertTrue(result.stepResults().getFirst().output().contains("kaboom"));
    }

    @Test
    void stringOutputStoredVerbatim() {

        var agents = new AgentRegistryMemory();
        var codeSteps = new CodeStepRegistry();
        codeSteps.register(new CodeStepSpec<>("speak", null, Void.class, String.class),
                (CodeStep<Void, String>) (input, ctx) -> "hello world");

        var runner = taskRunner(agents, codeSteps);

        var plan = WorkflowDefinition.builder("string-out", "string-out")
                .step().name("say").code("speak").end()
                .build();

        var result = runner.run(plan);

        assertEquals("hello world", result.stepResults().getFirst().output());
    }
}
