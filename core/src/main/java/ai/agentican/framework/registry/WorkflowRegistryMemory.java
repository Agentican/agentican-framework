package ai.agentican.framework.registry;

import ai.agentican.framework.orchestration.model.WorkflowDefinition;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class WorkflowRegistryMemory implements WorkflowRegistry {

    private final ConcurrentMap<String, WorkflowDefinition> byId = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, WorkflowDefinition> byName = new ConcurrentHashMap<>();

    @Override
    public WorkflowDefinition register(WorkflowDefinition plan) {

        byId.put(plan.id(), plan);
        byName.put(plan.name(), plan);

        return plan;
    }

    @Override
    public WorkflowDefinition registerIfAbsent(WorkflowDefinition plan) {

        var existing = byId.putIfAbsent(plan.id(), plan);

        if (existing != null) return existing;

        byName.putIfAbsent(plan.name(), plan);

        return plan;
    }

    @Override
    public WorkflowDefinition byId(String id) {

        return byId.get(id);
    }

    @Override
    public WorkflowDefinition byName(String name) {

        return byName.get(name);
    }

    @Override
    public boolean hasById(String id) {

        return byId.containsKey(id);
    }

    @Override
    public boolean hasByName(String name) {

        return byName.containsKey(name);
    }

    @Override
    public Collection<WorkflowDefinition> list() {

        return Collections.unmodifiableCollection(byId.values());
    }

    @Override
    public Map<String, WorkflowDefinition> asMap() {

        return Collections.unmodifiableMap(byId);
    }

    @Override
    public void delete(String ref) {

        var plan = byId.get(ref);

        if (plan == null) plan = byName.get(ref);

        if (plan == null) return;

        byId.remove(plan.id());
        byName.remove(plan.name());
    }
}
