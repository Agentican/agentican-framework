package ai.agentican.quarkus.store.rest;

import ai.agentican.framework.orchestration.model.WorkflowDefinition;
import ai.agentican.framework.orchestration.model.WorkflowDefinitionCodec;
import ai.agentican.framework.registry.WorkflowRegistry;
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
public class RestPlanRegistry implements WorkflowRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(RestPlanRegistry.class);

    private final ConcurrentMap<String, WorkflowDefinition> byId = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> idByName = new ConcurrentHashMap<>();

    @Inject
    @RestClient
    RestCatalogClient client;

    @Inject
    Instance<WorkflowDefinitionCodec> planCodec;

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
    public WorkflowDefinition register(WorkflowDefinition plan) {

        byId.put(plan.id(), plan);
        idByName.put(plan.name(), plan.id());

        LOG.debug("Registered definition '{}' locally (not persisted to central catalog)", plan.name());
        return plan;
    }

    @Override
    public WorkflowDefinition registerIfAbsent(WorkflowDefinition plan) {

        var existing = byId.putIfAbsent(plan.id(), plan);

        if (existing != null) return existing;

        idByName.putIfAbsent(plan.name(), plan.id());

        return plan;
    }

    @Override
    public WorkflowDefinition byName(String name) {

        var id = idByName.get(name);
        return id != null ? byId.get(id) : null;
    }

    @Override
    public WorkflowDefinition byId(String id) { return byId.get(id); }

    @Override
    public boolean hasById(String id) { return byId.containsKey(id); }

    @Override
    public boolean hasByName(String name) { return idByName.containsKey(name); }

    @Override
    public Collection<WorkflowDefinition> list() { return Collections.unmodifiableCollection(byId.values()); }

    @Override
    public Map<String, WorkflowDefinition> asMap() { return Collections.unmodifiableMap(byId); }

    private WorkflowDefinition readPlanFromView(JsonNode viewNode) {

        var planNode = viewNode != null ? viewNode.get("definition") : null;

        if (planNode == null || planNode.isNull()) {
            LOG.warn("Catalog definition entry is missing 'definition' field; skipping");
            return null;
        }

        try {

            var planJson = Json.mapper().writeValueAsString(planNode);
            return readPlan(planJson);
        }
        catch (Exception e) {

            var name = viewNode.hasNonNull("name") ? viewNode.get("name").asText() : "<unknown>";
            LOG.warn("Failed to deserialize definition '{}': {}", name, e.getMessage());
            return null;
        }
    }

    private WorkflowDefinition readPlan(String planJson) throws Exception {

        if (planCodec != null && planCodec.isResolvable())
            return planCodec.get().fromJson(planJson, WorkflowDefinition.class);

        return Json.readValue(planJson, WorkflowDefinition.class);
    }
}
