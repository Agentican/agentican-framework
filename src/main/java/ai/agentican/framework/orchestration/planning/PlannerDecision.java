package ai.agentican.framework.orchestration.planning;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = PlannerOutput.class, name = "create"),
        @JsonSubTypes.Type(value = ReuseExisting.class, name = "reuse")
})
public sealed interface PlannerDecision permits PlannerOutput, ReuseExisting {}
