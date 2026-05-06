package ai.agentican.quarkus.rest.dto;

import ai.agentican.framework.orchestration.model.WorkflowDefinition;
import ai.agentican.framework.orchestration.model.WorkflowParam;
import ai.agentican.framework.orchestration.model.WorkflowStep;
import ai.agentican.framework.util.Ids;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkflowDefinitionInput(
        String id,
        String name,
        String description,
        List<WorkflowParam> params,
        List<WorkflowStep> steps,
        String outputStep) {

    public WorkflowDefinition toDefinition() {

        var resolvedId = (id == null || id.isBlank()) ? Ids.generate() : id;

        return new WorkflowDefinition(resolvedId, name, description,
                params == null ? List.of() : params,
                steps,
                outputStep);
    }

    public WorkflowDefinition toDefinition(String fallbackId) {

        var resolvedId = (id == null || id.isBlank()) ? fallbackId : id;

        return new WorkflowDefinition(resolvedId, name, description,
                params == null ? List.of() : params,
                steps,
                outputStep);
    }
}
