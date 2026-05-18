package ai.agentican.temporal.workflow;

import ai.agentican.framework.agent.Agent;
import ai.agentican.framework.agent.SmacAgentRunner;
import ai.agentican.framework.event.AgenticanEventBus;
import ai.agentican.framework.hitl.HitlResponse;
import ai.agentican.framework.hitl.HitlType;
import ai.agentican.framework.llm.LlmClient;
import ai.agentican.framework.tools.Toolkit;
import ai.agentican.temporal.activity.AgenticanActivity;
import ai.agentican.temporal.event.ForwarderListener;

import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RunnerBasedAgentWorkflowImpl implements RunnerBasedAgentWorkflow {

    private static final LlmClient HOST_ONLY_LLM_CLIENT = req -> {

        throw new IllegalStateException(
                "LlmClient should not be called in Temporal host mode — every LLM call is routed via "
                        + "TemporalAgentLoopHost.callLlm → LlmCallActivity.");
    };

    private static final Duration DEFAULT_ACTIVITY_TIMEOUT = Duration.ofSeconds(30);

    private final Map<String, HitlResponse> hitlResponses = new HashMap<>();

    @Override
    public String run(RunnerBasedAgentInput input) {

        // Workflow-side bus is sparsely subscribed — exactly one listener forwards every
        // event to the AgenticanActivity, which on the activity worker side publishes onto
        // the application's main bus where the in-process listener set (persister,
        // knowledge ingestor, metrics, OTel, ...) handles them. Same listener wiring,
        // both runtimes.
        var agenticanActivity = Workflow.newActivityStub(AgenticanActivity.class,
                ActivityOptions.newBuilder().setStartToCloseTimeout(DEFAULT_ACTIVITY_TIMEOUT).build());

        var eventBus = new AgenticanEventBus();
        eventBus.subscribeFirst(new ForwarderListener(agenticanActivity));

        var runner = SmacAgentRunner.builder()
                .llmClient(HOST_ONLY_LLM_CLIENT)
                .eventBus(eventBus)
                .maxIterations(input.maxTurns())
                .timeout(input.timeout())
                .build();

        var agent = Agent.builder().config(input.agentConfig()).runner(runner).build();

        var host = new TemporalAgentLoopHost(eventBus, agenticanActivity, this::awaitHitlReply);

        var toolkits = buildToolkitMap(input);

        var result = runner.run(agent, input.task(), input.taskId(), input.stepId(), input.stepName(),
                input.timeout(), input.skills(), toolkits, null, host);

        return result.text();
    }

    @Override
    public void provideHitlResponse(String checkpointId, HitlResponse response) {

        hitlResponses.put(checkpointId, response);
    }

    private HitlResponse awaitHitlReply(String checkpointId) {

        Workflow.await(() -> hitlResponses.containsKey(checkpointId));

        return hitlResponses.remove(checkpointId);
    }

    private static Map<String, Toolkit> buildToolkitMap(RunnerBasedAgentInput input) {

        var map = new LinkedHashMap<String, Toolkit>();

        for (var def : input.toolDefinitions()) {

            var hitl = input.toolHitlTypes().getOrDefault(def.name(), HitlType.NONE);

            map.put(def.name(), new WorkflowToolkit(def.name(), List.of(def), Map.of(def.name(), hitl)));
        }

        return map;
    }
}
