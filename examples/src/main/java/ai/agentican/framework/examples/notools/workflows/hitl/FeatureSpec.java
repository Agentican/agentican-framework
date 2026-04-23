/**
 * Domain: Product Management
 * Tools: None
 * HITL: Step approval on the intermediate `spec` step — the Product Manager
 *       drafts the feature spec and the human signs off before the Tech
 *       Lead breaks it into engineering tasks.
 */
package ai.agentican.framework.examples.notools.workflows.hitl;

import ai.agentican.framework.Agentican;
import ai.agentican.framework.examples.notools.agents.hitl.CliHitlNotifier;
import ai.agentican.framework.hitl.HitlManager;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public class FeatureSpec {

    static String TASK_NAME = "Spec Bulk Payout API Feature";
    static String PLAN_NAME = "Feature Spec Review";

    static void main() throws Exception {

        try (var agentican = Agentican.builder(config())
                .hitlManager(new HitlManager(new CliHitlNotifier()))
                .build()) {

            var review = agentican.workflowTask(TASK_NAME)
                    .plan(PLAN_NAME)
                    .input(FeatureTicket.class)
                    .output(EngineeringBreakdown.class)
                    .build();

            var breakdown = review.runAsync(ticket()).join();

            print(breakdown);
        }
    }

    static Path config() throws Exception {

        return Path.of(Objects.requireNonNull(FeatureSpec.class.getResource("/feature-spec.yaml")).toURI());
    }

    static FeatureTicket ticket() {

        return new FeatureTicket(
                "Bulk Payout API",
                "As a merchant with many vendors, I want to submit a batch "
                        + "payout file so I can pay all my vendors in one request "
                        + "instead of one API call per vendor.",
                "Three enterprise merchants have asked for this in the last "
                        + "quarter. The biggest one processes 40K payouts a day "
                        + "and is currently hitting our per-request rate limit.");
    }

    static void print(EngineeringBreakdown b) {

        System.out.println("Summary: " + b.summary() + "\n");
        System.out.println("Tasks:");
        b.tasks().forEach(t -> System.out.println("  • " + t));
    }

    record FeatureTicket(

            @JsonPropertyDescription("Feature title")
            String title,

            @JsonPropertyDescription("User story as written in the ticket")
            String userStory,

            @JsonPropertyDescription("Why this matters right now — drivers, deadlines, stakeholders")
            String businessContext) {
    }

    record EngineeringBreakdown(

            @JsonPropertyDescription("One-sentence summary of the breakdown")
            String summary,

            @JsonPropertyDescription("Flat task list — each task names days, owner, and dependencies")
            List<String> tasks) {
    }
}
