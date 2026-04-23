/**
 * Domain: Operations / SRE
 * Tools: None
 */
package ai.agentican.framework.examples.notools.agents;

import ai.agentican.framework.Agentican;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public class AlertReview {

    static String TASK_NAME = "Audit Payments Alert";
    static String AGENT_NAME = "Site Reliability Engineer";
    static String SKILL_NAME = "Alert quality checklist";
    static String INSTRUCTIONS = """
                            Review this alert for signal quality, threshold defensibility and
                            runbook usefulness. Recommend specific fixes.

                            Alert name: {{param.name}}
                            Query: {{param.query}}
                            Threshold: {{param.threshold}}
                            Runbook: {{param.runbook_link}}
                            """;

    static void main() throws Exception {

        try (var agentican = Agentican.builder(config()).build()) {

            var reviewer = agentican.agentTask(TASK_NAME)
                    .agent(AGENT_NAME)
                    .input(AlertDefinition.class)
                    .output(AlertCritique.class)
                    .skills(SKILL_NAME)
                    .instructions(INSTRUCTIONS)
                    .build();

            var critique = reviewer.runAsync(alert()).join();

            print(critique);
        }
    }

    static Path config() throws Exception {

        return Path.of(Objects.requireNonNull(AlertReview.class.getResource("/alert-review.yaml")).toURI());
    }

    static AlertDefinition alert() {

        return new AlertDefinition(
                "payment-service-errors",
                "sum(rate(http_requests_total{service=\"payment-service\",code=~\"5..\"}[1m]))",
                "> 0 for 1m",
                "https://wiki.internal/runbooks/payment-errors");
    }

    static void print(AlertCritique c) {

        System.out.println("Severity: " + c.severity());
        System.out.println("\nIssues:");    c.issues().forEach(i -> System.out.println("  ⚠ " + i));
        System.out.println("\nRecommendation: " + c.recommendation());
    }

    record AlertDefinition(String name, String query, String threshold, String runbookLink) {}

    record AlertCritique(

            @JsonPropertyDescription("Severity of the alert-quality problems found — clean, minor, significant, broken")
            String severity,

            @JsonPropertyDescription("Specific issues — e.g. 'fires on transients (1m window too short)', 'no runbook content at URL'")
            List<String> issues,

            @JsonPropertyDescription("Concrete fix recommendation with specific threshold/query changes, not 'tune this'")
            String recommendation) {
    }
}
