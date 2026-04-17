package ai.agentican.quarkus.event;

import ai.agentican.framework.state.TaskLog;
import com.fasterxml.jackson.annotation.JsonIgnore;

public record TaskStartedEvent(String taskId, String taskName, @JsonIgnore TaskLog log) {}
