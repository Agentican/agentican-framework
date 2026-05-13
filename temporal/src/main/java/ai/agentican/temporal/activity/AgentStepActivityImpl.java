package ai.agentican.temporal.activity;

import ai.agentican.framework.agent.Agent;
import ai.agentican.framework.tools.Toolkit;
import ai.agentican.temporal.dto.AgentInvocationRequest;
import ai.agentican.temporal.dto.AgentInvocationResult;
import ai.agentican.temporal.dto.HitlCheckpointDto;
import ai.agentican.temporal.dto.TokenUsageDto;

import java.util.LinkedHashMap;
import java.util.Map;

public class AgentStepActivityImpl implements AgentStepActivity {

    private final AgentResolver agentResolver;
    private final ToolkitResolver toolkitResolver;

    public AgentStepActivityImpl(AgentResolver agentResolver, ToolkitResolver toolkitResolver) {

        if (agentResolver == null)   throw new IllegalArgumentException("agentResolver is required");
        if (toolkitResolver == null) throw new IllegalArgumentException("toolkitResolver is required");

        this.agentResolver = agentResolver;
        this.toolkitResolver = toolkitResolver;
    }

    @Override
    public AgentInvocationResult invokeAgent(AgentInvocationRequest req) {

        var agent = agentResolver.resolve(req.agentRef());

        if (agent == null)
            throw new IllegalArgumentException("Unknown agent: " + req.agentRef());

        var toolkits = new LinkedHashMap<String, Toolkit>();

        for (var slug : req.toolkitSlugs()) {

            var tk = toolkitResolver.resolve(slug);

            if (tk != null) toolkits.put(slug, tk);
        }

        var result = agent.run(req.renderedTask(), req.taskId(), req.stepId(), req.stepName(), req.timeout(),
                req.skills(), toolkits);

        return new AgentInvocationResult(result.status(), result.text(), HitlCheckpointDto.from(result.checkpoint()),
                TokenUsageDto.from(result.tokenUsage()));
    }

    @FunctionalInterface
    public interface AgentResolver {

        Agent resolve(String ref);
    }

    @FunctionalInterface
    public interface ToolkitResolver {

        Toolkit resolve(String slug);
    }
}
