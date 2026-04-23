/**
 * Domain: Sales / Deal Desk
 * Tools: None
 * HITL: Step approval via CLI — discount decisions bind the company to a
 *       margin impact, so a human signs off before the rep takes it back to
 *       the customer.
 */
package ai.agentican.framework.examples.notools.agents.hitl;

import ai.agentican.framework.Agentican;
import ai.agentican.framework.hitl.HitlManager;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.nio.file.Path;
import java.util.Objects;

public class DealDiscount {

    static String TASK_NAME = "Rule On Fintech Discount Ask";
    static String AGENT_NAME = "Sales Director";
    static String SKILL_NAME = "Discount approval matrix";
    static String INSTRUCTIONS = """
                            An AE is asking for a discount approval. Apply the
                            discount-approval-matrix skill and return the decision plus
                            the exact talk track for the rep.

                            Deal:
                            {{input}}
                            """;

    static void main() throws Exception {

        try (var agentican = Agentican.builder(config())
                .hitlManager(new HitlManager(new CliHitlNotifier()))
                .build()) {

            var desk = agentican.agentTask(TASK_NAME)
                    .agent(AGENT_NAME)
                    .input(DealContext.class)
                    .output(DiscountDecision.class)
                    .skills(SKILL_NAME)
                    .instructions(INSTRUCTIONS)
                    .hitl(true)
                    .build();

            var decision = desk.runAsync(deal()).join();

            print(decision);
        }
    }

    static Path config() throws Exception {

        return Path.of(Objects.requireNonNull(DealDiscount.class.getResource("/deal-discount.yaml")).toURI());
    }

    static DealContext deal() {

        return new DealContext(
                "Coastline Fintech",
                180000.00,
                28,
                "Losing RFP language cites a named competitor at 22% off list for a "
                        + "12-month term. Customer has offered a 3-year term if we meet 28%.",
                15000.00);
    }

    static void print(DiscountDecision d) {

        System.out.println("Approved discount: " + d.approvedDiscountPct() + "%");
        System.out.println("\nRationale:\n" + d.rationale());
        System.out.println("\nTalk track for the rep:\n" + d.talkTrack());
    }

    record DealContext(

            @JsonPropertyDescription("Customer account name")
            String customer,

            @JsonPropertyDescription("Total contract value at list price, in USD")
            double dealSizeUsd,

            @JsonPropertyDescription("Discount percentage the AE is requesting")
            int discountRequestedPct,

            @JsonPropertyDescription("Competitive context the AE has provided — quotes, RFP lines, named competitors")
            String competitivePressure,

            @JsonPropertyDescription("Customer's current monthly recurring revenue with us, in USD")
            double mrrUsd) {
    }

    record DiscountDecision(

            @JsonPropertyDescription("Approved discount percentage — may be less than requested, paired with a concession")
            int approvedDiscountPct,

            @JsonPropertyDescription("Rationale tied to the approval matrix and the deal's competitive context")
            String rationale,

            @JsonPropertyDescription("Verbatim talk track the rep takes back to the customer — not bullet points")
            String talkTrack) {
    }
}
