package ai.agentican.framework;

import ai.agentican.framework.registry.AgentRegistry;
import ai.agentican.framework.registry.PlanRegistry;
import ai.agentican.framework.registry.SkillRegistry;
import ai.agentican.framework.registry.ToolkitRegistry;

public record AgenticanRegistry(
        PlanRegistry plans,
        AgentRegistry agents,
        ToolkitRegistry toolkits,
        SkillRegistry skills) {
}
