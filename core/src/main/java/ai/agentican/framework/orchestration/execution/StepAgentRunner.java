package ai.agentican.framework.orchestration.execution;

import ai.agentican.framework.registry.AgentRegistry;
import ai.agentican.framework.llm.StructuredOutput;
import ai.agentican.framework.orchestration.model.PlanStepAgent;
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

    TaskStepResult run(PlanStepAgent taskStep, Map<String, String> parentStepOutputs, Map<String, String> taskParams,
                       String taskId, String stepId) {

        return run(taskStep, parentStepOutputs, taskParams, taskId, stepId, null);
    }

    TaskStepResult run(PlanStepAgent taskStep, Map<String, String> parentStepOutputs, Map<String, String> taskParams,
                       String taskId, String stepId, StructuredOutput structuredOutput) {

        var agentRef = taskStep.agentId();

        var agent = agentRegistry.get(agentRef);

        if (agent == null) agent = agentRegistry.getByName(agentRef);

        if (agent == null) {

            LOG.error("No agent found for ref '{}'", agentRef);

            return new TaskStepResult(taskStep.name(), TaskStatus.FAILED,
                    "No agent found for ref: " + agentRef, List.of());
        }

        var rawInstructions = taskStep.instructions();

        var instructions = Placeholders.resolveStepOutputs(Placeholders.resolveParams(rawInstructions, taskParams), parentStepOutputs);

        var taskStepToolkits = toolkitRegistry.scopeForStep(taskStep.tools());

        LOG.info(Logs.RUNNER_RUN_AGENT_STEP, taskStep.name());

        var taskStepResult = agent.run(instructions,
                taskId, stepId, taskStep.name(),
                taskStep.timeout(),
                taskStep.skills(), taskStepToolkits,
                structuredOutput);

        var stepResultStatus = taskStepResult.isCompleted() ? TaskStatus.COMPLETED
                : taskStepResult.isSuspended() ? TaskStatus.SUSPENDED
                : TaskStatus.FAILED;

        return new TaskStepResult(taskStep.name(), stepResultStatus, taskStepResult.text(), List.of(taskStepResult));
    }
}
