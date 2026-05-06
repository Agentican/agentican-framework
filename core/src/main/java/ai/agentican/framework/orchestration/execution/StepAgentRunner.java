package ai.agentican.framework.orchestration.execution;

import ai.agentican.framework.registry.AgentRegistry;
import ai.agentican.framework.llm.StructuredOutput;
import ai.agentican.framework.orchestration.model.WorkflowStepAgent;
import ai.agentican.framework.registry.ToolkitRegistry;
import ai.agentican.framework.util.Logs;
import ai.agentican.framework.util.Placeholders;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

class StepAgentRunner {

    private static final Logger LOG = LoggerFactory.getLogger(StepAgentRunner.class);

    private final AgentRegistry agentRegistry;

    private final ToolkitRegistry toolkitRegistry;

    StepAgentRunner(AgentRegistry agentRegistry, ToolkitRegistry toolkitRegistry) {

        this.agentRegistry = agentRegistry;
        this.toolkitRegistry = toolkitRegistry;
    }

    WorkflowStepResult run(WorkflowStepAgent taskStep, Map<String, String> parentStepOutputs,
                           Map<String, String> taskParams, String taskId, String stepId) {

        return run(taskStep, parentStepOutputs, taskParams, taskId, stepId, null);
    }

    WorkflowStepResult run(WorkflowStepAgent taskStep, Map<String, String> parentStepOutputs,
                           Map<String, String> taskParams, String taskId, String stepId,
                           StructuredOutput structuredOutput) {

        var agentName = taskStep.agentName();

        var agent = agentRegistry.byName(agentName);

        if (agent == null) {

            LOG.error("No agent found for name '{}'", agentName);

            return new WorkflowStepResult(taskStep.name(), WorkflowRunStatus.FAILED,
                    "Agent not found: name=" + agentName, List.of());
        }

        var rawInstructions = taskStep.instructions();

        var paramInstructions = Placeholders.resolveParams(rawInstructions, taskParams);

        var instructions = Placeholders.resolveStepOutputs(paramInstructions, parentStepOutputs);

        var taskStepToolkits = toolkitRegistry.scopeForStep(taskStep.tools());

        LOG.info(Logs.RUNNER_RUN_AGENT_STEP, taskStep.name());

        var taskStepResult = agent.run(instructions, taskId, stepId, taskStep.name(), taskStep.timeout(),
                taskStep.skills(), taskStepToolkits, structuredOutput);

        var stepResultStatus = taskStepResult.isCompleted() ? WorkflowRunStatus.COMPLETED
                : taskStepResult.isSuspended() ? WorkflowRunStatus.SUSPENDED
                : WorkflowRunStatus.FAILED;

        return new WorkflowStepResult(taskStep.name(), stepResultStatus, taskStepResult.text(), List.of(taskStepResult));
    }
}
