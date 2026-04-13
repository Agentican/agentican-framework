package ai.agentican.framework.orchestration.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = PlanStepAgent.class, name = "agent"),
        @JsonSubTypes.Type(value = PlanStepLoop.class, name = "loop"),
        @JsonSubTypes.Type(value = PlanStepBranch.class, name = "branch")
})
public sealed interface PlanStep permits PlanStepAgent, PlanStepLoop, PlanStepBranch {

    String name();

    List<String> dependencies();

    boolean hitl();
}
