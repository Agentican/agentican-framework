package ai.agentican.quarkus.event;

import ai.agentican.framework.state.TaskLog;
import ai.agentican.framework.orchestration.execution.TaskStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;

public record TaskCompletedEvent(String taskId, String taskName, TaskStatus status, @JsonIgnore TaskLog log) {

    public boolean succeeded() {

        return status == TaskStatus.COMPLETED;
    }
}
