package ai.agentican.framework.tools;

import java.util.*;

public class ToolkitRegistry implements AutoCloseable {

    private final Map<String, Toolkit> toolkits = new LinkedHashMap<>();

    public void register(String slug, Toolkit toolkit) {

        toolkits.put(slug, toolkit);
    }

    public Toolkit get(String slug) {

        return toolkits.get(slug);
    }

    public List<String> slugs() {

        return List.copyOf(toolkits.keySet());
    }

    public List<ToolDefinition> toolDefinitions(List<String> slugs) {

        return slugs.stream()
                .map(toolkits::get)
                .filter(Objects::nonNull)
                .flatMap(tk -> tk.toolDefinitions().stream())
                .toList();
    }

    public Map<String, Toolkit> scopeForStep(List<String> slugs) {

        if (slugs == null || slugs.isEmpty())
            return Map.of();

        var scoped = new LinkedHashMap<String, Toolkit>();
        var toolOwners = new LinkedHashMap<String, String>(); // tool name → slug that owns it

        for (var slug : slugs) {

            var toolkit = toolkits.get(slug);

            if (toolkit == null) continue;

            for (var td : toolkit.toolDefinitions()) {

                var existingSlug = toolOwners.get(td.name());

                if (existingSlug != null)
                    throw new IllegalStateException(
                            "Tool name '" + td.name() + "' is defined by both toolkit '" + existingSlug
                                    + "' and toolkit '" + slug + "'. "
                                    + "Scope them to different steps or rename the conflicting tool.");

                scoped.put(td.name(), toolkit);
                toolOwners.put(td.name(), slug);
            }
        }

        return scoped;
    }

    @Override
    public void close() {

        toolkits.values().stream()
                .distinct()
                .filter(t -> t instanceof AutoCloseable)
                .forEach(t -> {

                    try {
                        ((AutoCloseable) t).close();
                    }
                    catch (Exception _) {}
                });
    }
}
