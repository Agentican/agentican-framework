package ai.agentican.framework.orchestration.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowStepCodeTest {

    record Inputs(String url) { }

    @Test
    void requiresName() {

        assertThrows(IllegalArgumentException.class,
                () -> new WorkflowStepCode<>(null, "slug", null, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new WorkflowStepCode<>("", "slug", null, List.of()));
    }

    @Test
    void requiresCodeSlug() {

        assertThrows(IllegalArgumentException.class,
                () -> new WorkflowStepCode<>("name", null, null, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new WorkflowStepCode<>("name", "", null, List.of()));
    }

    @Test
    void hitlIsAlwaysFalse() {

        var step = new WorkflowStepCode<>("validate", "slug", null, List.of());

        assertFalse(step.hitl());
    }

    @Test
    void builderProducesEquivalentRecord() {

        var input = new Inputs("http://example");

        var built = WorkflowStepCode.<Inputs>builder("validate")
                .code("validate-payment")
                .input(input)
                .dependency("extract")
                .dependency("lookup")
                .build();

        assertEquals("validate", built.name());
        assertEquals("validate-payment", built.codeSlug());
        assertSame(input, built.input());
        assertEquals(List.of("extract", "lookup"), built.dependencies());
        assertFalse(built.hitl());
    }

    @Test
    void nullDependenciesBecomeEmptyList() {

        var step = new WorkflowStepCode<>("name", "slug", null, null);

        assertEquals(List.of(), step.dependencies());
    }

    @Test
    void inputMayBeNull() {

        var step = new WorkflowStepCode<>("name", "slug", null, List.of());

        assertNull(step.input());
    }
}
