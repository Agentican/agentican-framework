/**
 * Domain: Legal / Compliance
 * Tools: None
 * HITL: Step approval on the intermediate `assess` step — legal reviews the
 *       compliance analyst's risk flags before redline positions are drafted.
 */
package ai.agentican.framework.examples.notools.workflows.hitl;

import ai.agentican.framework.Agentican;
import ai.agentican.framework.examples.notools.agents.hitl.CliHitlNotifier;
import ai.agentican.framework.hitl.HitlManager;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public class DpaReview {

    static String TASK_NAME = "Review Messaging Vendor DPA";
    static String PLAN_NAME = "DPA Review";

    static void main() throws Exception {

        try (var agentican = Agentican.builder(config())
                .hitlManager(new HitlManager(new CliHitlNotifier()))
                .build()) {

            var review = agentican.workflowTask(TASK_NAME)
                    .plan(PLAN_NAME)
                    .input(VendorDpa.class)
                    .output(RedlinePositions.class)
                    .build();

            var positions = review.runAsync(vendor()).join();

            print(positions);
        }
    }

    static Path config() throws Exception {

        return Path.of(Objects.requireNonNull(DpaReview.class.getResource("/dpa-review.yaml")).toURI());
    }

    static VendorDpa vendor() {

        return new VendorDpa(
                "Torchwire Messaging",
                "Customer email addresses, phone numbers, event metadata "
                        + "(message IDs, timestamps, delivery status).",
                """
                        4.2 Sub-processors. Vendor may engage sub-processors with 5
                        business days' notice to Customer via the Vendor portal.
                        4.5 Security Incidents. Vendor will notify Customer of any
                        Security Incident without undue delay, but no later than 96
                        hours after confirming the Incident.
                        6.1 Audit. Customer may request copies of Vendor's most
                        recent SOC 2 Type II report annually. Onsite audits are not
                        permitted.
                        7.3 International Transfers. Vendor relies on Standard
                        Contractual Clauses for all transfers outside the EEA.
                        9.1 Liability. Vendor's total liability under this DPA is
                        capped at fees paid in the six months preceding the claim.
                        """);
    }

    static void print(RedlinePositions p) {

        System.out.println("Summary: " + p.summary() + "\n");
        System.out.println("Redline positions:");
        p.positions().forEach(pos -> System.out.println("  • " + pos));
    }

    record VendorDpa(

            @JsonPropertyDescription("Vendor name")
            String vendorName,

            @JsonPropertyDescription("Data categories the vendor will process for us")
            String dataCategories,

            @JsonPropertyDescription("Verbatim DPA text under review")
            String dpaText) {
    }

    record RedlinePositions(

            @JsonPropertyDescription("Short summary of the negotiation posture")
            String summary,

            @JsonPropertyDescription("Each position names the flagged clause, proposed edit, rationale, and fallback")
            List<String> positions) {
    }
}
