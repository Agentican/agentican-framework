package ai.agentican.framework.vector;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class VectorIndexRegistry {

    private final Map<String, VectorIndex> entries = new LinkedHashMap<>();

    public void register(VectorIndex kb) {

        if (kb == null)
            throw new IllegalArgumentException("VectorIndex is required");

        if (kb.name() == null || kb.name().isBlank())
            throw new IllegalArgumentException("VectorIndex name is required");

        if (entries.containsKey(kb.name()))
            throw new IllegalStateException("VectorIndex '" + kb.name() + "' is already registered");

        entries.put(kb.name(), kb);
    }

    public VectorIndex get(String name) {

        return entries.get(name);
    }

    public boolean contains(String name) {

        return entries.containsKey(name);
    }

    public Set<String> names() {

        return Set.copyOf(entries.keySet());
    }

    public int size() {

        return entries.size();
    }

    public boolean isEmpty() {

        return entries.isEmpty();
    }
}
