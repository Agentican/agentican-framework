package ai.agentican.quarkus.rest.dto;

import ai.agentican.framework.orchestration.model.WorkflowDefinition;

public record UpsertPlanRequest(WorkflowDefinition definition) {}
