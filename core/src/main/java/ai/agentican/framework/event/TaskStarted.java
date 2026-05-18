package ai.agentican.framework.event;

import ai.agentican.framework.orchestration.model.WorkflowDefinition;
import ai.agentican.framework.state.RuntimeOwner;

import java.util.Map;

public record TaskStarted(
        String taskId,
        String taskName,
        WorkflowDefinition plan,
        Map<String, String> params,
        String parentTaskId,
        String parentStepId,
        int iterationIndex,
        RuntimeOwner runtime,
        String temporalWorkflowId) implements AgenticanEvent {

    public TaskStarted {

        if (params == null)  params = Map.of();
        if (runtime == null) runtime = RuntimeOwner.IN_PROCESS;
    }
}
