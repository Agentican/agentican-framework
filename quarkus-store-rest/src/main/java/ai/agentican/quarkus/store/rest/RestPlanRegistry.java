package ai.agentican.quarkus.store.rest;

import ai.agentican.framework.orchestration.model.Plan;
import ai.agentican.framework.orchestration.model.PlanCodec;
import ai.agentican.framework.registry.PlanRegistry;
import ai.agentican.framework.util.Json;

import com.fasterxml.jackson.databind.JsonNode;

import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@ApplicationScoped
@IfBuildProperty(name = "agentican.store.backend", stringValue = "rest")
public class RestPlanRegistry implements PlanRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(RestPlanRegistry.class);

    private final ConcurrentMap<String, Plan> byId = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> idByName = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> idByExternalId = new ConcurrentHashMap<>();

    @Inject
    @RestClient
    RestCatalogClient client;

    @Inject
    Instance<PlanCodec> planCodec;

    @Override
    public void seed() {

        try {

            var root = Json.mapper().readTree(client.listPlansJson());

            if (root == null || !root.isArray()) {
                LOG.warn("Catalog /plans did not return a JSON array; skipping seed");
                return;
            }

            var loaded = 0;

            for (var node : root) {

                var plan = readPlanFromView(node);

                if (plan == null) continue;

                byId.put(plan.id(), plan);
                idByName.put(plan.name(), plan.id());

                if (plan.externalId() != null)
                    idByExternalId.put(plan.externalId(), plan.id());

                loaded++;
            }

            if (loaded > 0)
                LOG.info("RestPlanRegistry seeded {} plans from catalog", loaded);
        }
        catch (Exception e) {

            throw new IllegalStateException(
                    "Failed to seed plans from REST catalog (check quarkus.rest-client.agentican-catalog.url): "
                            + e.getMessage(), e);
        }
    }

    @Override
    public Plan register(Plan plan) {

        byId.put(plan.id(), plan);
        idByName.put(plan.name(), plan.id());

        if (plan.externalId() != null)
            idByExternalId.put(plan.externalId(), plan.id());

        LOG.debug("Registered plan '{}' locally (not persisted to central catalog)", plan.name());
        return plan;
    }

    @Override
    public Plan registerIfAbsent(Plan plan) {

        if (plan.externalId() != null && idByExternalId.containsKey(plan.externalId()))
            return byId.get(idByExternalId.get(plan.externalId()));

        var existing = byId.putIfAbsent(plan.id(), plan);

        if (existing != null) return existing;

        idByName.putIfAbsent(plan.name(), plan.id());

        if (plan.externalId() != null)
            idByExternalId.putIfAbsent(plan.externalId(), plan.id());

        return plan;
    }

    @Override
    public Plan get(String name) {

        var id = idByName.get(name);
        return id != null ? byId.get(id) : null;
    }

    @Override
    public Plan getById(String id) { return byId.get(id); }

    @Override
    public boolean isRegistered(String name) { return idByName.containsKey(name); }

    @Override
    public Collection<Plan> getAll() { return Collections.unmodifiableCollection(byId.values()); }

    @Override
    public Map<String, Plan> asMap() { return Collections.unmodifiableMap(byId); }

    private Plan readPlanFromView(JsonNode viewNode) {

        var planNode = viewNode != null ? viewNode.get("plan") : null;

        if (planNode == null || planNode.isNull()) {
            LOG.warn("Catalog plan entry is missing 'plan' field; skipping");
            return null;
        }

        try {

            var planJson = Json.mapper().writeValueAsString(planNode);
            return readPlan(planJson);
        }
        catch (Exception e) {

            var name = viewNode.hasNonNull("name") ? viewNode.get("name").asText() : "<unknown>";
            LOG.warn("Failed to deserialize plan '{}': {}", name, e.getMessage());
            return null;
        }
    }

    private Plan readPlan(String planJson) throws Exception {

        if (planCodec != null && planCodec.isResolvable())
            return planCodec.get().fromJson(planJson, Plan.class);

        return Json.readValue(planJson, Plan.class);
    }
}
