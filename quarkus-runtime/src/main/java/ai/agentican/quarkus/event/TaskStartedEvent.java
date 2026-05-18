package ai.agentican.quarkus.event;

import ai.agentican.framework.state.WorkflowRunLog;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * @param parentTaskId  set when this task is a sub-task (loop iteration, branch sub-task);
 *                      {@code null} for top-level tasks. Lets observers (e.g. SSE bubbling)
 *                      walk the parent chain without a store fetch.
 */
public record TaskStartedEvent(String taskId, String taskName, String parentTaskId,
                                @JsonIgnore WorkflowRunLog log) { }
