package ai.agentican.framework.examples;

import ai.agentican.framework.config.RuntimeConfig;

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
            "research-synthesis.yaml",
            "fraud-triage.yaml",
            "article-pipeline.yaml",
            "contributor-recognition.yaml"
    })
    void workflowYamlLoadsAndBuildsPlan(String resourceName) throws Exception {

        var config = loadConfig(resourceName);

        assertFalse(config.plans().isEmpty(), resourceName + ": expected at least one plan");

        var plan = config.plans().getFirst().toPlan();

        assertNotNull(plan.id());
        assertNotNull(plan.name());
        assertFalse(plan.steps().isEmpty(), resourceName + ": plan has no steps");
    }

    @org.junit.jupiter.api.Test
    void quickTaskYamlHasLlmOnly() throws Exception {

        var config = loadConfig("quick-task.yaml");

        assertFalse(config.llm().isEmpty(), "quick-task.yaml: expected at least one llm entry");
        assertTrue(config.agents().isEmpty(), "quick-task.yaml: planner-only, no agents expected");
        assertTrue(config.plans().isEmpty(), "quick-task.yaml: planner-only, no plans expected");
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

        var config = loadConfig(resourceName);

        assertFalse(config.agents().isEmpty(), resourceName + ": expected at least one agent");

        var agent = config.agents().getFirst();

        assertNotNull(agent.name());
        assertNotNull(agent.role());
        assertNotNull(agent.externalId());
    }

    private static RuntimeConfig loadConfig(String resourceName) throws Exception {

        var path = Path.of(Objects.requireNonNull(
                ExampleYamlTest.class.getResource("/" + resourceName)).toURI());

        return RuntimeConfig.load(path);
    }
}
