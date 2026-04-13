package ai.agentican.framework.orchestration;

import ai.agentican.framework.orchestration.model.Plan;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class PlanRegistry {

    private final ConcurrentMap<String, Plan> byName = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Plan> byId = new ConcurrentHashMap<>();

    public Plan register(Plan plan) {

        byName.put(plan.name(), plan);
        byId.put(plan.id(), plan);

        return plan;
    }

    public Plan registerIfAbsent(Plan plan) {

        return byName.computeIfAbsent(plan.name(), planName -> {

            byId.put(plan.id(), plan);

            return plan;
        });
    }

    public Plan get(String name) {

        return byName.get(name);
    }

    public Plan getById(String id) {

        return byId.get(id);
    }

    public boolean isRegistered(String name) {

        return byName.containsKey(name);
    }

    public Collection<Plan> getAll() {

        return Collections.unmodifiableCollection(byName.values());
    }

    public Map<String, Plan> asMap() {

        return Collections.unmodifiableMap(byName);
    }
}
