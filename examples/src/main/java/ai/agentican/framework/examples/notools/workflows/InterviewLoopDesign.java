/**
 * Domain: HR / Recruiting
 * Tools: None
 */
package ai.agentican.framework.examples.notools.workflows;

import ai.agentican.framework.Agentican;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public class InterviewLoopDesign {

    static String TASK_NAME = "Design Senior Backend Loop";
    static String PLAN_NAME = "Interview Loop Design";

    static void main() throws Exception {

        try (var agentican = Agentican.builder(config()).build()) {

            var generator = agentican.workflowTask(TASK_NAME)
                    .plan(PLAN_NAME)
                    .input(Role.class)
                    .output(InterviewLoop.class)
                    .build();

            var loop = generator.runAsync(role()).join();

            print(loop);
        }
    }

    static Path config() throws Exception {

        return Path.of(Objects.requireNonNull(InterviewLoopDesign.class.getResource("/interview-loop-design.yaml")).toURI());
    }

    static Role role() {

        return new Role("Senior Backend Engineer", "L5",
                List.of("distributed systems", "code quality", "collaboration"));
    }

    static void print(InterviewLoop loop) {

        loop.sets().forEach(s -> {
            System.out.println("== " + s.area() + " ==");
            s.questions().forEach(q -> System.out.println("  " + q.tier() + ": " + q.text() + " [" + q.rubric() + "]"));
        });
    }

    record Role(String role, String seniority, List<String> areas) {}

    record Question(

            @JsonPropertyDescription("One of: foundational, core, stretch")
            String tier,

            @JsonPropertyDescription("The interview question text")
            String text,

            @JsonPropertyDescription("One-line scoring guidance so interviewers grade consistently")
            String rubric) {
    }

    record QuestionSet(

            @JsonPropertyDescription("Assessment area from the input — matches one of the requested areas")
            String area,

            @JsonPropertyDescription("Questions for this area spanning foundational, core and stretch tiers")
            List<Question> questions) {
    }

    record InterviewLoop(List<QuestionSet> sets) {}
}
