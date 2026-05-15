package ai.agentican.temporal.workflow;

import ai.agentican.framework.agent.AgentStatus;
import ai.agentican.framework.orchestration.execution.OrchestrationHelpers;
import ai.agentican.framework.orchestration.model.WorkflowDefinition;
import ai.agentican.framework.orchestration.model.WorkflowStep;
import ai.agentican.framework.orchestration.model.WorkflowStepAgent;
import ai.agentican.framework.orchestration.model.WorkflowStepBranch;
import ai.agentican.framework.orchestration.model.WorkflowStepCode;
import ai.agentican.framework.orchestration.model.WorkflowStepLoop;
import ai.agentican.framework.tools.ToolResult;
import ai.agentican.framework.util.Json;
import ai.agentican.framework.util.Placeholders;
import ai.agentican.temporal.activity.AgentStepActivity;
import ai.agentican.temporal.activity.CodeStepActivity;
import ai.agentican.temporal.dto.AgentInvocationRequest;
import ai.agentican.temporal.dto.AgentInvocationResult;
import ai.agentican.temporal.dto.AgentResumeRequest;
import ai.agentican.temporal.dto.CodeInvocationRequest;

import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Async;
import io.temporal.workflow.Promise;
import io.temporal.workflow.Workflow;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AgenticanWorkflowImpl implements AgenticanWorkflow {

    private static final Duration DEFAULT_ACTIVITY_TIMEOUT = Duration.ofMinutes(15);

    private final AgentStepActivity agentActivity = Workflow.newActivityStub(AgentStepActivity.class,
            ActivityOptions.newBuilder().setStartToCloseTimeout(DEFAULT_ACTIVITY_TIMEOUT).build());

    private final CodeStepActivity codeActivity = Workflow.newActivityStub(CodeStepActivity.class,
            ActivityOptions.newBuilder().setStartToCloseTimeout(DEFAULT_ACTIVITY_TIMEOUT).build());

    private final Map<String, Deque<List<ToolResult>>> hitlReplies = new HashMap<>();

    @Override
    public String run(AgenticanWorkflowInput input) {

        var plan = input.plan();

        var params = Map.copyOf(input.params());

        validate(plan);

        var taskId = Workflow.randomUUID().toString();

        var outputs = executePlan(plan, params, taskId);

        if (plan.outputStep() == null || plan.outputStep().isBlank()) return "";

        return outputs.getOrDefault(plan.outputStep(), "");
    }

    @Override
    public void provideHitlReply(HitlReplySignal reply) {

        hitlReplies.computeIfAbsent(reply.stepName(), k -> new ArrayDeque<>()).addLast(reply.toolResults());
    }

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

    private StepResult executeAgentStep(WorkflowStepAgent step, Map<String, String> params,
                                        Map<String, String> outputs, String taskId) {

        var instructions = step.instructions();

        var item = params.get("item");

        if (item != null) instructions = instructions.replace("{{item}}", item);

        var rendered = Placeholders.resolveStepOutputs(Placeholders.resolveParams(instructions, params), outputs);

        var req = new AgentInvocationRequest(step.agentName(), rendered, taskId, Workflow.randomUUID().toString(),
                step.name(), step.timeout(), step.skills(), step.tools());

        var result = agentActivity.invokeAgent(req);

        result = awaitHitlCompletion(result, req, step.name());

        return new StepResult(step.name(), result.text());
    }

    private AgentInvocationResult awaitHitlCompletion(AgentInvocationResult result,
                                                     AgentInvocationRequest req, String stepName) {

        while (result.status() == AgentStatus.SUSPENDED) {

            Workflow.await(() -> {

                var q = hitlReplies.get(stepName);

                return q != null && !q.isEmpty();
            });

            var reply = hitlReplies.get(stepName).pollFirst();

            result = agentActivity.resumeAgent(new AgentResumeRequest(req, result.runId(), reply));
        }

        return result;
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
