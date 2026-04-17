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

    public List<String> allToolNames() {

        var names = new ArrayList<String>();

        for (var toolkit : toolkits.values())
            for (var td : toolkit.toolDefinitions())
                names.add(td.name());

        return names;
    }

    public List<ToolDefinition> allToolDefinitions() {

        var defs = new ArrayList<ToolDefinition>();

        for (var toolkit : toolkits.values())
            defs.addAll(toolkit.toolDefinitions());

        return defs;
    }

    public List<ToolDefinition> toolDefinitions(List<String> toolNames) {

        if (toolNames == null || toolNames.isEmpty()) return List.of();

        var wanted = new LinkedHashSet<>(toolNames);
        var result = new ArrayList<ToolDefinition>();

        for (var toolkit : toolkits.values())
            for (var td : toolkit.toolDefinitions())
                if (wanted.contains(td.name()))
                    result.add(td);

        return result;
    }

    public Map<String, Toolkit> scopeForStep(List<String> toolNames) {

        if (toolNames == null || toolNames.isEmpty()) return Map.of();

        var wanted = new LinkedHashSet<>(toolNames);
        var scoped = new LinkedHashMap<String, Toolkit>();

        for (var toolkit : toolkits.values())
            for (var td : toolkit.toolDefinitions())
                if (wanted.contains(td.name()) && !scoped.containsKey(td.name()))
                    scoped.put(td.name(), toolkit);

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
