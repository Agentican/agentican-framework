/**
 * Domain: Community / OSS
 * Tools: None
 * HITL: Question on the intermediate `categorize` step — the agent asks for
 *       the current quarter's recognition-tier criteria before categorizing,
 *       since criteria change quarter-to-quarter.
 */
package ai.agentican.framework.examples.notools.workflows.hitl;

import ai.agentican.framework.Agentican;
import ai.agentican.framework.examples.notools.agents.hitl.CliHitlNotifier;
import ai.agentican.framework.hitl.HitlManager;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public class ContributorRecognition {

    static String TASK_NAME = "Recognize Q2 OSS Contributors";
    static String PLAN_NAME = "Contributor Recognition";

    static void main() throws Exception {

        try (var agentican = Agentican.builder(config())
                .hitlManager(new HitlManager(new CliHitlNotifier()))
                .build()) {

            var recognition = agentican.workflowTask(TASK_NAME)
                    .plan(PLAN_NAME)
                    .input(ActivityPeriod.class)
                    .output(RecognitionMessages.class)
                    .build();

            var messages = recognition.runAsync(period()).join();

            print(messages);
        }
    }

    static Path config() throws Exception {

        return Path.of(Objects.requireNonNull(ContributorRecognition.class.getResource("/contributor-recognition.yaml")).toURI());
    }

    static ActivityPeriod period() {

        return new ActivityPeriod(
                "driftwave/core",
                "Q2 2026",
                """
                        @priya-m: merged #412 (streaming backpressure on slow
                        consumers), #437 (retry jitter configurable), #451
                        (grpc health endpoint). Reviewed 14 PRs. Triaged 9
                        issues.
                        @lukas-b: merged #398 (doc restructure on the getting-
                        started guide), #420 (CLI error messages rewrite).
                        Started and drove the Discussion #89 on schema-registry
                        API shape to a decision.
                        @anna-r: merged #441 (bug fix — connection leak on
                        cancel). Opened 6 well-reproduced bug reports. Reviewed
                        3 PRs.
                        @devj-ops: first-time contributor, merged #456 (typo
                        in README).
                        @micah-k: no merges this quarter but sustained on
                        Discord support — answered 22 help requests with
                        detailed code examples.
                        """);
    }

    static void print(RecognitionMessages m) {

        System.out.println("Summary: " + m.summary() + "\n");
        System.out.println("Messages:");
        m.messages().forEach(msg -> System.out.println("  • " + msg));
    }

    record ActivityPeriod(

            @JsonPropertyDescription("Repository the contributions are from")
            String repoName,

            @JsonPropertyDescription("Period label (e.g. Q2 2026)")
            String periodLabel,

            @JsonPropertyDescription("Raw activity log — merged PRs, reviews, issues, discussions, community support")
            String activity) {
    }

    record RecognitionMessages(

            @JsonPropertyDescription("One-sentence summary of the recognition cohort")
            String summary,

            @JsonPropertyDescription("Per-contributor message; each names at least one specific contribution")
            List<String> messages) {
    }
}
