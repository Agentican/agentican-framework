package ai.agentican.quarkus.store.rest;

import ai.agentican.framework.orchestration.model.Plan;
import ai.agentican.framework.orchestration.model.PlanStepAgent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RestPlanRegistryTest {

    private static String plansArrayJson(Plan... plans) throws Exception {

        var mapper = ai.agentican.framework.util.Json.mapper();
        var arr = mapper.createArrayNode();

        for (var p : plans) {

            var view = mapper.createObjectNode();
            view.put("planId", p.id());
            view.put("name", p.name());
            view.put("description", p.description());
            view.set("plan", mapper.valueToTree(p));

            arr.add(view);
        }

        return mapper.writeValueAsString(arr);
    }

    private static Plan samplePlan(String name, String externalId) {

        return Plan.builder(name)
                .externalId(externalId)
                .step(PlanStepAgent.builder("do")
                        .agent("analyst")
                        .instructions("do the thing")
                        .build())
                .build();
    }

    private RestPlanRegistry registryWith(FakeRestCatalogClient client) {

        var registry = new RestPlanRegistry();
        registry.client = client;
        return registry;
    }

    @Test
    void seedPopulatesCacheFromCatalog() throws Exception {

        var p1 = samplePlan("alpha", "ext.alpha");
        var p2 = samplePlan("beta", null);

        var client = new FakeRestCatalogClient(plansArrayJson(p1, p2), List.of(), List.of());
        var registry = registryWith(client);

        registry.seed();

        assertEquals(2, registry.getAll().size());
        assertNotNull(registry.get("alpha"));
        assertNotNull(registry.get("beta"));
        assertEquals("alpha", registry.get("alpha").name());
    }

    @Test
    void seedFailsFastOnClientError() {

        var client = new FakeRestCatalogClient("[]", List.of(), List.of());
        client.failNextPlansCall();

        var registry = registryWith(client);

        var ex = assertThrows(IllegalStateException.class, registry::seed);
        assertTrue(ex.getMessage().contains("Failed to seed plans"));
    }

    @Test
    void registerAddsLocalEntry() throws Exception {

        var client = new FakeRestCatalogClient(plansArrayJson(), List.of(), List.of());
        var registry = registryWith(client);
        registry.seed();

        var local = samplePlan("local-one", "ext.local");

        registry.register(local);

        assertEquals(local, registry.get("local-one"));
        assertEquals(local, registry.getById(local.id()));
    }

    @Test
    void registerIfAbsentDedupesByExternalId() throws Exception {

        var first = samplePlan("shared", "ext.shared");

        var client = new FakeRestCatalogClient(plansArrayJson(first), List.of(), List.of());
        var registry = registryWith(client);

        registry.seed();

        var duplicate = samplePlan("shared-variant", "ext.shared");

        var returned = registry.registerIfAbsent(duplicate);

        assertEquals(first.id(), returned.id(), "Existing entry returned when externalId matches");
        assertEquals(1, registry.getAll().size());
    }

    @Test
    void registerIfAbsentInsertsNewWhenUnique() throws Exception {

        var client = new FakeRestCatalogClient(plansArrayJson(), List.of(), List.of());
        var registry = registryWith(client);
        registry.seed();

        var fresh = samplePlan("fresh", "ext.fresh");

        assertSame(fresh, registry.registerIfAbsent(fresh));
        assertEquals(fresh, registry.get("fresh"));
    }
}
