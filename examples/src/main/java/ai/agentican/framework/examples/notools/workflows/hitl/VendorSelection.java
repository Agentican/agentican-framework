/**
 * Domain: Procurement
 * Tools: None
 * HITL: Step approval on the intermediate `rank` step — the procurement lead's
 *       scoring is signed off before the selection memo names a winner.
 */
package ai.agentican.framework.examples.notools.workflows.hitl;

import ai.agentican.framework.Agentican;
import ai.agentican.framework.examples.notools.agents.hitl.CliHitlNotifier;
import ai.agentican.framework.hitl.HitlManager;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.nio.file.Path;
import java.util.Objects;

public class VendorSelection {

    static String TASK_NAME = "Select Observability Vendor";
    static String PLAN_NAME = "Vendor Selection";

    static void main() throws Exception {

        try (var agentican = Agentican.builder(config())
                .hitlManager(new HitlManager(new CliHitlNotifier()))
                .build()) {

            var selection = agentican.workflowTask(TASK_NAME)
                    .plan(PLAN_NAME)
                    .input(VendorProposals.class)
                    .output(SelectionMemo.class)
                    .build();

            var memo = selection.runAsync(proposals()).join();

            print(memo);
        }
    }

    static Path config() throws Exception {

        return Path.of(Objects.requireNonNull(VendorSelection.class.getResource("/vendor-selection.yaml")).toURI());
    }

    static VendorProposals proposals() {

        return new VendorProposals(
                "Observability platform for 600-node fleet",
                """
                        Vendor A — Lumenscope
                          Annual list price: $420K. Volume tier: 20% off above 500 nodes.
                          SLA: 99.9% uptime, 10% credit for <99.5%.
                          Security: SOC 2 Type II, ISO 27001.
                          Integration lift: 4 weeks with a dedicated customer engineer.
                          Support: 24/7 via chat, enterprise account team.

                        Vendor B — Sentinel Metrics
                          Annual list price: $295K. Volume tier: flat pricing.
                          SLA: 99.95% uptime, 25% credit for <99.8%.
                          Security: SOC 2 Type II.
                          Integration lift: 8 weeks, shared CSM.
                          Support: business hours via email, community forum otherwise.

                        Vendor C — Northcap Telemetry
                          Annual list price: $510K. Volume tier: 30% off above 1000 nodes.
                          SLA: 99.9% uptime, credits not stated in proposal.
                          Security: SOC 2 Type II, ISO 27001, FedRAMP Moderate.
                          Integration lift: 3 weeks, dedicated white-glove team.
                          Support: 24/7 with named TAM.
                        """);
    }

    static void print(SelectionMemo m) {

        System.out.println("Winner: " + m.winner());
        System.out.println("Runner-up: " + m.runnerUp());
        System.out.println("Decisive criterion: " + m.decisiveCriterion());
        System.out.println("\nMemo:\n" + m.memo());
    }

    record VendorProposals(

            @JsonPropertyDescription("RFP title or scope")
            String rfpTitle,

            @JsonPropertyDescription("Vendor proposals as submitted")
            String proposals) {
    }

    record SelectionMemo(

            @JsonPropertyDescription("Selected vendor name")
            String winner,

            @JsonPropertyDescription("Runner-up vendor name")
            String runnerUp,

            @JsonPropertyDescription("The single dimension that decided winner over runner-up")
            String decisiveCriterion,

            @JsonPropertyDescription("Memo body — names winner, runner-up, decisive criterion, risks")
            String memo) {
    }
}
