/**
 * Domain: Customer Success
 * Tools: HubSpot, Gmail, Google Calendar, Slack (all via Composio)
 */
package ai.agentican.framework.examples.withtools;

import ai.agentican.framework.Agentican;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.nio.file.Path;
import java.util.Objects;

public class CustomerOnboarding {

    static String TASK_NAME = "Onboard Enterprise Fintech";
    static String PLAN_NAME = "Customer Onboarding";

    static void main() throws Exception {

        try (var agentican = Agentican.builder(config()).build()) {

            var onboarding = agentican.workflowTask(TASK_NAME)
                    .plan(PLAN_NAME)
                    .input(Customer.class)
                    .output(RiskAssessment.class)
                    .build();

            var risk = onboarding.runAsync(customer()).join();

            print(risk);
        }
    }

    static Path config() throws Exception {

        return Path.of(Objects.requireNonNull(CustomerOnboarding.class.getResource("/customer-onboarding.yaml")).toURI());
    }

    static Customer customer() {

        return new Customer("CUST-2026-0417", "Acme Fintech", "jane.chen@acmefintech.com");
    }

    static void print(RiskAssessment risk) {

        System.out.println("Risk score: " + risk.score() + " (" + risk.recommendation() + ")");
        System.out.println(risk.rationale());
    }

    record Customer(String customerId, String company, String contactEmail) {}

    record RiskAssessment(

            @JsonPropertyDescription("Composite risk score 1-100 (lower is better) — weighted from compliance, credit and business-fit per the risk framework")
            int score,

            @JsonPropertyDescription("One of: approve, review, escalate")
            String recommendation,

            @JsonPropertyDescription("One-paragraph plain-language summary a non-specialist can understand")
            String rationale) {
    }
}
