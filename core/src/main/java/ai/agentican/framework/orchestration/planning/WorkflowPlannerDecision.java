package ai.agentican.framework.orchestration.planning;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = WorkflowPlanned.class, name = "create"),
        @JsonSubTypes.Type(value = WorkflowSelected.class, name = "reuse")
})
public sealed interface WorkflowPlannerDecision permits WorkflowPlanned, WorkflowSelected {}
