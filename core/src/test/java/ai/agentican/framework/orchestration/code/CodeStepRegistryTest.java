package ai.agentican.framework.orchestration.code;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CodeStepRegistryTest {

    record Greeting(String name) { }

    @Test
    void registersAndLooksUp() {

        var registry = new CodeStepRegistry();

        CodeStep<Greeting, String> step = (input, ctx) -> "hi " + input.name();
        var spec = new CodeStepSpec<>("greet", null, Greeting.class, String.class);

        registry.register(spec, step);

        assertTrue(registry.contains("greet"));
        var registered = registry.get("greet");
        assertSame(spec, registered.spec());
        assertSame(step, registered.executor());
        assertEquals(1, registry.size());
    }

    @Test
    void duplicateSlugThrows() {

        var registry = new CodeStepRegistry();

        registry.register(new CodeStepSpec<>("slug", null, Void.class, String.class),
                (input, ctx) -> "a");

        assertThrows(IllegalStateException.class, () ->
                registry.register(new CodeStepSpec<>("slug", null, Void.class, String.class),
                        (input, ctx) -> "b"));
    }

    @Test
    void nullSpecThrows() {

        var registry = new CodeStepRegistry();

        assertThrows(IllegalArgumentException.class, () ->
                registry.register(null, (i, c) -> "x"));
    }

    @Test
    void nullExecutorThrows() {

        var registry = new CodeStepRegistry();

        assertThrows(IllegalArgumentException.class, () ->
                registry.register(new CodeStepSpec<>("slug", null, Void.class, String.class), null));
    }

    @Test
    void missingLookupReturnsNull() {

        var registry = new CodeStepRegistry();

        assertNull(registry.get("nope"));
        assertFalse(registry.contains("nope"));
    }
}
