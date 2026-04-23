/**
 * Domain: Productivity
 * Tools: None
 */
package ai.agentican.framework.examples.notools.workflows;

import ai.agentican.framework.Agentican;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public class EmailTriage {

    static String TASK_NAME = "Triage Morning Inbox";
    static String PLAN_NAME = "Email Triage";

    static void main() throws Exception {

        try (var agentican = Agentican.builder(config()).build()) {

            var triage = agentican.workflowTask(TASK_NAME)
                    .plan(PLAN_NAME)
                    .input(Inbox.class)
                    .output(TriageSummary.class)
                    .build();

            var summary = triage.runAsync(inbox()).join();

            print(summary);
        }
    }

    static Path config() throws Exception {

        return Path.of(Objects.requireNonNull(EmailTriage.class.getResource("/email-triage.yaml")).toURI());
    }

    static Inbox inbox() {

        return new Inbox(List.of(
                new EmailSummary("ceo@example.com", "Q2 board deck review", "Need your slides by Friday."),
                new EmailSummary("alice@example.com", "Re: retro notes", "Thanks! Will circulate tomorrow."),
                new EmailSummary("noreply@stripe.com", "Your April invoice", "Automated billing summary."),
                new EmailSummary("vendor-sales@acme.io", "Contract renewal Q&A", "Ping me when you have 20 minutes."),
                new EmailSummary("deals@example.com", "50% off productivity...", "Promotional offer.")));
    }

    static void print(TriageSummary summary) {

        System.out.println("Urgent:");
        summary.urgent().forEach(u -> System.out.println("  ⚡ " + u));
        System.out.println("\nBy category:");
        summary.decisions().forEach(d -> System.out.println("  [" + d.category() + "] " + d.subject() + " — " + d.rationale()));
        if (!summary.patterns().isEmpty()) {
            System.out.println("\nPatterns:");
            summary.patterns().forEach(p -> System.out.println("  • " + p));
        }
    }

    record EmailSummary(String sender, String subject, String preview) {}

    record Inbox(List<EmailSummary> emails) {}

    record TriageDecision(

            @JsonPropertyDescription("Subject line from the input email")
            String subject,

            @JsonPropertyDescription("One of: action-required, reply-needed, fyi-only, archive")
            String category,

            @JsonPropertyDescription("One-sentence reason for the chosen category")
            String rationale) {
    }

    record TriageSummary(

            @JsonPropertyDescription("Subject lines of items that need immediate attention, highest-priority first")
            List<String> urgent,

            @JsonPropertyDescription("All triage decisions in the same order as the input")
            List<TriageDecision> decisions,

            @JsonPropertyDescription("Observed patterns worth flagging — empty list if none")
            List<String> patterns) {
    }
}
