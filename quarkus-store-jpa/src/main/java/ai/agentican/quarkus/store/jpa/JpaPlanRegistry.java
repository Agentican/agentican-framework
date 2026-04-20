package ai.agentican.quarkus.store.jpa;

import ai.agentican.framework.registry.PlanRegistry;
import ai.agentican.framework.orchestration.model.Plan;
import ai.agentican.framework.orchestration.model.PlanCodec;
import ai.agentican.framework.util.Json;
import ai.agentican.quarkus.store.jpa.entity.PlanEntity;

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
public class JpaPlanRegistry implements PlanRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(JpaPlanRegistry.class);

    private final ConcurrentMap<String, Plan> byId = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> idByName = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> idByExternalId = new ConcurrentHashMap<>();

    @Inject
    Instance<PlanCodec> planCodec;

    @Override
    @Transactional
    public Plan register(Plan plan) {

        var canonical = persistAndAlign(plan);

        byId.put(canonical.id(), canonical);
        idByName.put(canonical.name(), canonical.id());
        if (canonical.externalId() != null)
            idByExternalId.put(canonical.externalId(), canonical.id());

        return canonical;
    }

    @Override
    @Transactional
    public Plan registerIfAbsent(Plan plan) {

        if (plan.externalId() != null && idByExternalId.containsKey(plan.externalId()))
            return byId.get(idByExternalId.get(plan.externalId()));

        var existing = byId.putIfAbsent(plan.id(), plan);

        if (existing != null)
            return existing;

        var canonical = persistAndAlign(plan);

        if (!canonical.id().equals(plan.id())) {
            byId.remove(plan.id());
            byId.put(canonical.id(), canonical);
        }
        idByName.putIfAbsent(canonical.name(), canonical.id());
        if (canonical.externalId() != null)
            idByExternalId.putIfAbsent(canonical.externalId(), canonical.id());

        return canonical;
    }

    @Override
    @Transactional
    public void seed() {

        java.util.List<PlanEntity> rows = PlanEntity.listAll();

        for (var row : rows) {

            try {

                var plan = readPlan(row.definitionJson);
                byId.put(plan.id(), plan);
                idByName.put(plan.name(), plan.id());
                if (row.externalId != null)
                    idByExternalId.put(row.externalId, plan.id());
            }
            catch (Exception ex) {
                LOG.warn("Failed to deserialize plan '{}' ({}): {}", row.name, row.id, ex.getMessage());
            }
        }

        if (!rows.isEmpty())
            LOG.info("JpaPlanRegistry seeded {} plans from catalog", rows.size());
    }

    public Plan getByExternalId(String externalId) {

        var id = idByExternalId.get(externalId);
        return id != null ? byId.get(id) : null;
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

    private Plan persistAndAlign(Plan plan) {

        if (plan.externalId() != null) {

            var existing = (PlanEntity) PlanEntity.find("externalId", plan.externalId()).firstResult();

            if (existing != null) {

                Plan canonical = plan.id().equals(existing.id)
                        ? plan
                        : new Plan(existing.id, plan.name(), plan.description(), plan.params(), plan.steps(), plan.externalId(), plan.outputStep());

                existing.name = canonical.name();
                existing.description = canonical.description();
                existing.definitionJson = serialize(canonical);
                existing.persist();
                return canonical;
            }

            var e = new PlanEntity();
            e.id = plan.id();
            e.externalId = plan.externalId();
            e.name = plan.name();
            e.description = plan.description();
            e.definitionJson = serialize(plan);
            e.createdAt = Instant.now();
            e.persist();
            return plan;
        }

        var existing = (PlanEntity) PlanEntity.findById(plan.id());
        var e = existing != null ? existing : new PlanEntity();

        if (existing == null) {
            e.id = plan.id();
            e.createdAt = Instant.now();
        }

        e.name = plan.name();
        e.description = plan.description();
        e.definitionJson = serialize(plan);

        e.persist();
        return plan;
    }

    private static String serialize(Plan plan) {

        try {
            return Json.writeValueAsString(plan);
        }
        catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize plan '" + plan.name() + "': " + ex.getMessage(), ex);
        }
    }

    private Plan readPlan(String json) throws Exception {

        if (planCodec != null && planCodec.isResolvable())
            return planCodec.get().fromJson(json, Plan.class);

        return Json.readValue(json, Plan.class);
    }
}
