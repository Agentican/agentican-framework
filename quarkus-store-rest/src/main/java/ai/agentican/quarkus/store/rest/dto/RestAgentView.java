package ai.agentican.quarkus.store.rest.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RestAgentView(
        String id,
        String name,
        String role,
        String llm,
        String externalId) {}
