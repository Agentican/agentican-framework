package ai.agentican.framework.orchestration.execution;

import ai.agentican.framework.agent.AgentRegistry;
import ai.agentican.framework.orchestration.model.PlanStepAgent;
import ai.agentican.framework.tools.ToolkitRegistry;
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

        var agent = agentRegistry.get(taskStep.agentName());

        if (agent == null) {

            LOG.error("No agent found with name '{}'", taskStep.agentName());

            return new TaskStepResult(taskStep.name(), TaskStatus.FAILED,
                    "No agent found with name: " + taskStep.agentName(), List.of());
        }

        var rawInstructions = taskStep.instructions();

        var instructions = Placeholders.resolveStepOutputs(Placeholders.resolveParams(rawInstructions, taskParams), parentStepOutputs);

        var taskStepToolkits = toolkitRegistry.scopeForStep(taskStep.toolkits());

        LOG.info(Logs.RUNNER_RUN_AGENT_STEP, taskStep.name());

        // Apply per-step timeout if set
        var runner = taskStep.timeout() != null
                ? agent.runner().withTimeout(taskStep.timeout())
                : agent.runner();

        var taskStepResult = runner.run(agent, instructions, taskStep.skills(), taskStepToolkits,
                taskId, stepId, taskStep.name());

        var stepResultStatus = taskStepResult.isCompleted() ? TaskStatus.COMPLETED
                : taskStepResult.isSuspended() ? TaskStatus.SUSPENDED
                : TaskStatus.FAILED;

        return new TaskStepResult(taskStep.name(), stepResultStatus, taskStepResult.text(), List.of(taskStepResult));
    }
}
