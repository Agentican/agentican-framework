/**
 * Domain: Customer Success
 * Tools: None
 */
package ai.agentican.framework.examples.notools.workflows;

import ai.agentican.framework.Agentican;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public class ChurnRisk {

    static String TASK_NAME = "Check Fintech Account Health";
    static String PLAN_NAME = "Churn Risk Assessment";

    static void main() throws Exception {

        try (var agentican = Agentican.builder(config()).build()) {

            var assessment = agentican.workflowTask(TASK_NAME)
                    .plan(PLAN_NAME)
                    .input(Account.class)
                    .output(ChurnAssessment.class)
                    .build();

            var result = assessment.runAsync(account()).join();

            print(result);
        }
    }

    static Path config() throws Exception {

        return Path.of(Objects.requireNonNull(ChurnRisk.class.getResource("/churn-risk.yaml")).toURI());
    }

    static Account account() {

        return new Account("""
                Acme Fintech (ARR $180K). Usage dropped 32% over the last 60 days.
                Five open support tickets, two escalated. Executive sponsor left
                the company in March. Renewal in 75 days. No engagement with the
                last two product releases.
                """);
    }

    static void print(ChurnAssessment result) {

        System.out.println("Score: " + result.score() + " [" + result.tier() + "]");
        result.drivingSignals().forEach(s -> System.out.println("  • " + s));
    }

    record Account(String account) {}

    record ChurnAssessment(

            @JsonPropertyDescription("Composite risk score 1-100 (higher is more at-risk), weighted per the churn-signals skill")
            int score,

            @JsonPropertyDescription("One of: low (0-30), medium (31-60), high (61-100)")
            String tier,

            @JsonPropertyDescription("Top 2-3 concrete signals from the account summary that drove this assessment")
            List<String> drivingSignals) {
    }
}
