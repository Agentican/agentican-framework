package ai.agentican.quarkus.store.jpa;

import ai.agentican.framework.registry.WorkflowRegistry;
import ai.agentican.framework.orchestration.model.WorkflowDefinition;
import ai.agentican.framework.orchestration.model.WorkflowDefinitionCodec;
import ai.agentican.framework.util.Json;
import ai.agentican.quarkus.store.jpa.entity.WorkflowEntity;

import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@ApplicationScoped
@IfBuildProperty(name = "agentican.store.backend", stringValue = "jpa", enableIfMissing = true)
public class JpaWorkflowRegistry implements WorkflowRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(JpaWorkflowRegistry.class);

    private final ConcurrentMap<String, WorkflowDefinition> byId = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> idByName = new ConcurrentHashMap<>();

    @Inject
    Instance<WorkflowDefinitionCodec> planCodec;

    @Override
    @Transactional
    public WorkflowDefinition register(WorkflowDefinition plan) {

        persist(plan);

        byId.put(plan.id(), plan);
        idByName.put(plan.name(), plan.id());

        return plan;
    }

    @Override
    @Transactional
    public WorkflowDefinition registerIfAbsent(WorkflowDefinition plan) {

        var existing = byId.putIfAbsent(plan.id(), plan);
        if (existing != null) return existing;

        persist(plan);
        idByName.putIfAbsent(plan.name(), plan.id());

        return plan;
    }

    @Override
    @Transactional
    public void seed() {

        java.util.List<WorkflowEntity> rows = WorkflowEntity.listAll();

        for (var row : rows) {

            try {

                var plan = readPlan(row.definitionJson);
                byId.put(plan.id(), plan);
                idByName.put(plan.name(), plan.id());
            }
            catch (Exception ex) {
                LOG.warn("Failed to deserialize workflow definition '{}' ({}): {}", row.name, row.id, ex.getMessage());
            }
        }

        if (!rows.isEmpty())
            LOG.info("JpaWorkflowRegistry seeded {} workflow definitions from catalog", rows.size());
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

    @Override
    @Transactional
    public void delete(String ref) {

        var plan = resolve(ref);

        if (plan == null) {
            LOG.debug("delete('{}'): no workflow definition registered under this ref", ref);
            return;
        }

        WorkflowEntity.deleteById(plan.id());

        byId.remove(plan.id());
        idByName.remove(plan.name());

        LOG.info("WorkflowDefinition '{}' (id={}) deleted from catalog", plan.name(), plan.id());
    }

    private WorkflowDefinition resolve(String ref) {

        var byIdHit = byId.get(ref);
        if (byIdHit != null) return byIdHit;

        return byName(ref);
    }

    private void persist(WorkflowDefinition plan) {

        var existing = (WorkflowEntity) WorkflowEntity.findById(plan.id());
        var e = existing != null ? existing : new WorkflowEntity();

        if (existing == null) {
            e.id = plan.id();
            e.createdAt = Instant.now();
        }

        e.name = plan.name();
        e.description = plan.description();
        e.definitionJson = serialize(plan);

        e.persist();
    }

    private static String serialize(WorkflowDefinition plan) {

        try {
            return Json.writeValueAsString(plan);
        }
        catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize definition '" + plan.name() + "': " + ex.getMessage(), ex);
        }
    }

    private WorkflowDefinition readPlan(String json) throws Exception {

        if (planCodec != null && planCodec.isResolvable())
            return planCodec.get().fromJson(json, WorkflowDefinition.class);

        return Json.readValue(json, WorkflowDefinition.class);
    }
}
