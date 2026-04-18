package ai.agentican.framework.orchestration.code;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CodeStepSpecTest {

    record In(String x) { }
    record Out(int y) { }

    @Test
    void requiresSlug() {

        assertThrows(IllegalArgumentException.class, () ->
                new CodeStepSpec<>(null, "desc", In.class, Out.class));
        assertThrows(IllegalArgumentException.class, () ->
                new CodeStepSpec<>("", "desc", In.class, Out.class));
    }

    @Test
    void nullTypesDefaultToVoid() {

        var spec = new CodeStepSpec<>("slug", null, null, null);

        assertEquals(Void.class, spec.inputType());
        assertEquals(Void.class, spec.outputType());
    }

    @Test
    void factoriesProduceEquivalentSpecs() {

        var a = CodeStepSpec.of("slug", In.class, Out.class);
        assertEquals("slug", a.slug());
        assertNull(a.description());
        assertEquals(In.class, a.inputType());
        assertEquals(Out.class, a.outputType());

        var b = CodeStepSpec.of("slug", "desc", In.class, Out.class);
        assertEquals("desc", b.description());
    }
}
