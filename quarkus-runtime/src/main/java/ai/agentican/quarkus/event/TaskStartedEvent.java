package ai.agentican.quarkus.event;

import ai.agentican.framework.state.WorkflowRunLog;
import com.fasterxml.jackson.annotation.JsonIgnore;

public record TaskStartedEvent(String taskId, String taskName, @JsonIgnore WorkflowRunLog log) {}
