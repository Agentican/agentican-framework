package ai.agentican.framework;

import ai.agentican.framework.vector.VectorIndexRegistry;
import ai.agentican.framework.registry.AgentRegistry;
import ai.agentican.framework.registry.WorkflowRegistry;
import ai.agentican.framework.registry.SkillRegistry;
import ai.agentican.framework.registry.ToolkitRegistry;

public record AgenticanRegistry(
        WorkflowRegistry workflows,
        AgentRegistry agents,
        ToolkitRegistry toolkits,
        SkillRegistry skills,
        VectorIndexRegistry indexes) {
}
