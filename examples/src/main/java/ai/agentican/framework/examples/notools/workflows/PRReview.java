/**
 * Domain: Engineering
 * Tools: None
 */
package ai.agentican.framework.examples.notools.workflows;

import ai.agentican.framework.Agentican;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public class PRReview {

    static String TASK_NAME = "Review Idempotency PR";
    static String PLAN_NAME = "PR Review";

    static void main() throws Exception {

        try (var agentican = Agentican.builder(config()).build()) {

            var review = agentican.workflowTask(TASK_NAME)
                    .plan(PLAN_NAME)
                    .input(PullRequest.class)
                    .output(ReviewSummary.class)
                    .build();

            var summary = review.runAsync(pr()).join();

            print(summary);
        }
    }

    static Path config() throws Exception {

        return Path.of(Objects.requireNonNull(PRReview.class.getResource("/pr-review.yaml")).toURI());
    }

    static PullRequest pr() {

        return new PullRequest("""
                Title: Add idempotency keys to the payment-service POST /charges endpoint

                Description: Clients occasionally retry charges after transient timeouts,
                causing duplicate charges. This PR adds an optional Idempotency-Key header,
                stored in Redis with a 24h TTL. Duplicate keys within the window return the
                original response.

                Files changed: 6 (controller, repo, redis client, tests x3)
                Diff summary: +340 / -28, no schema changes, new Redis connection pool,
                tests cover happy path and concurrent retry race.
                """);
    }

    static void print(ReviewSummary summary) {

        System.out.println("Recommendation: " + summary.recommendation());
        summary.findings().forEach(f -> System.out.println("  [" + f.severity() + "] " + f.title() + " — " + f.note()));
    }

    record PullRequest(String pr) {}

    record Finding(

            @JsonPropertyDescription("One of: blocking, significant, minor per the review-checklist skill")
            String severity,

            @JsonPropertyDescription("Short finding title, actionable (\"Missing rollback path for Redis outage\")")
            String title,

            @JsonPropertyDescription("One-to-two-sentence explanation or suggested change")
            String note) {
    }

    record ReviewSummary(

            @JsonPropertyDescription("Deduplicated findings ordered by severity")
            List<Finding> findings,

            @JsonPropertyDescription("One of: approve, approve-with-nits, request-changes, block")
            String recommendation) {
    }
}
