package ai.agentican.quarkus.store.rest;

import ai.agentican.framework.orchestration.model.WorkflowDefinition;
import ai.agentican.framework.orchestration.model.WorkflowStepAgent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RestPlanRegistryTest {

    private static String plansArrayJson(WorkflowDefinition... plans) throws Exception {

        var mapper = ai.agentican.framework.util.Json.mapper();
        var arr = mapper.createArrayNode();

        for (var p : plans) {

            var view = mapper.createObjectNode();
            view.put("planId", p.id());
            view.put("name", p.name());
            view.put("description", p.description());
            view.set("definition", mapper.valueToTree(p));

            arr.add(view);
        }

        return mapper.writeValueAsString(arr);
    }

    private static WorkflowDefinition samplePlan(String name) {

        return WorkflowDefinition.builder(name, name)
                .step().name("do").agent("analyst").instructions("do the thing").end()
                .build();
    }

    private RestPlanRegistry registryWith(FakeRestCatalogClient client) {

        var registry = new RestPlanRegistry();
        registry.client = client;
        return registry;
    }

    @Test
    void seedPopulatesCacheFromCatalog() throws Exception {

        var p1 = samplePlan("alpha");
        var p2 = samplePlan("beta");

        var client = new FakeRestCatalogClient(plansArrayJson(p1, p2), List.of(), List.of());
        var registry = registryWith(client);

        registry.seed();

        assertEquals(2, registry.list().size());
        assertNotNull(registry.byName("alpha"));
        assertNotNull(registry.byName("beta"));
        assertEquals("alpha", registry.byName("alpha").name());
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

        var local = samplePlan("local-one");

        registry.register(local);

        assertEquals(local, registry.byName("local-one"));
        assertEquals(local, registry.byId(local.id()));
    }

    @Test
    void registerIfAbsentReturnsExistingEntryById() throws Exception {

        var first = samplePlan("shared");

        var client = new FakeRestCatalogClient(plansArrayJson(first), List.of(), List.of());
        var registry = registryWith(client);

        registry.seed();

        var returned = registry.registerIfAbsent(first);

        assertEquals(first.id(), returned.id(), "Existing entry returned when id matches");
        assertEquals(1, registry.list().size());
    }

    @Test
    void registerIfAbsentInsertsNewWhenUnique() throws Exception {

        var client = new FakeRestCatalogClient(plansArrayJson(), List.of(), List.of());
        var registry = registryWith(client);
        registry.seed();

        var fresh = samplePlan("fresh");

        assertSame(fresh, registry.registerIfAbsent(fresh));
        assertEquals(fresh, registry.byName("fresh"));
    }
}
