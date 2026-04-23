/**
 * Domain: Risk / Fraud
 * Tools: None
 * HITL: Question on the intermediate `classify` step — the agent asks for
 *       this week's active risk thresholds before classifying scored
 *       transactions, since thresholds change weekly with loss appetite.
 */
package ai.agentican.framework.examples.notools.workflows.hitl;

import ai.agentican.framework.Agentican;
import ai.agentican.framework.examples.notools.agents.hitl.CliHitlNotifier;
import ai.agentican.framework.hitl.HitlManager;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public class FraudTriage {

    static String TASK_NAME = "Triage Weekend Fraud Batch";
    static String PLAN_NAME = "Fraud Triage";

    static void main() throws Exception {

        try (var agentican = Agentican.builder(config())
                .hitlManager(new HitlManager(new CliHitlNotifier()))
                .build()) {

            var triage = agentican.workflowTask(TASK_NAME)
                    .plan(PLAN_NAME)
                    .input(FlaggedBatch.class)
                    .output(ActionList.class)
                    .build();

            var actions = triage.runAsync(batch()).join();

            print(actions);
        }
    }

    static Path config() throws Exception {

        return Path.of(Objects.requireNonNull(FraudTriage.class.getResource("/fraud-triage.yaml")).toURI());
    }

    static FlaggedBatch batch() {

        return new FlaggedBatch(
                "BATCH-2026-04-21-0915",
                """
                        TXN-88421: $4,820 via card ending 7744. Five charges in
                        8 minutes to the same card. Card country US, IP country
                        RO. Device fingerprint clean. Merchant category:
                        electronics.
                        TXN-88432: $218 via card ending 2019. Card country US,
                        IP country US. 6x account 90-day average. Merchant
                        category: grocery. Device fingerprint clean.
                        TXN-88447: $9,500 via card ending 0012. Card country
                        DE, IP country DE. First-ever transaction on this
                        account. Merchant category: crypto OTC. Device
                        fingerprint flagged in two prior disputes this quarter.
                        TXN-88461: $75 via card ending 5521. Card country US,
                        IP country US. Normal velocity, normal amount, clean
                        device, merchant category: coffee.
                        """);
    }

    static void print(ActionList a) {

        System.out.println("Summary: " + a.summary() + "\n");
        System.out.println("Actions:");
        a.items().forEach(i -> System.out.println("  • " + i));
    }

    record FlaggedBatch(

            @JsonPropertyDescription("Batch identifier")
            String batchId,

            @JsonPropertyDescription("Flagged transactions with their signal context")
            String flaggedTransactions) {
    }

    record ActionList(

            @JsonPropertyDescription("One-sentence summary of the triage outcome")
            String summary,

            @JsonPropertyDescription("Action per transaction with a one-line customer-comms template")
            List<String> items) {
    }
}
