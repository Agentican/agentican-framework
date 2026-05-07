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

        var selectedBranch = selectBranch(step, upstreamOutput);

        if (selectedBranch == null) {

            return new WorkflowStepResult(step.name(), WorkflowRunStatus.FAILED,
                    "No matching branch found in branch step '" + step.name() + "'", List.of());
        }

        LOG.info("Branch step '{}': selected branch '{}'", step.name(), selectedBranch.name());

        workflowRunStore.branchPathChosen(parentTaskId, parentStepId, selectedBranch.name());

        var subPlan = WorkflowDefinition.builder(Ids.generate(), step.name() + "-" + selectedBranch.name())
                .description("")
                .steps(selectedBranch.steps())
                .build();

        var subResult = subPlanRunner.run(subPlan, params, cancelled, outputs, parentTaskId, parentStepId, 0);

        var allAgentResults = subResult.stepResults().stream()
                .flatMap(sr -> sr.agentResults().stream())
                .toList();

        var lastOutput = subResult.stepResults().isEmpty() ? "" : subResult.stepResults().getLast().output();

        return new WorkflowStepResult(step.name(), subResult.status(), lastOutput != null ? lastOutput : "",
                allAgentResults);
    }

    private WorkflowStepBranch.Branch selectBranch(WorkflowStepBranch step, String upstreamOutput) {

        var trimmed = upstreamOutput.strip().toLowerCase();

        for (var branch : step.branches())
            if (trimmed.equals(branch.name().toLowerCase()))
                return branch;

        for (var branch : step.branches())
            if (trimmed.contains(branch.name().toLowerCase()))
                return branch;

        try {

            int start = upstreamOutput.indexOf('[');
            int end = upstreamOutput.lastIndexOf(']');

            if (start >= 0 && end > start) {

                var jsonPart = upstreamOutput.substring(start, end + 1);

                List<String> parsed = Json.mapper().readValue(jsonPart, new TypeReference<>() {});

                if (!parsed.isEmpty()) {

                    var first = parsed.getFirst().strip().toLowerCase();

                    for (var branch : step.branches())
                        if (first.equals(branch.name().toLowerCase()))
                            return branch;
                }
            }
        }
        catch (Exception _) {}

        if (step.defaultBranch() != null) {

            for (var branch : step.branches())
                if (branch.name().equals(step.defaultBranch()))
                    return branch;
        }

        return null;
    }
}
