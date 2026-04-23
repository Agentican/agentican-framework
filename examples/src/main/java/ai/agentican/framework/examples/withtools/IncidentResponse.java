/**
 * Domain: Operations / SRE
 * Tools: Slack, Linear, GitHub (all via Composio)
 */
package ai.agentican.framework.examples.withtools;

import ai.agentican.framework.Agentican;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public class IncidentResponse {

    static String TASK_NAME = "Respond to Payments Alert";
    static String PLAN_NAME = "Incident Response";

    static void main() throws Exception {

        try (var agentican = Agentican.builder(config()).build()) {

            var incident = agentican.workflowTask(TASK_NAME)
                    .plan(PLAN_NAME)
                    .input(Alert.class)
                    .output(TriageReport.class)
                    .build();

            var report = incident.runAsync(alert()).join();

            print(report);
        }
    }

    static Path config() throws Exception {

        return Path.of(Objects.requireNonNull(IncidentResponse.class.getResource("/incident-response.yaml")).toURI());
    }

    static Alert alert() {

        return new Alert("Error rate 15% on payment-service, p99 > 5s", "payment-service");
    }

    static void print(TriageReport report) {

        System.out.println("[" + report.severity() + "] affected: " + report.affectedServices());
        System.out.println("Blast radius: " + report.blastRadius());
        System.out.println("Likely causes: " + report.likelyContributingFactors());
    }

    record Alert(String alert, String service) {}

    record TriageReport(

            @JsonPropertyDescription("One of: P1, P2, P3, P4 per the incident runbook's severity definitions")
            String severity,

            @JsonPropertyDescription("Service names directly impacted by the alert")
            List<String> affectedServices,

            @JsonPropertyDescription("Estimated scope of impact — e.g. '>5% of users', 'single tenant', 'regional'")
            String blastRadius,

            @JsonPropertyDescription("Probable root causes based on deployment history and diagnostic signals")
            List<String> likelyContributingFactors) {
    }
}
