package ai.agentican.quarkus.event;

import ai.agentican.framework.state.WorkflowRunLog;
import ai.agentican.framework.orchestration.execution.WorkflowRunStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;

public record TaskCompletedEvent(String taskId, String taskName, WorkflowRunStatus status, @JsonIgnore WorkflowRunLog log) {

    public boolean succeeded() {

        return status == WorkflowRunStatus.COMPLETED;
    }
}
