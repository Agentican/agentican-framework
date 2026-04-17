package ai.agentican.framework.orchestration;

import ai.agentican.framework.orchestration.model.Plan;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryPlanRegistry implements PlanRegistry {

    private final ConcurrentMap<String, Plan> byName = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Plan> byId = new ConcurrentHashMap<>();

    @Override
    public Plan register(Plan plan) {

        byName.put(plan.name(), plan);
        byId.put(plan.id(), plan);

        return plan;
    }

    @Override
    public Plan registerIfAbsent(Plan plan) {

        return byName.computeIfAbsent(plan.name(), planName -> {

            byId.put(plan.id(), plan);

            return plan;
        });
    }

    @Override
    public Plan get(String name) {

        return byName.get(name);
    }

    @Override
    public Plan getById(String id) {

        return byId.get(id);
    }

    @Override
    public boolean isRegistered(String name) {

        return byName.containsKey(name);
    }

    @Override
    public Collection<Plan> getAll() {

        return Collections.unmodifiableCollection(byName.values());
    }

    @Override
    public Map<String, Plan> asMap() {

        return Collections.unmodifiableMap(byName);
    }
}
