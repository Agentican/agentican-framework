package ai.agentican.framework.tools.scratchpad;

public record ScratchpadEntry(
        String key,
        String description,
        String details) {

    public ScratchpadEntry {

        if (key == null || key.isBlank())
            throw new IllegalArgumentException("Key is required");

        if (description == null || description.isBlank())
            throw new IllegalArgumentException("Description is required");

        if (details == null || details.isBlank())
            throw new IllegalArgumentException("Details are required");
    }
}
