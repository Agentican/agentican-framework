package ai.agentican.quarkus.store.rest.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RestSkillView(
        String id,
        String name,
        String instructions,
        String externalId) {}
