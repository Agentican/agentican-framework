package ai.agentican.quarkus.rest.dto;

import ai.agentican.framework.orchestration.model.WorkflowDefinition;

public record WorkflowView(String id, String name, String description, WorkflowDefinition definition) {

    public static WorkflowView of(WorkflowDefinition definition) {

        return new WorkflowView(definition.id(), definition.name(),
                definition.description(), definition);
    }
}
