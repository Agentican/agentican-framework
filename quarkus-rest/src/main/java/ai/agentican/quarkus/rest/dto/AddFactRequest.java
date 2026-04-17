package ai.agentican.quarkus.rest.dto;

import java.util.List;

public record AddFactRequest(String name, String content, List<String> tags) {}
