package ai.agentican.temporal.workflow;

import ai.agentican.framework.orchestration.execution.OrchestrationHelpers;
import ai.agentican.framework.orchestration.model.WorkflowDefinition;
import ai.agentican.framework.orchestration.model.WorkflowStep;
import ai.agentican.framework.orchestration.model.WorkflowStepAgent;
import ai.agentican.framework.orchestration.model.WorkflowStepBranch;
import ai.agentican.framework.orchestration.model.WorkflowStepCode;
import ai.agentican.framework.orchestration.model.WorkflowStepLoop;
import ai.agentican.framework.util.Json;
import ai.agentican.framework.util.Placeholders;
import ai.agentican.temporal.activity.AgentConfigActivity;
import ai.agentican.temporal.activity.CodeStepActivity;
import ai.agentican.temporal.dto.CodeInvocationRequest;

import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Async;
import io.temporal.workflow.ChildWorkflowOptions;
import io.temporal.workflow.Promise;
import io.temporal.workflow.Workflow;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fine-grained generic plan interpreter — same {@link AgenticanWorkflow}
 * interface and same {@code WorkflowDefinition} input as
 * {@link AgenticanWorkflowImpl}, but each {@code WorkflowStepAgent} dispatches
 * a child {@link RunnerBasedAgentWorkflow} instead of a single coarse
 * {@code AgentStepActivity}.
 *
 * <p>Net effect: every LLM round-trip and every tool call inside an agent step
 * shows up as a discrete activity in the child workflow's history, with its
 * own retry policy. The parent workflow's history shows one
 * {@code ChildWorkflowExecution} event per agent step plus one activity per
 * code step — same plan-level shape as the coarse interpreter.
 *
 * <h2>Worker-side wiring</h2>
 *
 * Register on the same worker (or split across workers — child can land on a
 * different task queue via {@code ChildWorkflowOptions.setTaskQueue}):
 *
 * <ul>
 *   <li>{@code FineGrainedAgenticanWorkflowImpl.class} — this class</li>
 *   <li>{@link RunnerBasedAgentWorkflowImpl} — the child workflow</li>
 *   <li>{@link AgentConfigActivity} impl — agent config lookup</li>
 *   <li>{@code LlmCallActivity}, {@code ToolCallActivity},
 *       {@code WorkflowRunStoreActivity}, {@code KnowledgeStoreActivity} —
 *       fine-grained activities the runner drives via the host SPI</li>
 *   <li>{@code CodeStepActivity} — only if your plan has code steps</li>
 * </ul>
 *
 * <h2>HITL note</h2>
 *
 * The {@link #provideHitlReply} signal on this parent workflow is a no-op:
 * suspended agent steps live inside the child workflow, so HITL responses
 * route to {@link RunnerBasedAgentWorkflow#provideHitlResponse} on the child,
 * not here. (Plan-level HITL between steps would land on the parent — not
 * supported in v1, see {@code AgenticanWorkflowImpl}'s validate() rules.)
 */
public class FineGrainedAgenticanWorkflowImpl implements AgenticanWorkflow {

    private static final Duration DEFAULT_ACTIVITY_TIMEOUT = Duration.ofMinutes(15);

    private final AgentConfigActivity configActivity = Workflow.newActivityStub(AgentConfigActivity.class,
            ActivityOptions.newBuilder().setStartToCloseTimeout(Duration.ofSeconds(30)).build());

    private final CodeStepActivity codeActivity = Workflow.newActivityStub(CodeStepActivity.class,
            ActivityOptions.newBuilder().setStartToCloseTimeout(DEFAULT_ACTIVITY_TIMEOUT).build());

    @Override
    public String run(AgenticanWorkflowInput input) {

        var plan   = input.plan();
        var params = Map.copyOf(input.params());

        validate(plan);

        var taskId = Workflow.randomUUID().toString();

        var outputs = executePlan(plan, params, taskId);

        if (plan.outputStep() == null || plan.outputStep().isBlank()) return "";

        return outputs.getOrDefault(plan.outputStep(), "");
    }

    @Override
    public void provideHitlReply(HitlReplySignal reply) {

        // No-op — HITL inside an agent step lives in the child RunnerBasedAgentWorkflow
        // and is routed via its own provideHitlResponse signal. See class doc.
    }

    // ── plan validation (same restrictions as AgenticanWorkflowImpl) ────────

    private static void validate(WorkflowDefinition plan) {

        for (var step : plan.steps()) {

            rejectConditions(step);

            if (step instanceof WorkflowStepLoop loop)
                validateBody(loop.body(), "loop body of '" + step.name() + "'");

            if (step instanceof WorkflowStepBranch branch)
                for (var b : branch.branches())
                    validateBody(b.steps(), "branch path '" + b.name() + "' of '" + step.name() + "'");
        }
    }

    private static void validateBody(List<WorkflowStep> body, String label) {

        for (var step : body) {

            rejectConditions(step);

            if (step instanceof WorkflowStepLoop || step instanceof WorkflowStepBranch)
                throw new UnsupportedOperationException(
                        "Nested loop/branch not supported in " + label + " (step '" + step.name() + "')");

            if (step.hitl())
                throw new UnsupportedOperationException(
                        "HITL inside " + label + " not supported in v1 (step '" + step.name() + "')");
        }
    }

    private static void rejectConditions(WorkflowStep step) {

        if (step instanceof WorkflowStepAgent a && a.conditions() != null && !a.conditions().isEmpty())
            throw new UnsupportedOperationException(
                    "Conditions on agent steps not supported in v1 (step '" + step.name() + "')");
    }

    // ── plan execution ──────────────────────────────────────────────────────

    private Map<String, String> executePlan(WorkflowDefinition plan, Map<String, String> params, String taskId) {

        var layers = OrchestrationHelpers.computeLayers(plan);

        var outputs = new LinkedHashMap<String, String>();

        for (var layer : layers) {

            var outputsSnapshot = Map.copyOf(outputs);

            var promises = new ArrayList<Promise<StepResult>>();

            for (var step : layer) {

                final var s = step;

                promises.add(Async.function(() -> executeStep(s, params, outputsSnapshot, taskId)));
            }

            Promise.allOf(promises).get();

            for (var p : promises) {

                var r = p.get();

                outputs.put(r.stepName(), r.output());
            }
        }

        return outputs;
    }

    private StepResult executeStep(WorkflowStep step, Map<String, String> params,
                                   Map<String, String> outputs, String taskId) {

        if (step instanceof WorkflowStepAgent a) return executeAgentStep(a, params, outputs, taskId);
        if (step instanceof WorkflowStepCode<?> c) return executeCodeStep(c, params, outputs, taskId);
        if (step instanceof WorkflowStepLoop l) return executeLoopStep(l, params, outputs, taskId);
        if (step instanceof WorkflowStepBranch b) return executeBranchStep(b, params, outputs, taskId);

        throw new IllegalStateException("Unknown step type: " + step.getClass().getName());
    }

    /**
     * The fine-grained difference vs {@link AgenticanWorkflowImpl#executeAgentStep}:
     * dispatch a child {@link RunnerBasedAgentWorkflow} instead of calling
     * {@code AgentStepActivity.invokeAgent}. The child runs the SmacAgentRunner
     * via {@code TemporalAgentLoopHost}, emitting one activity per LLM/tool call.
     */
    private StepResult executeAgentStep(WorkflowStepAgent step, Map<String, String> params,
                                        Map<String, String> outputs, String taskId) {

        var instructions = step.instructions();

        var item = params.get("item");

        if (item != null) instructions = instructions.replace("{{item}}", item);

        var rendered = Placeholders.resolveStepOutputs(Placeholders.resolveParams(instructions, params), outputs);

        // Look up the agent's config (deterministic activity) — needed to
        // build the child workflow's RunnerBasedAgentInput.
        var agentConfig = configActivity.get(step.agentName());

        var maxTurns = agentConfig.maxTurns() != null ? agentConfig.maxTurns() : 10;

        var stepId = Workflow.randomUUID().toString();

        var childInput = new RunnerBasedAgentInput(
                agentConfig,
                rendered,
                taskId,
                stepId,
                step.name(),
                step.skills(),
                List.of(),                  // tool defs resolved on the activity worker via ToolkitResolver
                Map.of(),                   // no per-tool HITL hint at the plan layer
                step.timeout(),
                maxTurns);

        var child = Workflow.newChildWorkflowStub(
                RunnerBasedAgentWorkflow.class,
                ChildWorkflowOptions.newBuilder()
                        .setWorkflowId("agentican-fg-" + step.name() + "-" + Workflow.randomUUID())
                        .build());

        var output = child.run(childInput);

        return new StepResult(step.name(), output);
    }

    private StepResult executeCodeStep(WorkflowStepCode<?> step, Map<String, String> params,
                                       Map<String, String> outputs, String taskId) {

        var req = new CodeInvocationRequest(step.codeSlug(), taskId, Workflow.randomUUID().toString(), step.name(),
                step.input(), params, outputs);

        var result = codeActivity.invokeCode(req);

        return new StepResult(step.name(), result.output());
    }

    private StepResult executeLoopStep(WorkflowStepLoop step, Map<String, String> params,
                                       Map<String, String> outputs, String taskId) {

        var over = outputs.get(step.over());

        if (over == null) over = params.get(step.over());

        if (over == null)
            throw new IllegalStateException(
                    "Loop step '" + step.name() + "' could not resolve 'over' reference: " + step.over());

        var items = Json.findArray(over);

        if (items == null || items.isEmpty()) return new StepResult(step.name(), "[]");

        var iterationPromises = new ArrayList<Promise<String>>();

        for (int i = 0; i < items.size(); i++) {

            final var idx = i;

            final var item = items.get(i);

            iterationPromises.add(Async.function(() -> executeLoopIteration(step, params, outputs, taskId, item, idx)));
        }

        Promise.allOf(iterationPromises).get();

        var pieces = iterationPromises.stream().map(Promise::get).toList();

        return new StepResult(step.name(), "[" + String.join(",", pieces) + "]");
    }

    private String executeLoopIteration(WorkflowStepLoop step, Map<String, String> parentParams,
                                        Map<String, String> parentOutputs, String taskId, String item, int index) {

        var iterParams = new LinkedHashMap<>(parentParams);

        iterParams.put("item", item);

        var iterOutputs = new LinkedHashMap<>(parentOutputs);

        var lastOutput = "";

        for (var bodyStep : step.body()) {

            var r = executeStep(bodyStep, iterParams, iterOutputs, taskId);

            iterOutputs.put(r.stepName(), r.output());

            lastOutput = r.output();
        }

        return lastOutput;
    }

    private StepResult executeBranchStep(WorkflowStepBranch step, Map<String, String> params,
                                         Map<String, String> outputs, String taskId) {

        var from = outputs.get(step.from());

        if (from == null) from = params.get(step.from());

        if (from == null)
            throw new IllegalStateException(
                    "Branch step '" + step.name() + "' could not resolve 'from' reference: " + step.from());

        var selected = OrchestrationHelpers.selectBranch(step, from);

        if (selected == null)
            throw new IllegalStateException(
                    "Branch step '" + step.name() + "': no matching branch and no default");

        var localOutputs = new LinkedHashMap<>(outputs);

        var lastOutput = "";

        for (var bodyStep : selected.steps()) {

            var r = executeStep(bodyStep, params, localOutputs, taskId);

            localOutputs.put(r.stepName(), r.output());

            lastOutput = r.output();
        }

        return new StepResult(step.name(), lastOutput);
    }

    private record StepResult(String stepName, String output) { }
}
