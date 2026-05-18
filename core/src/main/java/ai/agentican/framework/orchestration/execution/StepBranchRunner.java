package ai.agentican.framework.orchestration.execution;

import ai.agentican.framework.event.AgenticanEventBus;
import ai.agentican.framework.event.BranchPathChosen;
import ai.agentican.framework.orchestration.model.*;
import ai.agentican.framework.util.Ids;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

class StepBranchRunner {

    private static final Logger LOG = LoggerFactory.getLogger(StepBranchRunner.class);

    private final StepLoopRunner.SubPlanRunner subPlanRunner;
    private final AgenticanEventBus eventBus;

    StepBranchRunner(StepLoopRunner.SubPlanRunner subPlanRunner, AgenticanEventBus eventBus) {

        this.subPlanRunner = subPlanRunner;
        this.eventBus = eventBus;
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

        var selectedBranch = OrchestrationHelpers.selectBranch(step, upstreamOutput);

        if (selectedBranch == null) {

            return new WorkflowStepResult(step.name(), WorkflowRunStatus.FAILED,
                    "No matching branch found in branch step '" + step.name() + "'", List.of());
        }

        LOG.info("Branch step '{}': selected branch '{}'", step.name(), selectedBranch.name());

        eventBus.publish(new BranchPathChosen(parentTaskId, parentStepId, selectedBranch.name()));

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

}
