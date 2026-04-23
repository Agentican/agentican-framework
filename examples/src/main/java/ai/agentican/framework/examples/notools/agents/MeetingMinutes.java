/**
 * Domain: Productivity
 * Tools: None
 */
package ai.agentican.framework.examples.notools.agents;

import ai.agentican.framework.Agentican;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public class MeetingMinutes {

    static String TASK_NAME = "Structure Rollout Notes";
    static String AGENT_NAME = "Chief of Staff";
    static String SKILL_NAME = "Notes structure";
    static String INSTRUCTIONS = """
                            Clean these raw notes from "{{param.meeting_title}}" into structured output.
                            Separate decisions from action items from open questions. Preserve the
                            actual wording of decisions. Flag action items with no named owner.

                            Raw notes:
                            {{param.raw_text}}
                            """;

    static void main() throws Exception {

        try (var agentican = Agentican.builder(config()).build()) {

            var cleaner = agentican.agentTask(TASK_NAME)
                    .agent(AGENT_NAME)
                    .input(RawNotes.class)
                    .output(CleanedNotes.class)
                    .skills(SKILL_NAME)
                    .instructions(INSTRUCTIONS)
                    .build();

            var cleaned = cleaner.runAsync(notes()).join();

            print(cleaned);
        }
    }

    static Path config() throws Exception {

        return Path.of(Objects.requireNonNull(MeetingMinutes.class.getResource("/meeting-minutes.yaml")).toURI());
    }

    static RawNotes notes() {

        return new RawNotes("Agentican rollout planning", """
                Discussed Q2 rollout. Jane said we need better audit trails before turning
                on auto-approve for payments. Agreed to pilot with two customers (Acme and
                Zenith). Still unclear whether we need OTel or custom instrumentation —
                punted to next week. Someone needs to write the pilot agreement by EOW.
                Bob will draft the customer comms by Thursday. Everyone agreed the board
                deck is the priority this sprint.
                """);
    }

    static void print(CleanedNotes c) {

        System.out.println("SUMMARY: " + c.summary());
        System.out.println("\nDecisions:");     c.decisions().forEach(d -> System.out.println("  ✓ " + d));
        System.out.println("\nAction items:");
        c.actionItems().forEach(a -> System.out.println("  [" + (a.owner() == null ? "UNASSIGNED" : a.owner()) + "] " + a.description() + (a.due() == null ? "" : " (due " + a.due() + ")")));
        System.out.println("\nOpen questions:"); c.openQuestions().forEach(q -> System.out.println("  ? " + q));
    }

    record RawNotes(String meetingTitle, String rawText) {}

    record ActionItem(

            @JsonPropertyDescription("Concrete action to take — preserves the actual commitment language")
            String description,

            @JsonPropertyDescription("Named owner, or null if the notes didn't attribute one")
            String owner,

            @JsonPropertyDescription("Due date/timing if stated in the notes, otherwise null")
            String due) {
    }

    record CleanedNotes(

            @JsonPropertyDescription("One-sentence summary of what the meeting accomplished")
            String summary,

            @JsonPropertyDescription("Decisions the group made — preserve actual wording, don't smooth into corporate-speak")
            List<String> decisions,

            @JsonPropertyDescription("Action items with owner and due when attributed, null otherwise")
            List<ActionItem> actionItems,

            @JsonPropertyDescription("Unresolved topics or questions left for follow-up")
            List<String> openQuestions) {
    }
}
