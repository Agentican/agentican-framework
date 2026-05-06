package ai.agentican.framework.orchestration.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = WorkflowStepAgent.class, name = "agent"),
        @JsonSubTypes.Type(value = WorkflowStepLoop.class, name = "loop"),
        @JsonSubTypes.Type(value = WorkflowStepBranch.class, name = "branch"),
        @JsonSubTypes.Type(value = WorkflowStepCode.class, name = "code")
})
public sealed interface WorkflowStep permits WorkflowStepAgent, WorkflowStepLoop, WorkflowStepBranch, WorkflowStepCode {

    String name();

    List<String> dependencies();

    boolean hitl();
}
