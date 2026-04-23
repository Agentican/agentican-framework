/**
 * Domain: Customer Success
 * Tools: None
 * HITL: Step approval via CLI — the refund decision is drafted by the agent
 *       and must be approved by a human before it is treated as final.
 */
package ai.agentican.framework.examples.notools.agents.hitl;

import ai.agentican.framework.Agentican;
import ai.agentican.framework.hitl.HitlManager;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.nio.file.Path;
import java.util.Objects;

public class RefundApproval {

    static String TASK_NAME = "Decide Enterprise Refund";
    static String AGENT_NAME = "Customer Success Manager";
    static String SKILL_NAME = "Refund policy";
    static String INSTRUCTIONS = """
                            A customer is requesting a refund. Decide the remedy using the
                            refund-policy skill, and draft the customer-facing reply.

                            Request:
                            {{input}}
                            """;

    static void main() throws Exception {

        try (var agentican = Agentican.builder(config())
                .hitlManager(new HitlManager(new CliHitlNotifier()))
                .build()) {

            var decide = agentican.agentTask(TASK_NAME)
                    .agent(AGENT_NAME)
                    .input(RefundRequest.class)
                    .output(RefundDecision.class)
                    .skills(SKILL_NAME)
                    .instructions(INSTRUCTIONS)
                    .hitl(true)
                    .build();

            var decision = decide.runAsync(request()).join();

            print(decision);
        }
    }

    static Path config() throws Exception {

        return Path.of(Objects.requireNonNull(RefundApproval.class.getResource("/refund-approval.yaml")).toURI());
    }

    static RefundRequest request() {

        return new RefundRequest(
                "Meridian Logistics",
                12500.00,
                "Ingestion pipeline was down for 6 hours on 2026-03-14, overlapping their "
                        + "weekly freight-settlement run. They escalated in writing the day "
                        + "before the outage asking for reliability guarantees.",
                31);
    }

    static void print(RefundDecision d) {

        System.out.println("Approved refund: $" + d.approvedAmountUsd());
        System.out.println("\nRationale:\n" + d.rationale());
        System.out.println("\nCustomer reply:\n" + d.customerReply());
    }

    record RefundRequest(

            @JsonPropertyDescription("Customer account name")
            String customer,

            @JsonPropertyDescription("Amount the customer is requesting back, in USD")
            double amountUsd,

            @JsonPropertyDescription("Customer's narrative of what happened — verbatim when possible")
            String reason,

            @JsonPropertyDescription("How many months the account has been a paying customer")
            int tenureMonths) {
    }

    record RefundDecision(

            @JsonPropertyDescription("Approved refund amount in USD — may be less than requested")
            double approvedAmountUsd,

            @JsonPropertyDescription("Decision rationale grounded in the refund policy and account history")
            String rationale,

            @JsonPropertyDescription("Customer-facing reply — empathetic, specific, names the remedy")
            String customerReply) {
    }
}
