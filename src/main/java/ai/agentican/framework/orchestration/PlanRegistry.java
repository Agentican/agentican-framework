package ai.agentican.framework.orchestration;

import ai.agentican.framework.orchestration.model.Plan;

import java.util.Collection;
import java.util.Map;

public interface PlanRegistry {

    Plan register(Plan plan);

    Plan registerIfAbsent(Plan plan);

    Plan get(String name);

    Plan getById(String id);

    boolean isRegistered(String name);

    Collection<Plan> getAll();

    Map<String, Plan> asMap();

    default void seed() { }
}
