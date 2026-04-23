/**
 * Domain: Operations / SRE
 * Tools: None
 */
package ai.agentican.framework.examples.notools.workflows;

import ai.agentican.framework.Agentican;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public class Postmortem {

    static String TASK_NAME = "Draft Payments Postmortem";
    static String PLAN_NAME = "Incident Postmortem";

    static void main() throws Exception {

        try (var agentican = Agentican.builder(config()).build()) {

            var postmortem = agentican.workflowTask(TASK_NAME)
                    .plan(PLAN_NAME)
                    .input(Incident.class)
                    .output(PostmortemDoc.class)
                    .build();

            var doc = postmortem.runAsync(incident()).join();

            print(doc);
        }
    }

    static Path config() throws Exception {

        return Path.of(Objects.requireNonNull(Postmortem.class.getResource("/postmortem.yaml")).toURI());
    }

    static Incident incident() {

        return new Incident("""
                2026-04-12 09:14 UTC: error rate on payment-service spikes to 18%. Oncall
                paged. Initial hypothesis: upstream DB. DB metrics clean. 09:32: second
                engineer notices recent deploy at 09:10 enabled a new feature flag for
                retry logic. 09:41: flag rolled back. Errors subside by 09:47. Post-deploy
                smoke tests did not cover the retry path. Flag was enabled for ~31 minutes,
                estimated $4K in failed charges. No data loss — all retried successfully
                after rollback.
                """);
    }

    static void print(PostmortemDoc doc) {

        System.out.println("SUMMARY: " + doc.summary());
        System.out.println("\nTimeline:");
        doc.timeline().forEach(e -> System.out.println("  " + e.offset() + " [" + e.kind() + "] " + e.description()));
        System.out.println("\nContributing factors:");
        doc.contributingFactors().forEach(f -> System.out.println("  • " + f));
        System.out.println("\nAction items:");
        doc.actionItems().forEach(a -> System.out.println("  [" + a.owner() + "/" + a.type() + "] " + a.description()));
    }

    record Incident(String incident) {}

    record TimelineEntry(

            @JsonPropertyDescription("Timestamp or relative offset like '09:14 UTC' or '+0m'")
            String offset,

            @JsonPropertyDescription("One of: detection, diagnostic, mitigation")
            String kind,

            @JsonPropertyDescription("Factual description of the event — no interpretation")
            String description) {
    }

    record ActionItem(

            @JsonPropertyDescription("Owning team or role (e.g. SRE, platform-team, payments-team)")
            String owner,

            @JsonPropertyDescription("One of: preventive, detective, corrective")
            String type,

            @JsonPropertyDescription("Concrete action with a measurable success criterion")
            String description) {
    }

    record PostmortemDoc(

            @JsonPropertyDescription("Plain-language executive summary, 2-4 sentences, readable by a non-engineer")
            String summary,

            @JsonPropertyDescription("Verified timeline in chronological order")
            List<TimelineEntry> timeline,

            @JsonPropertyDescription("Contributing factors — technical, process and organizational — not just the triggering event")
            List<String> contributingFactors,

            @JsonPropertyDescription("Concrete action items with owner, type and success criteria")
            List<ActionItem> actionItems) {
    }
}
