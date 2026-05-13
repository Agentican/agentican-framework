package ai.agentican.temporal.activity;

import ai.agentican.framework.agent.Agent;
import ai.agentican.framework.state.RunLog;
import ai.agentican.framework.store.WorkflowRunStore;
import ai.agentican.framework.tools.Toolkit;
import ai.agentican.temporal.dto.AgentInvocationRequest;
import ai.agentican.temporal.dto.AgentInvocationResult;
import ai.agentican.temporal.dto.AgentResumeRequest;
import ai.agentican.temporal.dto.HitlCheckpointDto;
import ai.agentican.temporal.dto.TokenUsageDto;

import java.util.LinkedHashMap;
import java.util.Map;

public class AgentStepActivityImpl implements AgentStepActivity {

    private final AgentResolver agentResolver;
    private final ToolkitResolver toolkitResolver;
    private final WorkflowRunStore runStore;

    public AgentStepActivityImpl(AgentResolver agentResolver, ToolkitResolver toolkitResolver) {

        this(agentResolver, toolkitResolver, null);
    }

    public AgentStepActivityImpl(AgentResolver agentResolver, ToolkitResolver toolkitResolver,
                                 WorkflowRunStore runStore) {

        if (agentResolver == null)   throw new IllegalArgumentException("agentResolver is required");
        if (toolkitResolver == null) throw new IllegalArgumentException("toolkitResolver is required");

        this.agentResolver = agentResolver;
        this.toolkitResolver = toolkitResolver;
        this.runStore = runStore;
    }

    @Override
    public AgentInvocationResult invokeAgent(AgentInvocationRequest req) {

        var agent = agentResolver.resolve(req.agentRef());

        if (agent == null)
            throw new IllegalArgumentException("Unknown agent: " + req.agentRef());

        var toolkits = resolveToolkits(req.toolkitSlugs());

        var result = agent.run(req.renderedTask(), req.taskId(), req.stepId(), req.stepName(), req.timeout(),
                req.skills(), toolkits);

        return mapResult(result);
    }

    @Override
    public AgentInvocationResult resumeAgent(AgentResumeRequest req) {

        if (runStore == null)
            throw new IllegalStateException(
                    "resumeAgent requires a WorkflowRunStore; construct AgentStepActivityImpl with the 3-arg constructor.");

        var orig = req.originalRequest();

        var agent = agentResolver.resolve(orig.agentRef());

        if (agent == null)
            throw new IllegalArgumentException("Unknown agent: " + orig.agentRef());

        var savedRun = loadSavedRun(orig.taskId(), req.runId());

        if (savedRun == null)
            throw new IllegalStateException(
                    "No saved RunLog found for taskId=" + orig.taskId() + " runId=" + req.runId());

        var toolkits = resolveToolkits(orig.toolkitSlugs());

        var result = agent.resume(orig.renderedTask(), orig.taskId(), orig.stepId(), orig.stepName(), orig.timeout(),
                orig.skills(), toolkits, savedRun, req.hitlToolResults());

        return mapResult(result);
    }

    private Map<String, Toolkit> resolveToolkits(java.util.List<String> slugs) {

        var toolkits = new LinkedHashMap<String, Toolkit>();

        for (var slug : slugs) {

            var tk = toolkitResolver.resolve(slug);

            if (tk != null) toolkits.put(slug, tk);
        }

        return toolkits;
    }

    private RunLog loadSavedRun(String taskId, String runId) {

        var log = runStore.load(taskId);

        if (log == null) return null;

        return log.findRunById(runId);
    }

    private static AgentInvocationResult mapResult(ai.agentican.framework.agent.AgentResult result) {

        var runId = result.run() != null ? result.run().id() : null;

        return new AgentInvocationResult(result.status(), result.text(), runId,
                HitlCheckpointDto.from(result.checkpoint()), TokenUsageDto.from(result.tokenUsage()));
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
