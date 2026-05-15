package ai.agentican.temporal.workflow;

import ai.agentican.framework.config.AgentConfig;
import ai.agentican.framework.hitl.HitlType;
import ai.agentican.framework.tools.ToolDefinition;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public record RunnerBasedAgentInput(
        AgentConfig agentConfig,
        String task,
        String taskId,
        String stepId,
        String stepName,
        List<String> skills,
        List<ToolDefinition> toolDefinitions,
        Map<String, HitlType> toolHitlTypes,
        Duration timeout,
        int maxTurns) {

    public RunnerBasedAgentInput {

        if (agentConfig == null) throw new IllegalArgumentException("agentConfig is required");
        if (task == null || task.isBlank()) throw new IllegalArgumentException("task is required");

        if (skills == null) skills = List.of();
        if (toolDefinitions == null) toolDefinitions = List.of();
        if (toolHitlTypes == null) toolHitlTypes = Map.of();
        if (maxTurns < 1) maxTurns = 10;
    }
}
