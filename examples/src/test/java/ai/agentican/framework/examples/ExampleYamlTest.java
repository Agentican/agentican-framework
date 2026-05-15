package ai.agentican.framework.examples;

import ai.agentican.framework.Agentican;
import ai.agentican.framework.config.CatalogConfig;
import ai.agentican.framework.config.EngineConfig;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class ExampleYamlTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "candidate-screening.yaml",
            "content-pipeline.yaml",
            "customer-onboarding.yaml",
            "daily-standup-digest.yaml",
            "incident-response.yaml",
            "invoice-processing.yaml",
            "lead-qualification.yaml",
            "meeting-prep-brief.yaml",
            "interview-loop-design.yaml",
            "message-variants.yaml",
            "churn-risk.yaml",
            "pr-review.yaml",
            "postmortem.yaml",
            "expense-audit.yaml",
            "objection-handler.yaml",
            "email-triage.yaml",
            "data-migration.yaml",
            "feature-spec.yaml",
            "dpa-review.yaml",
            "vendor-selection.yaml",
            "market-brief.yaml",
            "research-synthesis.yaml",
            "fraud-triage.yaml",
            "article-pipeline.yaml",
            "contributor-recognition.yaml"
    })
    void workflowYamlLoadsAndBuildsPlan(String resourceName) throws Exception {

        var catalog = loadCatalog(resourceName);

        assertFalse(catalog.workflows().isEmpty(), resourceName + ": expected at least one definition");

        var plan = catalog.workflows().getFirst().toDefinition();

        assertNotNull(plan.id());
        assertNotNull(plan.name());
        assertFalse(plan.steps().isEmpty(), resourceName + ": definition has no steps");
    }

    @org.junit.jupiter.api.Test
    void engineYamlHasLlm() throws Exception {

        var engine = EngineConfig.load(resourcePath("engine.yaml"));

        assertFalse(engine.llm().isEmpty(), "engine.yaml: expected at least one llm entry");
    }

    @org.junit.jupiter.api.Test
    void quickTaskCatalogIsPlannerOnly() throws Exception {

        var catalog = loadCatalog("quick-task.yaml");

        assertTrue(catalog.agents().isEmpty(), "quick-task.yaml: planner-only, no agents expected");
        assertTrue(catalog.workflows().isEmpty(), "quick-task.yaml: planner-only, no workflows expected");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "resume-review.yaml",
            "brand-taglines.yaml",
            "call-debrief.yaml",
            "commit-message.yaml",
            "alert-review.yaml",
            "budget-variance-analysis.yaml",
            "discovery-call-prep.yaml",
            "meeting-minutes.yaml",
            "refund-approval.yaml",
            "promotion-package.yaml",
            "incident-postmortem.yaml",
            "deal-discount.yaml",
            "landing-copy.yaml",
            "okr-draft.yaml",
            "security-advisory.yaml",
            "dashboard-scope.yaml"
    })
    void agentYamlDefinesAnAgent(String resourceName) throws Exception {

        var catalog = loadCatalog(resourceName);

        assertFalse(catalog.agents().isEmpty(), resourceName + ": expected at least one agent");

        var agent = catalog.agents().getFirst();

        assertNotNull(agent.name());
        assertNotNull(agent.role());
    }

    /**
     * Smoke test: build a real {@link Agentican} from the shared engine YAML +
     * a per-example catalog YAML to confirm the example wiring path works.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "alert-review.yaml",
            "quick-task.yaml",
            "vendor-selection.yaml",
            "market-brief.yaml"
    })
    void exampleYamlBootsViaBothAxes(String resourceName) throws Exception {

        var enginePath = resourcePath("engine.yaml");
        var catalogPath = resourcePath(resourceName);

        try (var agentican = Agentican.builder()
                .configuration().yaml().path(enginePath).end()
                .registry().yaml().path(catalogPath).end()
                .build()) {

            assertNotNull(agentican);
        }
    }

    private static CatalogConfig loadCatalog(String resourceName) throws Exception {

        return CatalogConfig.load(resourcePath(resourceName));
    }

    private static Path resourcePath(String resourceName) throws Exception {

        return Path.of(Objects.requireNonNull(
                ExampleYamlTest.class.getResource("/" + resourceName)).toURI());
    }
}
