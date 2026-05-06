package ai.agentican.framework.orchestration.execution;

import ai.agentican.framework.orchestration.model.*;
import ai.agentican.framework.store.WorkflowRunStore;
import ai.agentican.framework.util.Ids;

import ai.agentican.framework.util.Json;

import com.fasterxml.jackson.core.type.TypeReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

class StepBranchRunner {

    private static final Logger LOG = LoggerFactory.getLogger(StepBranchRunner.class);

    private final StepLoopRunner.SubPlanRunner subPlanRunner;
    private final WorkflowRunStore workflowRunStore;

    StepBranchRunner(StepLoopRunner.SubPlanRunner subPlanRunner, WorkflowRunStore workflowRunStore) {

        this.subPlanRunner = subPlanRunner;
        this.workflowRunStore = workflowRunStore;
    }

    WorkflowStepResult run(WorkflowStepBranch step, Map<String, String> outputs, Map<String, String> params,
                       AtomicBoolean cancelled, String parentTaskId, String parentStepId) {

        var upstreamOutput = outputs.get(step.from());

        if (upstreamOutput == null) upstreamOutput = params.get(step.from());

        if (upstreamOutput == null) {

            return new WorkflowStepResult(step.name(), WorkflowRunStatus.FAILED,
                    "No output or param found for '" + step.from() + "' (branch step '" + step.name() + "')",
                    List.of());
        }

        var selectedPath = selectBranch(step, upstreamOutput);

        if (selectedPath == null) {

            return new WorkflowStepResult(step.name(), WorkflowRunStatus.FAILED,
                    "No matching path found in branch '" + step.name() + "'", List.of());
        }

        LOG.info("Branch step '{}': selected path '{}'", step.name(), selectedPath.pathName());

        workflowRunStore.branchPathChosen(parentTaskId, parentStepId, selectedPath.pathName());

        var subPlan = WorkflowDefinition.builder(Ids.generate(), step.name() + "-" + selectedPath.pathName())
                .description("")
                .steps(selectedPath.body())
                .build();

        var subResult = subPlanRunner.run(subPlan, params, cancelled, outputs, parentTaskId, parentStepId, 0);

        var allAgentResults = subResult.stepResults().stream()
                .flatMap(sr -> sr.agentResults().stream())
                .toList();

        var lastOutput = subResult.stepResults().isEmpty() ? "" : subResult.stepResults().getLast().output();

        return new WorkflowStepResult(step.name(), subResult.status(), lastOutput != null ? lastOutput : "",
                allAgentResults);
    }

    private WorkflowStepBranch.Path selectBranch(WorkflowStepBranch step, String upstreamOutput) {

        var trimmed = upstreamOutput.strip().toLowerCase();

        for (var path : step.paths())
            if (trimmed.equals(path.pathName().toLowerCase()))
                return path;

        for (var path : step.paths())
            if (trimmed.contains(path.pathName().toLowerCase()))
                return path;

        try {

            int start = upstreamOutput.indexOf('[');
            int end = upstreamOutput.lastIndexOf(']');

            if (start >= 0 && end > start) {

                var jsonPart = upstreamOutput.substring(start, end + 1);

                List<String> parsed = Json.mapper().readValue(jsonPart, new TypeReference<>() {});

                if (!parsed.isEmpty()) {

                    var first = parsed.getFirst().strip().toLowerCase();

                    for (var path : step.paths())
                        if (first.equals(path.pathName().toLowerCase()))
                            return path;
                }
            }
        }
        catch (Exception _) {}

        if (step.defaultPath() != null) {

            for (var path : step.paths())
                if (path.pathName().equals(step.defaultPath()))
                    return path;
        }

        return null;
    }
}
