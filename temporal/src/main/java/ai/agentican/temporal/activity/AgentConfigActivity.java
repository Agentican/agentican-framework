package ai.agentican.temporal.activity;

import ai.agentican.framework.config.AgentConfig;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * Looks up an {@link AgentConfig} by agent ref. Used by
 * {@link ai.agentican.temporal.workflow.FineGrainedAgenticanWorkflowImpl} —
 * the parent workflow needs an agent's config (role, llm name, max turns) to
 * pass into the child {@code RunnerBasedAgentWorkflow}, but cannot hold a live
 * {@code Agent} reference inside a workflow body.
 */
@ActivityInterface
public interface AgentConfigActivity {

    @ActivityMethod
    AgentConfig get(String agentRef);
}
