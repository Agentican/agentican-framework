package ai.agentican.framework;

import ai.agentican.framework.agent.AgentRegistry;
import ai.agentican.framework.orchestration.PlanRegistry;
import ai.agentican.framework.skill.SkillRegistry;
import ai.agentican.framework.tools.ToolkitRegistry;

public record AgenticanRegistry(
        PlanRegistry plans,
        AgentRegistry agents,
        ToolkitRegistry toolkits,
        SkillRegistry skills) {
}
