package ai.agentican.framework.examples.temporal.common;

import ai.agentican.framework.tools.ToolDefinition;

import java.util.List;

public record IdentifyVendorsInput(
        String topic,
        int vendorCount,
        String systemPrompt,
        List<ToolDefinition> tools,
        int maxTurns,
        String llmName) {

    public IdentifyVendorsInput {

        if (topic == null || topic.isBlank())
            throw new IllegalArgumentException("topic is required");

        if (systemPrompt == null || systemPrompt.isBlank())
            throw new IllegalArgumentException("systemPrompt is required");

        if (vendorCount < 1) vendorCount = 5;
        if (tools == null) tools = List.of();
        if (maxTurns < 1) maxTurns = 10;
    }

    public String userTask() {

        return """
               Identify the top %d vendors in %s.
               Return a JSON array of vendor names — names only, no commentary.
               """.formatted(vendorCount, topic);
    }
}
