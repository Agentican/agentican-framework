/**
 * Domain: Productivity
 * Tools: Google Calendar (all via Composio)
 */
package ai.agentican.framework.examples.withtools;

import ai.agentican.framework.Agentican;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public class MeetingPrepBrief {

    static String TASK_NAME = "Prep Today's Briefs";
    static String PLAN_NAME = "Meeting Prep Brief";

    static void main() throws Exception {

        try (var agentican = Agentican.builder(config()).build()) {

            var prep = agentican.workflowTask(TASK_NAME)
                    .plan(PLAN_NAME)
                    .input(MeetingDay.class)
                    .output(DailyBriefs.class)
                    .build();

            var briefs = prep.runAsync(day()).join();

            print(briefs);
        }
    }

    static Path config() throws Exception {

        return Path.of(Objects.requireNonNull(MeetingPrepBrief.class.getResource("/meeting-prep-brief.yaml")).toURI());
    }

    static MeetingDay day() {

        return new MeetingDay(LocalDate.now().toString());
    }

    static void print(DailyBriefs briefs) {

        briefs.briefs().forEach(b -> {
            System.out.println(b.title() + " — " + b.attendees());
            System.out.println("  " + b.purpose());
            b.talkingPoints().forEach(p -> System.out.println("  • " + p));
        });
    }

    record MeetingDay(String date) {}

    record DailyBriefs(List<MeetingBrief> briefs) {}

    record MeetingBrief(

            @JsonPropertyDescription("Meeting title as it appears on the calendar")
            String title,

            @JsonPropertyDescription("Attendee names (fallback to email addresses when names aren't available)")
            List<String> attendees,

            @JsonPropertyDescription("One-sentence summary of the meeting's purpose")
            String purpose,

            @JsonPropertyDescription("Exactly three talking points, prioritized, each phrased so the user can act on it")
            List<String> talkingPoints) {
    }
}
