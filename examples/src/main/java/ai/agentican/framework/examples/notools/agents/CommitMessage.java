/**
 * Domain: Engineering
 * Tools: None
 */
package ai.agentican.framework.examples.notools.agents;

import ai.agentican.framework.Agentican;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public class CommitMessage {

    static String TASK_NAME = "Draft Payments Commit Message";
    static String AGENT_NAME = "Software Engineer";
    static String SKILL_NAME = "Commit style";
    static String INSTRUCTIONS = """
                            Write a Conventional Commits message for this change.

                            Description: {{param.description}}
                            Files changed: {{param.files_changed}}
                            Why this change: {{param.rationale}}
                            """;

    static void main() throws Exception {

        try (var agentican = Agentican.builder(config()).build()) {

            var scribe = agentican.agentTask(TASK_NAME)
                    .agent(AGENT_NAME)
                    .input(ChangeDescription.class)
                    .output(ConventionalCommit.class)
                    .skills(SKILL_NAME)
                    .instructions(INSTRUCTIONS)
                    .build();

            var msg = scribe.runAsync(diff()).join();

            print(msg);
        }
    }

    static Path config() throws Exception {

        return Path.of(Objects.requireNonNull(CommitMessage.class.getResource("/commit-message.yaml")).toURI());
    }

    static ChangeDescription diff() {

        return new ChangeDescription(
                "Add idempotency keys to POST /charges endpoint backed by Redis",
                List.of("payments/Controller.java", "payments/IdempotencyStore.java",
                        "payments/RedisConfig.java", "payments/ControllerTest.java"),
                "Clients occasionally retry charges after transient network timeouts, causing "
                        + "duplicate charges. Needed before the holiday traffic surge.");
    }

    static void print(ConventionalCommit m) {

        System.out.println(m.subject());
        if (m.body() != null && !m.body().isBlank()) System.out.println("\n" + m.body());
    }

    record ChangeDescription(String description, List<String> filesChanged, String rationale) {}

    record ConventionalCommit(

            @JsonPropertyDescription("Conventional Commits subject line — imperative mood, under 72 chars, no trailing period, type prefix (feat:, fix:, refactor:, etc.)")
            String subject,

            @JsonPropertyDescription("Optional body explaining the why — empty string if there's nothing to add beyond the subject")
            String body) {
    }
}
