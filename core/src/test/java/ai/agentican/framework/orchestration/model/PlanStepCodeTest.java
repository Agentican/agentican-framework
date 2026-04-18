package ai.agentican.framework.orchestration.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlanStepCodeTest {

    record Inputs(String url) { }

    @Test
    void requiresName() {

        assertThrows(IllegalArgumentException.class,
                () -> new PlanStepCode<>(null, "slug", null, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new PlanStepCode<>("", "slug", null, List.of()));
    }

    @Test
    void requiresCodeSlug() {

        assertThrows(IllegalArgumentException.class,
                () -> new PlanStepCode<>("name", null, null, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new PlanStepCode<>("name", "", null, List.of()));
    }

    @Test
    void hitlIsAlwaysFalse() {

        var step = new PlanStepCode<>("validate", "slug", null, List.of());

        assertFalse(step.hitl());
    }

    @Test
    void builderProducesEquivalentRecord() {

        var inputs = new Inputs("http://example");

        var built = PlanStepCode.<Inputs>builder("validate")
                .code("validate-payment")
                .inputs(inputs)
                .dependency("extract")
                .dependency("lookup")
                .build();

        assertEquals("validate", built.name());
        assertEquals("validate-payment", built.codeSlug());
        assertSame(inputs, built.inputs());
        assertEquals(List.of("extract", "lookup"), built.dependencies());
        assertFalse(built.hitl());
    }

    @Test
    void nullDependenciesBecomeEmptyList() {

        var step = new PlanStepCode<>("name", "slug", null, null);

        assertEquals(List.of(), step.dependencies());
    }

    @Test
    void inputsMayBeNull() {

        var step = new PlanStepCode<>("name", "slug", null, List.of());

        assertNull(step.inputs());
    }
}
